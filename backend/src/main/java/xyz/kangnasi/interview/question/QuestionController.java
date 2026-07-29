package xyz.kangnasi.interview.question;

import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import xyz.kangnasi.interview.auth.UserPrincipal;
import xyz.kangnasi.interview.common.ApiResponse;
import xyz.kangnasi.interview.common.PageResponse;
import xyz.kangnasi.interview.practice.PracticeService;

@RestController
@RequestMapping("/api")
public class QuestionController {

    private final QuestionService questionService;
    private final PracticeService practiceService;

    public QuestionController(QuestionService questionService, PracticeService practiceService) {
        this.questionService = questionService;
        this.practiceService = practiceService;
    }

    @GetMapping("/questions")
    public ApiResponse<PageResponse<QuestionSummaryResponse>> listQuestions(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) QuestionDifficulty difficulty,
            @RequestParam(required = false) Long tagId,
            @RequestParam(required = false) String keyword
    ) {
        return ApiResponse.ok(PageResponse.from(questionService.listUserQuestions(principal, page, pageSize, difficulty, tagId, keyword)));
    }

    @GetMapping("/questions/{questionId}")
    public ApiResponse<QuestionResponse> questionDetail(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long questionId
    ) {
        return ApiResponse.ok(questionService.questionDetail(principal, questionId, false));
    }

    @PostMapping("/questions/{questionId}/answer")
    public ApiResponse<AnswerQuestionResponse> answerQuestion(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long questionId,
            @RequestBody AnswerQuestionRequest request
    ) {
        return ApiResponse.ok(practiceService.answerQuestion(principal, questionId, request));
    }

    @GetMapping("/questions/{questionId}/analysis")
    public ApiResponse<QuestionResponse> questionAnalysis(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long questionId
    ) {
        return ApiResponse.ok(questionService.questionAnalysis(principal, questionId));
    }

    @GetMapping("/tags")
    public ApiResponse<List<QuestionTagResponse>> tags() {
        return ApiResponse.ok(questionService.listTags());
    }
}
