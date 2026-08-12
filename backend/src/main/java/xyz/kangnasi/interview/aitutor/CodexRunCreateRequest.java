package xyz.kangnasi.interview.aitutor;

public record CodexRunCreateRequest(
        String clientRequestId,
        String conversationId,
        CodexRunInput input,
        CodexRunContext context,
        CodexRunOptions options
) {
}
