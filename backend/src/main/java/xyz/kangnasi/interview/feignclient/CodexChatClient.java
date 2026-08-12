package xyz.kangnasi.interview.feignclient;

import feign.Response;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import xyz.kangnasi.interview.aitutor.AiTutorModelCatalog;
import xyz.kangnasi.interview.aitutor.AiTutorRunAccepted;
import xyz.kangnasi.interview.aitutor.AiTutorRunCancelResult;
import xyz.kangnasi.interview.aitutor.CodexRunCreateRequest;
import xyz.kangnasi.interview.aitutor.CodexRunReference;

@FeignClient(name = "codex-chat-service")
public interface CodexChatClient {

    @GetMapping("/api/chat/models")
    AiTutorModelCatalog listModels();

    @PostMapping(value = "/api/chat/runs", consumes = MediaType.APPLICATION_JSON_VALUE)
    AiTutorRunAccepted createRun(@RequestBody CodexRunCreateRequest request);

    @GetMapping("/api/chat/runs/{runId}")
    CodexRunReference getRun(@PathVariable("runId") String runId);

    @GetMapping(value = "/api/chat/runs/{runId}/events/stream", produces = "application/x-ndjson")
    Response streamEvents(
            @PathVariable("runId") String runId,
            @RequestParam("afterId") long afterId
    );

    @PostMapping("/api/chat/runs/{runId}/cancel")
    AiTutorRunCancelResult cancelRun(@PathVariable("runId") String runId);
}
