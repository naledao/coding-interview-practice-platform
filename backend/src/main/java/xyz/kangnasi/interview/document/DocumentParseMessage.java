package xyz.kangnasi.interview.document;

public record DocumentParseMessage(
        Long parseTaskId,
        Long uploadId
) {
}
