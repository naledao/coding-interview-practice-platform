package xyz.kangnasi.interview.document;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import xyz.kangnasi.interview.common.AppException;
import xyz.kangnasi.interview.importjob.ImportJobService;

@Service
public class DocumentParseTaskService {

    private final DocumentParseTaskRepository parseTaskRepository;
    private final DocumentUploadRepository uploadRepository;
    private final KnowledgeDocumentRepository documentRepository;
    private final ImportJobService importJobService;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final Path runtimeRoot;
    private final long maxMarkdownBytes;
    private final long maxZipTotalBytes;
    private final int maxZipEntryCount;

    public DocumentParseTaskService(
            DocumentParseTaskRepository parseTaskRepository,
            DocumentUploadRepository uploadRepository,
            KnowledgeDocumentRepository documentRepository,
            ImportJobService importJobService,
            ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager,
            @Value("${app.upload.root:runtime/uploads}") String runtimeRoot,
            @Value("${app.upload.max-markdown-bytes:10485760}") long maxMarkdownBytes,
            @Value("${app.upload.max-zip-total-bytes:52428800}") long maxZipTotalBytes,
            @Value("${app.upload.max-zip-entry-count:200}") int maxZipEntryCount
    ) {
        this.parseTaskRepository = parseTaskRepository;
        this.uploadRepository = uploadRepository;
        this.documentRepository = documentRepository;
        this.importJobService = importJobService;
        this.objectMapper = objectMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.runtimeRoot = Path.of(runtimeRoot);
        this.maxMarkdownBytes = maxMarkdownBytes;
        this.maxZipTotalBytes = maxZipTotalBytes;
        this.maxZipEntryCount = maxZipEntryCount;
    }

    public void parse(Long parseTaskId) {
        try {
            if (!markRunning(parseTaskId)) {
                return;
            }

            transactionTemplate.executeWithoutResult(status -> parseContent(parseTaskId));
        } catch (DocumentParseFailure exception) {
            markFailed(parseTaskId, exception.failureMessage(), exception.ignoredFileCount(), exception.skippedFileCount(), exception.skippedFilesJson());
        } catch (AppException exception) {
            markFailed(parseTaskId, exception.getMessage(), 0, 0, "[]");
        } catch (RuntimeException exception) {
            markFailed(parseTaskId, "文档解析失败", 0, 0, "[]");
        }
    }

    private boolean markRunning(Long parseTaskId) {
        return Boolean.TRUE.equals(transactionTemplate.execute(status -> {
            DocumentParseTask parseTask = parseTaskRepository.findById(parseTaskId)
                    .orElseThrow(() -> AppException.notFound("解析任务不存在"));
            if (parseTask.getStatus() == DocumentParseTaskStatus.SUCCEEDED) {
                return false;
            }

            DocumentUpload upload = uploadRepository.findById(parseTask.getUploadId())
                    .orElseThrow(() -> AppException.notFound("上传批次不存在"));

            parseTask.markRunning();
            upload.markParsing();
            return true;
        }));
    }

    private void parseContent(Long parseTaskId) {
        DocumentParseTask parseTask = parseTaskRepository.findById(parseTaskId)
                .orElseThrow(() -> AppException.notFound("解析任务不存在"));
        if (parseTask.getStatus() == DocumentParseTaskStatus.SUCCEEDED) {
            return;
        }

        DocumentUpload upload = uploadRepository.findById(parseTask.getUploadId())
                .orElseThrow(() -> AppException.notFound("上传批次不存在"));

        if (upload.getUploadType() == UploadType.MARKDOWN) {
            parseMarkdownUpload(upload, parseTask.isAutoStartImport());
        } else {
            parseZipUpload(upload, parseTask.isAutoStartImport());
        }
        parseTask.markSucceeded();
    }

    private void markFailed(
            Long parseTaskId,
            String failedReason,
            int ignoredFileCount,
            int skippedFileCount,
            String skippedFilesJson
    ) {
        transactionTemplate.executeWithoutResult(status ->
                parseTaskRepository.findById(parseTaskId).ifPresent(parseTask -> {
                    parseTask.markFailed(failedReason);
                    uploadRepository.findById(parseTask.getUploadId()).ifPresent(upload ->
                            upload.markFailed(ignoredFileCount, skippedFileCount, skippedFilesJson));
                }));
    }

