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
@RequestMapping("/api/excluded-questions")
public class ExcludedQuestionController {

    private final ExcludedQuestionService excludedQuestionService;

    public ExcludedQuestionController(ExcludedQuestionService excludedQuestionService) {
        this.excludedQuestionService = excludedQuestionService;
    }

    @GetMapping
    public ApiResponse<PageResponse<ExcludedQuestionResponse>> listExcludedQuestions(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        return ApiResponse.ok(PageResponse.from(excludedQuestionService.listExcludedQuestions(
                principal,
                page,
                pageSize
        )));
    }

    @PostMapping("/{questionId}")
    public ApiResponse<ExcludedQuestionResponse> excludeQuestion(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long questionId
    ) {
        return ApiResponse.ok(excludedQuestionService.excludeQuestion(principal, questionId));
    }

    @DeleteMapping("/{questionId}")
    public ApiResponse<Void> restoreQuestion(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long questionId
    ) {
        excludedQuestionService.restoreQuestion(principal, questionId);
        return ApiResponse.ok();
    }
}
