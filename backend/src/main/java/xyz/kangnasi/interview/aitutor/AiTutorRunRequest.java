package xyz.kangnasi.interview.aitutor;

public record AiTutorRunRequest(
        String clientRequestId,
        String conversationId,
        Long questionId,
        String input,
        String model,
        String reasoningEffort
) {
}
