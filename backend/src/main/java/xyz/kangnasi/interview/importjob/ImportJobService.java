package xyz.kangnasi.interview.importjob;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.kangnasi.interview.common.AppException;
import xyz.kangnasi.interview.document.KnowledgeDocument;
import xyz.kangnasi.interview.document.KnowledgeDocumentRepository;
import xyz.kangnasi.interview.question.QuestionService;
import xyz.kangnasi.interview.question.QuestionSummaryResponse;

@Service
public class ImportJobService {

    private final ImportJobRepository importJobRepository;
    private final ImportJobLogRepository importJobLogRepository;
    private final KnowledgeDocumentRepository documentRepository;
    private final CodexJobLauncher codexJobLauncher;
    private final QuestionService questionService;

    public ImportJobService(
            ImportJobRepository importJobRepository,
            ImportJobLogRepository importJobLogRepository,
            KnowledgeDocumentRepository documentRepository,
            CodexJobLauncher codexJobLauncher,
            QuestionService questionService
    ) {
        this.importJobRepository = importJobRepository;
        this.importJobLogRepository = importJobLogRepository;
        this.documentRepository = documentRepository;
        this.codexJobLauncher = codexJobLauncher;
        this.questionService = questionService;
    }

    @Transactional
    public ImportJob createForDocument(KnowledgeDocument document, boolean autoStart) {
        ImportJob job = importJobRepository.save(ImportJob.create(document));
        importJobLogRepository.save(ImportJobLog.create(job.getId(), ImportJobLogLevel.INFO, "导入任务已创建", null));

        if (autoStart) {
            codexJobLauncher.start(job.getId());
        }

        return job;
    }

    @Transactional
    public ImportJob createForDocumentId(Long documentId, boolean autoStart) {
        KnowledgeDocument document = documentRepository.findById(documentId)
                .orElseThrow(() -> AppException.notFound("知识文档不存在"));
        return createForDocument(document, autoStart);
    }

    @Transactional(readOnly = true)
    public Page<ImportJobResponse> list(
            int page,
            int pageSize,
            ImportJobStatus status,
            String documentName,
            String createdFrom,
            String createdTo
    ) {
        PageRequest pageRequest = PageRequest.of(normalizePage(page), normalizePageSize(pageSize), Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<ImportJob> jobs = importJobRepository.search(
                status,
                normalizeKeyword(documentName),
                parseInstant(createdFrom, "createdFrom 参数错误"),
                parseInstant(createdTo, "createdTo 参数错误"),
                pageRequest
        );
        return jobs.map(ImportJobResponse::from);
    }

    @Transactional(readOnly = true)
    public ImportJobResponse detail(Long jobId) {
        return ImportJobResponse.from(importJobRepository.findById(jobId)
                .orElseThrow(() -> AppException.notFound("导入任务不存在")));
    }

    @Transactional(readOnly = true)
    public List<ImportJobLogResponse> logs(Long jobId) {
        if (!importJobRepository.existsById(jobId)) {
            throw AppException.notFound("导入任务不存在");
        }
        return importJobLogRepository.findByImportJobIdOrderByCreatedAtAsc(jobId)
                .stream()
                .map(ImportJobLogResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<QuestionSummaryResponse> questions(Long jobId, int page, int pageSize) {
        if (!importJobRepository.existsById(jobId)) {
            throw AppException.notFound("导入任务不存在");
        }
        return questionService.listImportJobQuestions(jobId, page, pageSize);
    }

    @Transactional
    public ImportJob retry(Long jobId) {
        ImportJob oldJob = importJobRepository.findById(jobId)
                .orElseThrow(() -> AppException.notFound("导入任务不存在"));
        if (oldJob.getStatus() != ImportJobStatus.FAILED) {
            throw AppException.conflict("只有失败任务可以重试");
        }
        return createForDocumentId(oldJob.getDocumentId(), true);
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

    private String normalizeKeyword(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private Instant parseInstant(String value, String message) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value.trim());
        } catch (DateTimeParseException exception) {
            throw AppException.badRequest(message);
        }
    }
}
