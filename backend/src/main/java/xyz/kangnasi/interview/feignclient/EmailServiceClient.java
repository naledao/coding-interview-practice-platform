package xyz.kangnasi.interview.feignclient;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import xyz.kangnasi.interview.email.EmailAcceptedResponse;
import xyz.kangnasi.interview.email.EmailSendRequest;

@FeignClient(name = "email-service")
public interface EmailServiceClient {

    @PostMapping(
            value = "/api/email/send",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    ResponseEntity<EmailAcceptedResponse> send(@RequestBody EmailSendRequest request);
}
