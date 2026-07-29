package xyz.kangnasi.interview.importjob;

import java.time.Instant;

public record ImportJobLogResponse(
        Long id,
        ImportJobLogLevel level,
        String message,
        String payload,
        Instant createdAt
) {

    public static ImportJobLogResponse from(ImportJobLog log) {
        return new ImportJobLogResponse(
                log.getId(),
                log.getLevel(),
                log.getMessage(),
                log.getPayload(),
                log.getCreatedAt()
        );
    }
}
