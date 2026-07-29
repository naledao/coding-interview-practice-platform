package xyz.kangnasi.interview.document;

import xyz.kangnasi.interview.importjob.ImportJob;

public record UploadResultDocumentResponse(
        Long documentId,
        String originalFilename,
        String archiveEntryPath,
        long fileSize,
        KnowledgeDocumentStatus documentStatus,
        LatestJobResponse latestJob
) {

    public static UploadResultDocumentResponse from(KnowledgeDocument document, ImportJob latestJob) {
        return new UploadResultDocumentResponse(
                document.getId(),
                document.getOriginalFilename(),
                document.getArchiveEntryPath(),
                document.getFileSize(),
                document.getStatus(),
                LatestJobResponse.from(latestJob)
        );
    }
}
