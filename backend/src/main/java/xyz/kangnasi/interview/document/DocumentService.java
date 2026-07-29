package xyz.kangnasi.interview.document;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import xyz.kangnasi.interview.auth.UserPrincipal;
import xyz.kangnasi.interview.common.AppException;
import xyz.kangnasi.interview.importjob.ImportJob;
import xyz.kangnasi.interview.importjob.ImportJobRepository;
import xyz.kangnasi.interview.importjob.ImportJobService;
import xyz.kangnasi.interview.user.AppUser;
import xyz.kangnasi.interview.user.UserRepository;

@Service
public class DocumentService {

    private static final TypeReference<List<SkippedFileResponse>> SKIPPED_FILE_LIST = new TypeReference<>() {
    };

    private final DocumentUploadRepository uploadRepository;
    private final KnowledgeDocumentRepository documentRepository;
    private final DocumentParseTaskRepository parseTaskRepository;
    private final DocumentParseTaskPublisher parseTaskPublisher;
    private final ImportJobRepository importJobRepository;
    private final ImportJobService importJobService;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final Path runtimeRoot;
    private final long maxUploadBytes;

    public DocumentService(
            DocumentUploadRepository uploadRepository,
            KnowledgeDocumentRepository documentRepository,
            DocumentParseTaskRepository parseTaskRepository,
            DocumentParseTaskPublisher parseTaskPublisher,
            ImportJobRepository importJobRepository,
            ImportJobService importJobService,
            UserRepository userRepository,
            ObjectMapper objectMapper,
            @Value("${app.upload.root:runtime/uploads}") String runtimeRoot,
            @Value("${app.upload.max-upload-bytes:52428800}") long maxUploadBytes
    ) {
        this.uploadRepository = uploadRepository;
        this.documentRepository = documentRepository;
        this.parseTaskRepository = parseTaskRepository;
        this.parseTaskPublisher = parseTaskPublisher;
        this.importJobRepository = importJobRepository;
        this.importJobService = importJobService;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
        this.runtimeRoot = Path.of(runtimeRoot);
        this.maxUploadBytes = maxUploadBytes;
    }

    @Transactional
    public DocumentUploadResponse upload(MultipartFile file, boolean autoStart, UserPrincipal principal) {
        if (principal == null) {
            throw AppException.unauthorized("未登录或登录已过期");
        }
        validateUploadFile(file);

        AppUser user = userRepository.findById(principal.id())
                .orElseThrow(() -> AppException.unauthorized("未登录或登录已过期"));

        String originalFilename = cleanDisplayFilename(file.getOriginalFilename());
        UploadType uploadType = detectUploadType(originalFilename);
        Path tempFile = saveTempUpload(file, originalFilename);
        String uploadSha256 = sha256(tempFile);

        DocumentUpload upload = uploadRepository.save(DocumentUpload.create(
                uploadType,
                originalFilename,
                file.getSize(),
                uploadSha256,
                tempFile.toString(),
                user,
                UploadParseStatus.QUEUED
        ));

        Path finalUploadPath = runtimeRoot
                .resolve("knowledge-uploads")
                .resolve(upload.getId().toString())
                .resolve(safeFilename(originalFilename));
        moveFile(tempFile, finalUploadPath);
        upload.changeStoredPath(finalUploadPath.toString());

        DocumentParseTask parseTask = parseTaskRepository.save(DocumentParseTask.create(upload.getId(), autoStart));
        parseTaskPublisher.publish(new DocumentParseMessage(parseTask.getId(), upload.getId()));

        return DocumentUploadResponse.queued(upload, parseTask);
    }

    @Transactional(readOnly = true)
    public UploadDetailResponse uploadDetail(Long uploadId) {
        DocumentUpload upload = uploadRepository.findById(uploadId)
                .orElseThrow(() -> AppException.notFound("上传批次不存在"));
        List<KnowledgeDocument> documents = documentRepository.findByUploadIdOrderByIdAsc(upload.getId());
        Map<Long, ImportJob> latestJobs = latestJobsByDocumentId(documents);
        DocumentParseTask parseTask = parseTaskRepository.findFirstByUploadIdOrderByCreatedAtDesc(upload.getId())
                .orElse(null);

        List<UploadResultDocumentResponse> documentResponses = documents.stream()
                .map(document -> UploadResultDocumentResponse.from(document, latestJobs.get(document.getId())))
                .toList();

        return UploadDetailResponse.from(
                upload,
                parseSkippedFiles(upload.getSkippedFilesJson()),
                parseTask,
                documentResponses
        );
    }

    @Transactional(readOnly = true)
    public Page<DocumentListItemResponse> listDocuments(int page, int pageSize) {
        PageRequest pageRequest = PageRequest.of(normalizePage(page), normalizePageSize(pageSize));
        Page<KnowledgeDocument> documentPage = documentRepository.findAll(pageRequest);
        Map<Long, ImportJob> latestJobs = latestJobsByDocumentId(documentPage.getContent());

        List<DocumentListItemResponse> items = documentPage.getContent().stream()
                .map(document -> DocumentListItemResponse.from(document, latestJobs.get(document.getId())))
                .toList();
        return new PageImpl<>(items, pageRequest, documentPage.getTotalElements());
    }

