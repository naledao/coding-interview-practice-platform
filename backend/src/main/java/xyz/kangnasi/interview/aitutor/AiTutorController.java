package xyz.kangnasi.interview.aitutor;

import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import xyz.kangnasi.interview.auth.UserPrincipal;
import xyz.kangnasi.interview.common.ApiResponse;

@RestController
@RequestMapping("/api/ai-tutor")
public class AiTutorController {

    private final AiTutorService aiTutorService;

    public AiTutorController(AiTutorService aiTutorService) {
        this.aiTutorService = aiTutorService;
    }

    @GetMapping("/models")
    public ApiResponse<AiTutorModelCatalog> listModels() {
        return ApiResponse.ok(aiTutorService.listModels());
    }

    @PostMapping("/runs")
    public ResponseEntity<ApiResponse<AiTutorRunAccepted>> createRun(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody AiTutorRunRequest request
    ) {
        AiTutorRunAccepted accepted = aiTutorService.createRun(principal, request);
        ResponseEntity.BodyBuilder response = accepted.deduplicated()
                ? ResponseEntity.ok()
                : ResponseEntity.accepted();
        return response
                .location(URI.create("/api/ai-tutor/runs/" + accepted.runId()))
                .header("Cache-Control", "no-cache, no-store, no-transform")
                .body(ApiResponse.ok(accepted));
    }

    @PostMapping("/runs/{runId}/cancel")
    public ApiResponse<AiTutorRunCancelResult> cancelRun(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String runId
    ) {
        return ApiResponse.ok(aiTutorService.cancelRun(principal, runId));
    }
}
