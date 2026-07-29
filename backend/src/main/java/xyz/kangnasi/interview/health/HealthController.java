package xyz.kangnasi.interview.health;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import xyz.kangnasi.interview.common.ApiResponse;

@RestController
public class HealthController {

    @GetMapping("/")
    public ApiResponse<Map<String, String>> root() {
        return ApiResponse.ok(Map.of(
                "service", "coding-interview-practice-platform-backend",
                "status", "UP"
        ));
    }

    @GetMapping("/api/health")
    public ApiResponse<Map<String, String>> health() {
        return ApiResponse.ok(Map.of("status", "UP"));
    }
}
