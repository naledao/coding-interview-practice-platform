package xyz.kangnasi.interview.document;

import java.time.Instant;
import xyz.kangnasi.interview.importjob.ImportJob;

public record DocumentDetailResponse(
        Long id,
        Long uploadId,
        String originalFilename,
        UploadType sourceType,
        String archiveOriginalFilename,
        String archiveEntryPath,
        long fileSize,
        String contentSha256,
        String storedPath,
        KnowledgeDocumentStatus status,
        String uploadedBy,
        Instant createdAt,
        LatestJobResponse latestJob,
        int generatedQuestionCount,
        String content
) {

    public static DocumentDetailResponse from(KnowledgeDocument document, ImportJob latestJob) {
        return from(document, latestJob, "");
    }

    public static DocumentDetailResponse from(KnowledgeDocument document, ImportJob latestJob, String content) {
        int generatedQuestionCount = latestJob == null ? 0 : latestJob.getGeneratedQuestionCount();
        return new DocumentDetailResponse(
                document.getId(),
                document.getUploadId(),
                document.getOriginalFilename(),
                document.getSourceType(),
                document.getArchiveOriginalFilename(),
                document.getArchiveEntryPath(),
                document.getFileSize(),
                document.getContentSha256(),
                document.getStoredPath(),
                document.getStatus(),
                document.getUploadedByName(),
                document.getCreatedAt(),
                LatestJobResponse.from(latestJob),
                generatedQuestionCount,
                content
        );
    }
}
