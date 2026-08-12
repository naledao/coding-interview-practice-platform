package xyz.kangnasi.interview.codextool;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;
import xyz.kangnasi.interview.importjob.ImportJobLogLevel;

@Service
public class CodexMcpToolService {

    private final CodexToolService codexToolService;

    public CodexMcpToolService(CodexToolService codexToolService) {
        this.codexToolService = codexToolService;
    }

    @McpTool(
            name = "get_import_job",
            description = "读取导入任务基础信息。参数：importJobId。"
    )
    public GetImportJobToolResponse getImportJob(
            @McpToolParam(description = "导入任务 ID") Long importJobId
    ) {
        return codexToolService.getImportJob(new GetImportJobToolRequest(importJobId));
    }

    @McpTool(
            name = "get_generated_question_count",
            description = "读取某个导入任务已生成入库的题目数量。参数：importJobId。"
    )
    public GetGeneratedQuestionCountToolResponse getGeneratedQuestionCount(
            @McpToolParam(description = "导入任务 ID") Long importJobId
    ) {
        return codexToolService.getGeneratedQuestionCount(new GetGeneratedQuestionCountToolRequest(importJobId));
    }

    @McpTool(
            name = "get_recommended_tags",
            description = "读取推荐标签列表，用于给 Java 后端或 Java 智能体面试单选题打标签。无参数。"
    )
    public GetRecommendedTagsToolResponse getRecommendedTags() {
        return codexToolService.getRecommendedTags();
    }

    @McpTool(
            name = "read_import_document",
            description = "读取导入任务关联的 Markdown 文档内容。参数：importJobId。"
    )
    public ReadImportDocumentToolResponse readImportDocument(
            @McpToolParam(description = "导入任务 ID") Long importJobId
    ) {
        return codexToolService.readImportDocument(new ReadImportDocumentToolRequest(importJobId));
    }

    @McpTool(
            name = "append_generation_log",
            description = "追加导入任务执行日志。level 可为 INFO、WARN、ERROR，payload 可为空。"
    )
    public CodexToolOkResponse appendGenerationLog(
            @McpToolParam(description = "导入任务 ID") Long importJobId,
            @McpToolParam(description = "日志级别：INFO、WARN、ERROR", required = false) ImportJobLogLevel level,
            @McpToolParam(description = "日志内容，最长 1000 字") String message,
            @McpToolParam(description = "附加 JSON payload，可为空", required = false) JsonNode payload
    ) {
        return codexToolService.appendGenerationLog(new AppendGenerationLogToolRequest(
                importJobId,
                level,
                message,
                payload
        ));
    }

    @McpTool(
            name = "validate_question_batch",
            description = "校验一批 Java 后端或 Java 智能体面试单选题，不写入数据库。每题必须有 stem、difficulty、knowledgePoint、answerAnalysis、codexReviewSummary、tags、options。"
    )
    public ValidateQuestionBatchToolResponse validateQuestionBatch(
            @McpToolParam(description = "导入任务 ID") Long importJobId,
            @McpToolParam(description = "待校验的题目列表，最多 50 题") List<CodexQuestionPayload> questions
    ) {
        return codexToolService.validateQuestionBatch(new CreateQuestionBatchToolRequest(importJobId, questions));
    }

    @McpTool(
            name = "create_question_batch",
            description = "分批写入 Java 后端或 Java 智能体面试单选题。每题 4 个 A/B/C/D 选项且只有一个 correct=true。"
    )
    public CreateQuestionBatchToolResponse createQuestionBatch(
            @McpToolParam(description = "导入任务 ID") Long importJobId,
            @McpToolParam(description = "待写入的题目列表，最多 50 题") List<CodexQuestionPayload> questions
    ) {
        return codexToolService.createQuestionBatch(new CreateQuestionBatchToolRequest(importJobId, questions));
    }

    @McpTool(
            name = "mark_import_job_running",
            description = "将导入任务标记为运行中。参数：importJobId。"
    )
    public CodexToolOkResponse markImportJobRunning(
            @McpToolParam(description = "导入任务 ID") Long importJobId
    ) {
        return codexToolService.markImportJobRunning(new MarkImportJobRunningToolRequest(importJobId));
    }

    @McpTool(
            name = "mark_import_job_succeeded",
            description = "将导入任务标记为成功。generatedQuestionCount 可为空，为空时按数据库实际数量统计。"
    )
    public CodexToolOkResponse markImportJobSucceeded(
            @McpToolParam(description = "导入任务 ID") Long importJobId,
            @McpToolParam(description = "生成题目数量，可为空", required = false) Integer generatedQuestionCount,
            @McpToolParam(description = "成功摘要，可为空", required = false) String summary
    ) {
        return codexToolService.markImportJobSucceeded(new MarkImportJobSucceededToolRequest(
                importJobId,
                generatedQuestionCount,
                summary
        ));
    }

    @McpTool(
            name = "mark_import_job_failed",
            description = "将导入任务标记为失败。必须提供失败原因。"
    )
    public CodexToolOkResponse markImportJobFailed(
            @McpToolParam(description = "导入任务 ID") Long importJobId,
            @McpToolParam(description = "失败原因，最长 1000 字") String reason
    ) {
        return codexToolService.markImportJobFailed(new MarkImportJobFailedToolRequest(importJobId, reason));
    }
}
