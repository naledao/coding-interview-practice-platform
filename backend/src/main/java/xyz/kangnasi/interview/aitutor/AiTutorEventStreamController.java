package xyz.kangnasi.interview.aitutor;

import feign.Response;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import xyz.kangnasi.interview.auth.UserPrincipal;
import xyz.kangnasi.interview.common.AppException;

@RestController
@RequestMapping("/api/ai-tutor")
public class AiTutorEventStreamController {

    private static final MediaType NDJSON = MediaType.parseMediaType("application/x-ndjson;charset=UTF-8");

    private final AiTutorService aiTutorService;
    private final CodexChatGateway gateway;

    public AiTutorEventStreamController(AiTutorService aiTutorService, CodexChatGateway gateway) {
        this.aiTutorService = aiTutorService;
        this.gateway = gateway;
    }

    @GetMapping(value = "/runs/{runId}/events/stream", produces = "application/x-ndjson;charset=UTF-8")
    public ResponseEntity<StreamingResponseBody> streamEvents(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String runId,
            @RequestParam(defaultValue = "0") long afterId
    ) {
        if (afterId < 0) {
            throw AppException.badRequest("afterId 不能为负数");
        }
        String normalizedRunId = aiTutorService.requireOwnedRun(principal, runId);
        StreamingResponseBody body = output -> copyStream(normalizedRunId, afterId, output);
        return ResponseEntity.ok()
                .contentType(NDJSON)
                .header("Cache-Control", "no-cache, no-store, no-transform")
                .header("X-Accel-Buffering", "no")
                .header("X-Content-Type-Options", "nosniff")
                .body(body);
    }

    private void copyStream(String runId, long afterId, OutputStream output) throws IOException {
        try (Response response = gateway.streamEvents(runId, afterId)) {
            if (response == null || response.status() < 200 || response.status() >= 300
                    || response.body() == null) {
                throw new IOException("AI 助教服务未返回事件流");
            }
            try (InputStream input = response.body().asInputStream()) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    output.write(buffer, 0, read);
                    output.flush();
                }
            }
        }
    }
}
