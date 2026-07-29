package xyz.kangnasi.interview.document;

import java.time.Instant;
import java.util.List;

public record UploadDetailResponse(
        Long id,
        UploadType uploadType,
        String originalFilename,
        long fileSize,
        String uploadSha256,
        UploadParseStatus parseStatus,
        Long parseTaskId,
        DocumentParseTaskStatus parseTaskStatus,
        String parseFailedReason,
        int documentCount,
        int ignoredFileCount,
        int skippedFileCount,
        List<SkippedFileResponse> skippedFiles,
        String uploadedBy,
        Instant createdAt,
        List<UploadResultDocumentResponse> documents
) {

    public static UploadDetailResponse from(
            DocumentUpload upload,
            List<SkippedFileResponse> skippedFiles,
            DocumentParseTask parseTask,
            List<UploadResultDocumentResponse> documents
    ) {
        return new UploadDetailResponse(
                upload.getId(),
                upload.getUploadType(),
                upload.getOriginalFilename(),
                upload.getFileSize(),
                upload.getUploadSha256(),
                upload.getParseStatus(),
                parseTask == null ? null : parseTask.getId(),
                parseTask == null ? null : parseTask.getStatus(),
                parseTask == null ? null : parseTask.getFailedReason(),
                upload.getDocumentCount(),
                upload.getIgnoredFileCount(),
                upload.getSkippedFileCount(),
                skippedFiles,
                upload.getUploadedByName(),
                upload.getCreatedAt(),
                documents
        );
    }
}