    @Transactional(readOnly = true)
    public DocumentDetailResponse documentDetail(Long documentId) {
        KnowledgeDocument document = documentRepository.findById(documentId)
                .orElseThrow(() -> AppException.notFound("知识文档不存在"));
        ImportJob latestJob = importJobRepository.findFirstByDocumentIdOrderByCreatedAtDesc(document.getId())
                .orElse(null);
        return DocumentDetailResponse.from(document, latestJob, readDocumentContent(document));
    }

    @Transactional
    public ImportJob createImportJob(Long documentId, boolean autoStart) {
        return importJobService.createForDocumentId(documentId, autoStart);
    }

    private void validateUploadFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw AppException.badRequest("文件内容不能为空");
        }
        if (file.getSize() > maxUploadBytes) {
            throw AppException.badRequest("文件超过大小限制");
        }
        detectUploadType(cleanDisplayFilename(file.getOriginalFilename()));
    }

    private UploadType detectUploadType(String filename) {
        String lowerFilename = filename.toLowerCase(Locale.ROOT);
        if (lowerFilename.endsWith(".md")) {
            return UploadType.MARKDOWN;
        }
        if (lowerFilename.endsWith(".zip")) {
            return UploadType.ZIP;
        }
        throw AppException.badRequest("仅支持 Markdown 文件或 ZIP 压缩包");
    }

    private Path saveTempUpload(MultipartFile file, String filename) {
        Path tempPath = runtimeRoot
                .resolve("tmp")
                .resolve(UUID.randomUUID().toString())
                .resolve(safeFilename(filename));
        try {
            Files.createDirectories(tempPath.getParent());
            file.transferTo(tempPath);
            return tempPath;
        } catch (IOException exception) {
            throw AppException.badRequest("文件保存失败，请重试");
        }
    }

    private Map<Long, ImportJob> latestJobsByDocumentId(List<KnowledgeDocument> documents) {
        Map<Long, ImportJob> latestJobs = new HashMap<>();
        for (KnowledgeDocument document : documents) {
            importJobRepository.findFirstByDocumentIdOrderByCreatedAtDesc(document.getId())
                    .ifPresent(job -> latestJobs.put(document.getId(), job));
        }
        return latestJobs;
    }

    private List<SkippedFileResponse> parseSkippedFiles(String skippedFilesJson) {
        if (skippedFilesJson == null || skippedFilesJson.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(skippedFilesJson, SKIPPED_FILE_LIST);
        } catch (JsonProcessingException exception) {
            return List.of();
        }
    }

    private String readDocumentContent(KnowledgeDocument document) {
        if (document.getStoredPath() == null || document.getStoredPath().isBlank()) {
            return "";
        }
        Path path = Path.of(document.getStoredPath());
        if (!Files.isRegularFile(path)) {
            return "";
        }
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            return "";
        }
    }

    private String cleanDisplayFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "upload";
        }
        String normalized = filename.replace('\\', '/');
        int index = normalized.lastIndexOf('/');
        String displayName = index >= 0 ? normalized.substring(index + 1) : normalized;
        return displayName.isBlank() ? "upload" : displayName;
    }

    private String safeFilename(String filename) {
        String normalized = Normalizer.normalize(filename, Normalizer.Form.NFKC)
                .replace('\\', '_')
                .replace('/', '_')
                .replaceAll("[^A-Za-z0-9._-]", "_");
        normalized = normalized.replaceAll("_+", "_");
        if (normalized.isBlank() || normalized.equals(".") || normalized.equals("..")) {
            return "document.md";
        }
        return normalized;
    }

    private void moveFile(Path source, Path target) {
        try {
            Files.createDirectories(target.getParent());
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
            deleteEmptyParents(source.getParent(), runtimeRoot.resolve("tmp"));
        } catch (IOException exception) {
            throw AppException.badRequest("文件保存失败，请重试");
        }
    }

    private void deleteEmptyParents(Path start, Path stop) {
        if (start == null || stop == null) {
            return;
        }
        Path normalizedStop = stop.toAbsolutePath().normalize();
        Path current = start.toAbsolutePath().normalize();
        while (current.startsWith(normalizedStop) && !current.equals(normalizedStop)) {
            try {
                Files.deleteIfExists(current);
            } catch (IOException exception) {
                return;
            }
            current = current.getParent();
        }
    }

    private String sha256(Path path) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream inputStream = Files.newInputStream(path);
                 DigestInputStream digestInputStream = new DigestInputStream(inputStream, digest)) {
                digestInputStream.transferTo(OutputStreamDiscarder.INSTANCE);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException | IOException exception) {
            throw AppException.badRequest("文件保存失败，请重试");
        }
    }

    private int normalizePage(int page) {
        return Math.max(page, 1) - 1;
    }

    private int normalizePageSize(int pageSize) {
        if (pageSize < 1) {
            return 20;
        }
        return Math.min(pageSize, 100);
    }

    private static final class OutputStreamDiscarder extends java.io.OutputStream {

        private static final OutputStreamDiscarder INSTANCE = new OutputStreamDiscarder();

        @Override
        public void write(int value) {
        }

        @Override
        public void write(byte[] bytes, int offset, int length) {
        }
    }
}
