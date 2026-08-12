package xyz.kangnasi.interview.aitutor;

public record AiTutorRunAccepted(
        String conversationId,
        String runId,
        String clientRequestId,
        Integer turnNo,
        String status,
        boolean deduplicated
) {
}
