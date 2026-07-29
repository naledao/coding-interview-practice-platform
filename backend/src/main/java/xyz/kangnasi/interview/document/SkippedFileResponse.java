package xyz.kangnasi.interview.document;

public record SkippedFileResponse(
        String archiveEntryPath,
        String reason
) {
}
