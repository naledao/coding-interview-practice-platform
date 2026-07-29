package xyz.kangnasi.interview.codextool;

import xyz.kangnasi.interview.importjob.ImportJob;
import xyz.kangnasi.interview.importjob.ImportJobStatus;

public record GetImportJobToolResponse(
        Long importJobId,
        ImportJobStatus status,
        Long documentId,
        String documentName,
        int generatedQuestionCount
) {

    public static GetImportJobToolResponse from(ImportJob job) {
        return new GetImportJobToolResponse(
                job.getId(),
                job.getStatus(),
                job.getDocumentId(),
                job.getDocumentName(),
                job.getGeneratedQuestionCount()
        );
    }
}
