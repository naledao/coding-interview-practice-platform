package xyz.kangnasi.interview.statistics;

import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import xyz.kangnasi.interview.auth.UserPrincipal;
import xyz.kangnasi.interview.common.ApiResponse;

@RestController
@RequestMapping("/api/statistics")
public class StatisticsController {

    private final StatisticsService statisticsService;

    public StatisticsController(StatisticsService statisticsService) {
        this.statisticsService = statisticsService;
    }

    @GetMapping("/overview")
    public ApiResponse<StatisticsOverviewResponse> overview(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(statisticsService.overview(principal));
    }

    @GetMapping("/tags")
    public ApiResponse<List<TagStatisticsResponse>> tags(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) String sort
    ) {
        return ApiResponse.ok(statisticsService.tags(principal, sort));
    }

    @GetMapping("/daily")
    public ApiResponse<List<DailyStatisticsResponse>> daily(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) Integer days
    ) {
        return ApiResponse.ok(statisticsService.daily(principal, days));
    }
}
