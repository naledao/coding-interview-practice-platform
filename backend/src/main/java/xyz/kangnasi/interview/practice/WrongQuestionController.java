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
@RequestMapping("/api/wrong-questions")
public class WrongQuestionController {

    private final ReviewService reviewService;

    public WrongQuestionController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @GetMapping
    public ApiResponse<PageResponse<WrongQuestionResponse>> listWrongQuestions(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Boolean mastered
    ) {
        return ApiResponse.ok(PageResponse.from(reviewService.listWrongQuestions(principal, page, pageSize, mastered)));
    }

    @PostMapping("/{questionId}/master")
    public ApiResponse<WrongQuestionResponse> markMastered(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long questionId
    ) {
        return ApiResponse.ok(reviewService.markMastered(principal, questionId));
    }

    @PostMapping("/{questionId}/unmaster")
    public ApiResponse<WrongQuestionResponse> unmarkMastered(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long questionId
    ) {
        return ApiResponse.ok(reviewService.unmarkMastered(principal, questionId));
    }

    @DeleteMapping("/{questionId}")
    public ApiResponse<Void> removeWrongQuestion(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long questionId
    ) {
        reviewService.removeWrongQuestion(principal, questionId);
        return ApiResponse.ok();
    }
}
