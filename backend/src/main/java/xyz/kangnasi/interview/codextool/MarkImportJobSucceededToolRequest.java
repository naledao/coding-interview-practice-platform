package xyz.kangnasi.interview.codextool;

public record MarkImportJobSucceededToolRequest(
        Long importJobId,
        Integer generatedQuestionCount,
        String summary
) {
}
