package xyz.kangnasi.interview.email;

public record EmailSendRequest(
        String requestId,
        String sourceService,
        String businessType,
        String to,
        String subject,
        String content
) {
}
