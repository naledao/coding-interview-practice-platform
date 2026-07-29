package xyz.kangnasi.interview.document;

import java.util.List;
import xyz.kangnasi.interview.importjob.ImportJob;
import xyz.kangnasi.interview.importjob.ImportJobStatus;

public record DocumentUploadResponse(
        Long uploadId,
        UploadType uploadType,
        String originalFilename,
        UploadParseStatus parseStatus,
        Long parseTaskId,
        DocumentParseTaskStatus parseTaskStatus,
        Long documentId,
        Long importJobId,
        KnowledgeDocumentStatus documentStatus,
        ImportJobStatus jobStatus,
        int documentCount,
        int ignoredFileCount,
        int skippedFileCount,
        List<SkippedFileResponse> skippedFiles,
        List<UploadDocumentItemResponse> documents
) {

    public static DocumentUploadResponse queued(DocumentUpload upload, DocumentParseTask parseTask) {
        return new DocumentUploadResponse(
                upload.getId(),
                upload.getUploadType(),
                upload.getOriginalFilename(),
                upload.getParseStatus(),
                parseTask.getId(),
                parseTask.getStatus(),
                null,
                null,
                null,
                null,
                upload.getDocumentCount(),
                upload.getIgnoredFileCount(),
                upload.getSkippedFileCount(),
                List.of(),
                List.of()
        );
    }

    public static DocumentUploadResponse markdown(DocumentUpload upload, KnowledgeDocument document, ImportJob job) {
        return new DocumentUploadResponse(
                upload.getId(),
                upload.getUploadType(),
                upload.getOriginalFilename(),
                upload.getParseStatus(),
                null,
                null,
                document.getId(),
                job.getId(),
                document.getStatus(),
                job.getStatus(),
                1,
                0,
                0,
                List.of(),
                List.of(UploadDocumentItemResponse.from(document, job))
        );
    }

    public static DocumentUploadResponse zip(
            DocumentUpload upload,
            List<SkippedFileResponse> skippedFiles,
            List<UploadDocumentItemResponse> documents
    ) {
        return new DocumentUploadResponse(
                upload.getId(),
                upload.getUploadType(),
                upload.getOriginalFilename(),
                upload.getParseStatus(),
                null,
                null,
                null,
                null,
                null,
                null,
                upload.getDocumentCount(),
                upload.getIgnoredFileCount(),
                upload.getSkippedFileCount(),
                skippedFiles,
                documents
        );
    }
}