    private void parseMarkdownUpload(DocumentUpload upload, boolean autoStartImport) {
        Path uploadedFile = Path.of(upload.getStoredPath());
        long size = fileSize(uploadedFile);
        if (size == 0) {
            throw failUpload(upload, "文件内容不能为空");
        }
        if (size > maxMarkdownBytes) {
            throw failUpload(upload, "文件超过大小限制");
        }

        KnowledgeDocument document = documentRepository.save(KnowledgeDocument.create(
                upload,
                upload.getOriginalFilename(),
                UploadType.MARKDOWN,
                null,
                null,
                size,
                sha256(uploadedFile),
                uploadedFile.toString()
        ));

        Path documentPath = runtimeRoot
                .resolve("knowledge-documents")
                .resolve(document.getId().toString())
                .resolve(safeFilename(upload.getOriginalFilename()));
        copyFile(uploadedFile, documentPath);
        document.changeStoredPath(documentPath.toString());
        upload.markParsed(1, 0, 0, writeSkippedFiles(List.of()));
        importJobService.createForDocument(document, autoStartImport);
    }

    private void parseZipUpload(DocumentUpload upload, boolean autoStartImport) {
        Path zipFile = Path.of(upload.getStoredPath());
        List<SkippedFileResponse> skippedFiles = new ArrayList<>();
        List<PendingMarkdownDocument> pendingDocuments = new ArrayList<>();
        int ignoredFiles = 0;
        int entryCount = 0;
        long totalMarkdownBytes = 0;

        try (ZipInputStream zipInputStream = new ZipInputStream(Files.newInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                entryCount++;
                if (entryCount > maxZipEntryCount) {
                    throw failUpload(upload, "压缩包内文件数量超过限制", ignoredFiles, skippedFiles);
                }

                String entryPath = normalizeEntryPath(entry.getName());
                if (entry.isDirectory()) {
                    zipInputStream.closeEntry();
                    continue;
                }

                if (entryPath == null) {
                    skippedFiles.add(new SkippedFileResponse(entry.getName(), "UNSAFE_PATH"));
                    drainEntry(zipInputStream);
                    zipInputStream.closeEntry();
                    continue;
                }

                if (isHiddenSystemFile(entryPath) || !isMarkdownFilename(entryPath)) {
                    ignoredFiles++;
                    drainEntry(zipInputStream);
                    zipInputStream.closeEntry();
                    continue;
                }

                byte[] bytes = readZipEntryBytes(zipInputStream);
                zipInputStream.closeEntry();
                totalMarkdownBytes += bytes.length;
                if (totalMarkdownBytes > maxZipTotalBytes) {
                    throw failUpload(upload, "压缩包解压后内容超过大小限制", ignoredFiles, skippedFiles);
                }

                if (bytes.length == 0 || isBlankMarkdown(bytes)) {
                    skippedFiles.add(new SkippedFileResponse(entryPath, "EMPTY_MARKDOWN"));
                    continue;
                }

                String entryFilename = filenameFromPath(entryPath);
                pendingDocuments.add(new PendingMarkdownDocument(
                        entryFilename,
                        entryPath,
                        bytes.length,
                        sha256(bytes),
                        bytes
                ));
            }
        } catch (ZipException exception) {
            throw failUpload(upload, "压缩包解析失败，请检查文件格式", ignoredFiles, skippedFiles);
        } catch (IOException exception) {
            throw failUpload(upload, "压缩包解析失败，请检查文件格式", ignoredFiles, skippedFiles);
        }

        if (entryCount == 0) {
            throw failUpload(upload, "压缩包内容不能为空", ignoredFiles, skippedFiles);
        }
        if (pendingDocuments.isEmpty()) {
            throw failUpload(upload, "压缩包内未找到 Markdown 文件", ignoredFiles, skippedFiles);
        }

        List<KnowledgeDocument> documents = new ArrayList<>();
        for (PendingMarkdownDocument pendingDocument : pendingDocuments) {
            KnowledgeDocument document = documentRepository.save(KnowledgeDocument.create(
                    upload,
                    pendingDocument.originalFilename(),
                    UploadType.ZIP,
                    upload.getOriginalFilename(),
                    pendingDocument.archiveEntryPath(),
                    pendingDocument.fileSize(),
                    pendingDocument.contentSha256(),
                    ""
            ));

            Path documentPath = runtimeRoot
                    .resolve("knowledge-documents")
                    .resolve(document.getId().toString())
                    .resolve(safeFilename(pendingDocument.originalFilename()));
            writeBytes(documentPath, pendingDocument.content());
            document.changeStoredPath(documentPath.toString());
            documents.add(document);
        }

        for (KnowledgeDocument document : documents) {
            importJobService.createForDocument(document, autoStartImport);
        }

        upload.markParsed(documents.size(), ignoredFiles, skippedFiles.size(), writeSkippedFiles(skippedFiles));
    }

    private DocumentParseFailure failUpload(DocumentUpload upload, String message) {
        return new DocumentParseFailure(
                message,
                upload.getIgnoredFileCount(),
                upload.getSkippedFileCount(),
                upload.getSkippedFilesJson() == null ? "[]" : upload.getSkippedFilesJson()
        );
    }

