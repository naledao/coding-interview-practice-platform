package xyz.kangnasi.interview.practice;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import xyz.kangnasi.interview.auth.UserPrincipal;
import xyz.kangnasi.interview.common.ApiResponse;
import xyz.kangnasi.interview.common.PageResponse;
import xyz.kangnasi.interview.question.QuestionDifficulty;
import xyz.kangnasi.interview.question.QuestionResponse;

@RestController
@RequestMapping("/api/practice")
public class PracticeController {

    private final PracticeService practiceService;

    public PracticeController(PracticeService practiceService) {
        this.practiceService = practiceService;
    }

    @GetMapping("/answered-questions")
    public ApiResponse<PageResponse<AnsweredQuestionResponse>> answeredQuestions(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        return ApiResponse.ok(PageResponse.from(practiceService.listAnsweredQuestions(principal, page, pageSize)));
    }

    @GetMapping("/count")
    public ApiResponse<PracticeQuestionCountResponse> countQuestions(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "RANDOM") String mode,
            @RequestParam(required = false) QuestionDifficulty difficulty,
            @RequestParam(required = false) Long tagId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "false") boolean excludeAnswered
    ) {
        return ApiResponse.ok(new PracticeQuestionCountResponse(practiceService.countQuestions(
                principal,
                mode,
                difficulty,
                tagId,
                keyword,
                excludeAnswered
        )));
    }

    @GetMapping("/next")
    public ApiResponse<QuestionResponse> nextQuestion(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "RANDOM") String mode,
            @RequestParam(required = false) QuestionDifficulty difficulty,
            @RequestParam(required = false) Long tagId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "false") boolean excludeAnswered,
            @RequestParam(required = false) Long currentQuestionId
    ) {
        return ApiResponse.ok(practiceService.nextQuestion(
                principal,
                mode,
                difficulty,
                tagId,
                keyword,
                excludeAnswered,
                currentQuestionId
        ));
    }
}
