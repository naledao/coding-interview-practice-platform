package xyz.kangnasi.interview.practice;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import xyz.kangnasi.interview.auth.UserPrincipal;
import xyz.kangnasi.interview.common.ApiResponse;
import xyz.kangnasi.interview.common.PageResponse;

@RestController
@RequestMapping("/api/favorites")
public class FavoriteQuestionController {

    private final ReviewService reviewService;

    public FavoriteQuestionController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @GetMapping
    public ApiResponse<PageResponse<FavoriteQuestionResponse>> listFavorites(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        return ApiResponse.ok(PageResponse.from(reviewService.listFavorites(principal, page, pageSize)));
    }

    @PostMapping("/{questionId}")
    public ApiResponse<FavoriteQuestionResponse> favoriteQuestion(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long questionId
    ) {
        return ApiResponse.ok(reviewService.favoriteQuestion(principal, questionId));
    }

    @DeleteMapping("/{questionId}")
    public ApiResponse<Void> unfavoriteQuestion(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long questionId
    ) {
        reviewService.unfavoriteQuestion(principal, questionId);
        return ApiResponse.ok();
    }
}
