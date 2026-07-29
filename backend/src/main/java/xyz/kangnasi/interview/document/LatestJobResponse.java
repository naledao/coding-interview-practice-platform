package xyz.kangnasi.interview.document;

import xyz.kangnasi.interview.importjob.ImportJob;
import xyz.kangnasi.interview.importjob.ImportJobStatus;

public record LatestJobResponse(
        Long id,
        ImportJobStatus status,
        int generatedQuestionCount
) {

    public static LatestJobResponse from(ImportJob job) {
        if (job == null) {
            return null;
        }
        return new LatestJobResponse(job.getId(), job.getStatus(), job.getGeneratedQuestionCount());
    }
}
