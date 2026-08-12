package xyz.kangnasi.interview.email;

import feign.FeignException;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import xyz.kangnasi.interview.common.AppException;
import xyz.kangnasi.interview.feignclient.EmailServiceClient;

@Component
public class EmailServiceGateway {

    private static final String QUEUED_STATUS = "QUEUED";

    private final EmailServiceClient client;
    private final String sourceService;

    public EmailServiceGateway(
            EmailServiceClient client,
            @Value("${spring.application.name}") String sourceService
    ) {
        this.client = client;
        this.sourceService = sourceService;
    }

    public void send(String businessType, String recipient, String subject, String content) {
        String requestId = UUID.randomUUID().toString();
        EmailSendRequest request = new EmailSendRequest(
                requestId,
                sourceService,
                businessType,
                recipient,
                subject,
                content
        );

        ResponseEntity<EmailAcceptedResponse> response;
        try {
            response = client.send(request);
        } catch (FeignException exception) {
            throw translate(exception);
        }

        if (response == null || response.getStatusCode().value() != HttpStatus.ACCEPTED.value()) {
            throw unavailable();
        }
        EmailAcceptedResponse body = response.getBody();
        if (body == null
                || !requestId.equals(body.messageId())
                || !QUEUED_STATUS.equals(body.status())) {
            throw unavailable();
        }
    }

    private AppException translate(FeignException exception) {
        if (exception.status() == HttpStatus.BAD_REQUEST.value()) {
            return AppException.badRequest("邮箱格式不正确");
        }
        return unavailable();
    }

    private AppException unavailable() {
        return AppException.serviceUnavailable("验证码邮件服务暂时不可用，请稍后重试");
    }
}
