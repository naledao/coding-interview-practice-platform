package xyz.kangnasi.interview.codextool;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import xyz.kangnasi.interview.common.ApiResponse;

@RestController
@RequestMapping("/api/codex-tools")
public class CodexToolController {

    private final CodexToolService codexToolService;
    private final CodexToolAccessGuard accessGuard;

    public CodexToolController(CodexToolService codexToolService, CodexToolAccessGuard accessGuard) {
        this.codexToolService = codexToolService;
        this.accessGuard = accessGuard;
    }

    @PostMapping("/get_import_job")
    public ApiResponse<GetImportJobToolResponse> getImportJob(
            @RequestBody GetImportJobToolRequest request,
            HttpServletRequest servletRequest
    ) {
        accessGuard.requireAllowed(servletRequest);
        return ApiResponse.ok(codexToolService.getImportJob(request));
    }

    @PostMapping("/get_generated_question_count")
    public ApiResponse<GetGeneratedQuestionCountToolResponse> getGeneratedQuestionCount(
            @RequestBody GetGeneratedQuestionCountToolRequest request,
            HttpServletRequest servletRequest
    ) {
        accessGuard.requireAllowed(servletRequest);
        return ApiResponse.ok(codexToolService.getGeneratedQuestionCount(request));
    }

    @PostMapping("/get_recommended_tags")
    public ApiResponse<GetRecommendedTagsToolResponse> getRecommendedTags(HttpServletRequest servletRequest) {
        accessGuard.requireAllowed(servletRequest);
        return ApiResponse.ok(codexToolService.getRecommendedTags());
    }

    @PostMapping("/read_import_document")
    public ApiResponse<ReadImportDocumentToolResponse> readImportDocument(
            @RequestBody ReadImportDocumentToolRequest request,
            HttpServletRequest servletRequest
    ) {
        accessGuard.requireAllowed(servletRequest);
        return ApiResponse.ok(codexToolService.readImportDocument(request));
    }

    @PostMapping("/append_generation_log")
    public ApiResponse<CodexToolOkResponse> appendGenerationLog(
            @RequestBody AppendGenerationLogToolRequest request,
            HttpServletRequest servletRequest
    ) {
        accessGuard.requireAllowed(servletRequest);
        return ApiResponse.ok(codexToolService.appendGenerationLog(request));
    }

    @PostMapping("/create_question_batch")
    public ApiResponse<CreateQuestionBatchToolResponse> createQuestionBatch(
            @RequestBody CreateQuestionBatchToolRequest request,
            HttpServletRequest servletRequest
    ) {
        accessGuard.requireAllowed(servletRequest);
        return ApiResponse.ok(codexToolService.createQuestionBatch(request));
    }

    @PostMapping("/validate_question_batch")
    public ApiResponse<ValidateQuestionBatchToolResponse> validateQuestionBatch(
            @RequestBody CreateQuestionBatchToolRequest request,
            HttpServletRequest servletRequest
    ) {
        accessGuard.requireAllowed(servletRequest);
        return ApiResponse.ok(codexToolService.validateQuestionBatch(request));
    }

    @PostMapping("/mark_import_job_running")
    public ApiResponse<CodexToolOkResponse> markImportJobRunning(
            @RequestBody MarkImportJobRunningToolRequest request,
            HttpServletRequest servletRequest
    ) {
        accessGuard.requireAllowed(servletRequest);
        return ApiResponse.ok(codexToolService.markImportJobRunning(request));
    }

    @PostMapping("/mark_import_job_succeeded")
    public ApiResponse<CodexToolOkResponse> markImportJobSucceeded(
            @RequestBody MarkImportJobSucceededToolRequest request,
            HttpServletRequest servletRequest
    ) {
        accessGuard.requireAllowed(servletRequest);
        return ApiResponse.ok(codexToolService.markImportJobSucceeded(request));
    }

    @PostMapping("/mark_import_job_failed")
    public ApiResponse<CodexToolOkResponse> markImportJobFailed(
            @RequestBody MarkImportJobFailedToolRequest request,
            HttpServletRequest servletRequest
    ) {
        accessGuard.requireAllowed(servletRequest);
        return ApiResponse.ok(codexToolService.markImportJobFailed(request));
    }
}
