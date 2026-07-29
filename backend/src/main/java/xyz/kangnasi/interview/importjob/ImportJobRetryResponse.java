package xyz.kangnasi.interview.importjob;

public record ImportJobRetryResponse(
        Long newImportJobId,
        ImportJobStatus status
) {

    public static ImportJobRetryResponse from(ImportJob job) {
        return new ImportJobRetryResponse(job.getId(), job.getStatus());
    }
}