    private DocumentParseFailure failUpload(
            DocumentUpload upload,
            String message,
            int ignoredFiles,
            List<SkippedFileResponse> skippedFiles
    ) {
        return new DocumentParseFailure(message, ignoredFiles, skippedFiles.size(), writeSkippedFiles(skippedFiles));
    }

    private String writeSkippedFiles(List<SkippedFileResponse> skippedFiles) {
        try {
            return objectMapper.writeValueAsString(skippedFiles);
        } catch (JsonProcessingException exception) {
            return "[]";
        }
    }

    private byte[] readZipEntryBytes(ZipInputStream inputStream) throws IOException {
        byte[] buffer = new byte[8192];
        int read;
        long total = 0;
        try (LimitedByteArrayOutputStream outputStream = new LimitedByteArrayOutputStream(maxMarkdownBytes)) {
            while ((read = inputStream.read(buffer)) != -1) {
                total += read;
                if (total > maxMarkdownBytes) {
                    throw AppException.badRequest("文件超过大小限制");
                }
                outputStream.write(buffer, 0, read);
            }
            return outputStream.toByteArray();
        }
    }

    private void drainEntry(InputStream inputStream) throws IOException {
        byte[] buffer = new byte[8192];
        while (inputStream.read(buffer) != -1) {
            // Read current ZIP entry fully before moving to the next entry.
        }
    }

    private boolean isBlankMarkdown(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8).isBlank();
    }

    private boolean isMarkdownFilename(String filename) {
        return filename.toLowerCase(java.util.Locale.ROOT).endsWith(".md");
    }

    private boolean isHiddenSystemFile(String path) {
        String normalized = path.replace('\\', '/');
        if (normalized.startsWith("__MACOSX/") || normalized.endsWith("/.DS_Store")) {
            return true;
        }
        for (String segment : normalized.split("/")) {
            if (segment.startsWith(".") && !segment.equals(".")) {
                return true;
            }
        }
        return false;
    }

    private String normalizeEntryPath(String entryName) {
        if (entryName == null || entryName.isBlank()) {
            return null;
        }
        String normalized = entryName.replace('\\', '/');
        if (normalized.startsWith("/") || normalized.contains("\0")) {
            return null;
        }
        Path path = Path.of(normalized).normalize();
        if (path.isAbsolute() || path.startsWith("..")) {
            return null;
        }
        String safePath = path.toString().replace('\\', '/');
        if (safePath.isBlank() || safePath.equals(".") || safePath.contains("../")) {
            return null;
        }
        return safePath;
    }

    private String filenameFromPath(String path) {
        int index = path.lastIndexOf('/');
        if (index >= 0 && index + 1 < path.length()) {
            return path.substring(index + 1);
        }
        return path;
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

    private long fileSize(Path path) {
        try {
            return Files.size(path);
        } catch (IOException exception) {
            throw AppException.badRequest("文件保存失败，请重试");
        }
    }

    private void copyFile(Path source, Path target) {
        try {
            Files.createDirectories(target.getParent());
            Files.copy(source, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            throw AppException.badRequest("文件保存失败，请重试");
        }
    }

    private void writeBytes(Path target, byte[] bytes) {
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, bytes);
        } catch (IOException exception) {
            throw AppException.badRequest("文件保存失败，请重试");
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

    private String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw AppException.badRequest("文件保存失败，请重试");
        }
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

    private static final class LimitedByteArrayOutputStream extends java.io.ByteArrayOutputStream {

        private final long limit;

        private LimitedByteArrayOutputStream(long limit) {
            this.limit = limit;
        }

        @Override
        public synchronized void write(byte[] bytes, int offset, int length) {
            if ((long) count + length > limit) {
                throw AppException.badRequest("文件超过大小限制");
            }
            super.write(bytes, offset, length);
        }

        @Override
        public synchronized void write(int value) {
            if ((long) count + 1 > limit) {
                throw AppException.badRequest("文件超过大小限制");
            }
            super.write(value);
        }
    }

    private record PendingMarkdownDocument(
            String originalFilename,
            String archiveEntryPath,
            long fileSize,
            String contentSha256,
            byte[] content
    ) {
    }

    private static final class DocumentParseFailure extends RuntimeException {

        private final int ignoredFileCount;
        private final int skippedFileCount;
        private final String skippedFilesJson;

        private DocumentParseFailure(
                String failureMessage,
                int ignoredFileCount,
                int skippedFileCount,
                String skippedFilesJson
        ) {
            super(failureMessage);
            this.ignoredFileCount = ignoredFileCount;
            this.skippedFileCount = skippedFileCount;
            this.skippedFilesJson = skippedFilesJson;
        }

        private String failureMessage() {
            return getMessage();
        }

        private int ignoredFileCount() {
            return ignoredFileCount;
        }

        private int skippedFileCount() {
            return skippedFileCount;
        }

        private String skippedFilesJson() {
            return skippedFilesJson;
        }
    }
}
