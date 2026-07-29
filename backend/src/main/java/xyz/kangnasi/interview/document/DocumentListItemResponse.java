package xyz.kangnasi.interview.document;

import java.time.Instant;
import xyz.kangnasi.interview.importjob.ImportJob;

public record DocumentListItemResponse(
        Long id,
        Long uploadId,
        String originalFilename,
        UploadType sourceType,
        String archiveOriginalFilename,
        String archiveEntryPath,
        long fileSize,
        KnowledgeDocumentStatus status,
        String uploadedBy,
        Instant createdAt,
        LatestJobResponse latestJob
) {

    public static DocumentListItemResponse from(KnowledgeDocument document, ImportJob latestJob) {
        return new DocumentListItemResponse(
                document.getId(),
                document.getUploadId(),
                document.getOriginalFilename(),
                document.getSourceType(),
                document.getArchiveOriginalFilename(),
                document.getArchiveEntryPath(),
                document.getFileSize(),
                document.getStatus(),
                document.getUploadedByName(),
                document.getCreatedAt(),
                LatestJobResponse.from(latestJob)
        );
    }
}
