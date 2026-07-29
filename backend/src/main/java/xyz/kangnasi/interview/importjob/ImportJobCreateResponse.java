package xyz.kangnasi.interview.importjob;

public record ImportJobCreateResponse(
        Long importJobId,
        ImportJobStatus status
) {

    public static ImportJobCreateResponse from(ImportJob job) {
        return new ImportJobCreateResponse(job.getId(), job.getStatus());
    }
}
