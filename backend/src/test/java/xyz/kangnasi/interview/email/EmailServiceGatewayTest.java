package xyz.kangnasi.interview.email;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import xyz.kangnasi.interview.common.AppException;
import xyz.kangnasi.interview.feignclient.EmailServiceClient;

class EmailServiceGatewayTest {

    @Test
    void submitsEmailWithServiceIdentityAndBusinessType() {
        EmailServiceClient client = mock(EmailServiceClient.class);
        EmailServiceGateway gateway = new EmailServiceGateway(client, "coding-interview-practice-platform-service");
        when(client.send(any(EmailSendRequest.class))).thenAnswer(invocation -> {
            EmailSendRequest request = invocation.getArgument(0);
            UUID.fromString(request.requestId());
            assertEquals("coding-interview-practice-platform-service", request.sourceService());
            assertEquals("LOGIN_CODE", request.businessType());
            assertEquals("user@example.com", request.to());
            return ResponseEntity.accepted().body(
                    new EmailAcceptedResponse(request.requestId(), "QUEUED")
            );
        });

        gateway.send("LOGIN_CODE", "user@example.com", "登录验证码", "验证码正文");
    }

    @Test
    void rejectsUnexpectedEmailServiceResponse() {
        EmailServiceClient client = mock(EmailServiceClient.class);
        EmailServiceGateway gateway = new EmailServiceGateway(client, "coding-interview-practice-platform-service");
        when(client.send(any(EmailSendRequest.class))).thenReturn(
                ResponseEntity.ok(new EmailAcceptedResponse("unexpected", "QUEUED"))
        );

        AppException exception = assertThrows(
                AppException.class,
                () -> gateway.send("LOGIN_CODE", "user@example.com", "登录验证码", "验证码正文")
        );

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.getStatus());
    }
}
