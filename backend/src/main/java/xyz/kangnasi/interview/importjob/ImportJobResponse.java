package xyz.kangnasi.interview.importjob;

import java.time.Instant;

public record ImportJobResponse(
        Long id,
        Long documentId,
        String documentName,
        ImportJobStatus status,
        int generatedQuestionCount,
        String codexSessionId,
        String failedReason,
        Instant startedAt,
        Instant finishedAt,
        Instant createdAt
) {

    public static ImportJobResponse from(ImportJob job) {
        return new ImportJobResponse(
                job.getId(),
                job.getDocumentId(),
                job.getDocumentName(),
                job.getStatus(),
                job.getGeneratedQuestionCount(),
                job.getCodexSessionId(),
                job.getFailedReason(),
                job.getStartedAt(),
                job.getFinishedAt(),
                job.getCreatedAt()
        );
    }
}
