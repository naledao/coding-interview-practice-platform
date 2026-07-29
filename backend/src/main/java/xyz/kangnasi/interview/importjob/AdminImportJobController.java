package xyz.kangnasi.interview.importjob;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import xyz.kangnasi.interview.common.ApiResponse;
import xyz.kangnasi.interview.common.PageResponse;

@RestController
@RequestMapping("/api/admin/import-jobs")
public class AdminImportJobController {

    private final ImportJobService importJobService;

    public AdminImportJobController(ImportJobService importJobService) {
        this.importJobService = importJobService;
    }

    @GetMapping
    public ApiResponse<PageResponse<ImportJobResponse>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) ImportJobStatus status,
            @RequestParam(required = false) String documentName,
            @RequestParam(required = false) String createdFrom,
            @RequestParam(required = false) String createdTo
    ) {
        return ApiResponse.ok(PageResponse.from(importJobService.list(
                page,
                pageSize,
                status,
                documentName,
                createdFrom,
                createdTo
        )));
    }

    @GetMapping("/{jobId}")
    public ApiResponse<ImportJobResponse> detail(@PathVariable Long jobId) {
        return ApiResponse.ok(importJobService.detail(jobId));
    }

    @GetMapping("/{jobId}/logs")
    public ApiResponse<List<ImportJobLogResponse>> logs(@PathVariable Long jobId) {
        return ApiResponse.ok(importJobService.logs(jobId));
    }

    @GetMapping("/{jobId}/questions")
    public ApiResponse<PageResponse<xyz.kangnasi.interview.question.QuestionSummaryResponse>> questions(
            @PathVariable Long jobId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        return ApiResponse.ok(PageResponse.from(importJobService.questions(jobId, page, pageSize)));
    }

    @PostMapping("/{jobId}/retry")
    public ApiResponse<ImportJobRetryResponse> retry(@PathVariable Long jobId) {
        return ApiResponse.ok(ImportJobRetryResponse.from(importJobService.retry(jobId)));
    }
}
