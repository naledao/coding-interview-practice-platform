package xyz.kangnasi.interview.question;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import xyz.kangnasi.interview.common.ApiResponse;
import xyz.kangnasi.interview.common.PageResponse;

@RestController
@RequestMapping("/api/admin/questions")
public class AdminQuestionController {

    private final QuestionService questionService;

    public AdminQuestionController(QuestionService questionService) {
        this.questionService = questionService;
    }

    @GetMapping
    public ApiResponse<PageResponse<QuestionSummaryResponse>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) QuestionStatus status,
            @RequestParam(required = false) Long importJobId
    ) {
        return ApiResponse.ok(PageResponse.from(questionService.listAdminQuestions(page, pageSize, status, importJobId)));
    }

    @GetMapping("/{questionId}")
    public ApiResponse<QuestionResponse> detail(@PathVariable Long questionId) {
        return ApiResponse.ok(questionService.questionDetail(null, questionId, true));
    }

    @PostMapping("/{questionId}/disable")
    public ApiResponse<QuestionResponse> disable(@PathVariable Long questionId) {
        return ApiResponse.ok(questionService.disable(questionId));
    }

    @PostMapping("/{questionId}/enable")
    public ApiResponse<QuestionResponse> enable(@PathVariable Long questionId) {
        return ApiResponse.ok(questionService.enable(questionId));
    }
}
