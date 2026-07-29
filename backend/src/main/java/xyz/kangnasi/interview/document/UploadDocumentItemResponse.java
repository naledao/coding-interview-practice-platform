package xyz.kangnasi.interview.document;

import xyz.kangnasi.interview.importjob.ImportJob;
import xyz.kangnasi.interview.importjob.ImportJobStatus;

public record UploadDocumentItemResponse(
        Long documentId,
        String originalFilename,
        String archiveEntryPath,
        long fileSize,
        Long importJobId,
        KnowledgeDocumentStatus documentStatus,
        ImportJobStatus jobStatus
) {

    public static UploadDocumentItemResponse from(KnowledgeDocument document, ImportJob job) {
        return new UploadDocumentItemResponse(
                document.getId(),
                document.getOriginalFilename(),
                document.getArchiveEntryPath(),
                document.getFileSize(),
                job == null ? null : job.getId(),
                document.getStatus(),
                job == null ? null : job.getStatus()
        );
    }
}
