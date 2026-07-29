package xyz.kangnasi.interview.codextool;

public record MarkImportJobFailedToolRequest(
        Long importJobId,
        String reason
) {
}
