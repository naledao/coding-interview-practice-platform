package xyz.kangnasi.interview.codextool;

public record ReadImportDocumentToolResponse(
        Long importJobId,
        Long documentId,
        String filename,
        String content
) {
}
