package xyz.kangnasi.interview.aitutor;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.stereotype.Service;
import xyz.kangnasi.interview.auth.UserPrincipal;
import xyz.kangnasi.interview.common.AppException;

@Service
public class AiTutorService {

    private static final int MAX_INPUT_BYTES = 64 * 1024;
    private static final int MAX_MODEL_BYTES = 128;
    private static final int MAX_EFFORT_BYTES = 16;

    private final CodexChatGateway gateway;
    private final AiTutorConversationOwnershipService ownershipService;
    private final AiTutorQuestionContextService questionContextService;

    public AiTutorService(
            CodexChatGateway gateway,
            AiTutorConversationOwnershipService ownershipService,
            AiTutorQuestionContextService questionContextService
    ) {
        this.gateway = gateway;
        this.ownershipService = ownershipService;
        this.questionContextService = questionContextService;
    }

    public AiTutorModelCatalog listModels() {
        AiTutorModelCatalog catalog = gateway.listModels();
        if (catalog == null || catalog.defaultModel() == null
                || catalog.models() == null || catalog.models().isEmpty()) {
            throw AppException.serviceUnavailable("AI 助教模型目录暂时不可用");
        }
        return catalog;
    }

    public AiTutorRunAccepted createRun(UserPrincipal principal, AiTutorRunRequest request) {
        Long userId = requireUserId(principal);
        ValidatedRun validated = validate(request);
        String conversationId = validated.conversationId();
        if (conversationId != null) {
            conversationId = ownershipService.requireOwned(userId, conversationId);
        }

        CodexRunCreateRequest upstreamRequest = new CodexRunCreateRequest(
                validated.clientRequestId(),
                conversationId,
                new CodexRunInput("text", validated.input(), List.of()),
                questionContextService.build(validated.questionId()),
                new CodexRunOptions(validated.model(), validated.reasoningEffort())
        );
        AiTutorRunAccepted accepted = gateway.createRun(upstreamRequest);
        if (accepted == null || accepted.conversationId() == null || accepted.runId() == null) {
            throw AppException.serviceUnavailable("AI 助教服务返回了无效结果");
        }
        ownershipService.bind(userId, accepted.conversationId());
        return accepted;
    }

    public AiTutorRunCancelResult cancelRun(UserPrincipal principal, String runId) {
        String normalizedRunId = requireOwnedRun(principal, runId);
        return gateway.cancelRun(normalizedRunId);
    }

    public String requireOwnedRun(UserPrincipal principal, String runId) {
        Long userId = requireUserId(principal);
        String normalizedRunId = AiTutorConversationOwnershipService.normalizeUuid(runId, "runId");
        CodexRunReference run = gateway.getRun(normalizedRunId);
        if (run == null || run.conversationId() == null) {
            throw AppException.notFound("AI 助教轮次不存在");
        }
        ownershipService.requireOwned(userId, run.conversationId());
        return normalizedRunId;
    }

    private ValidatedRun validate(AiTutorRunRequest request) {
        if (request == null) {
            throw AppException.badRequest("请求参数错误");
        }
        String clientRequestId = AiTutorConversationOwnershipService.normalizeUuid(
                request.clientRequestId(),
                "clientRequestId"
        );
        String conversationId = request.conversationId() == null || request.conversationId().isBlank()
                ? null
                : AiTutorConversationOwnershipService.normalizeUuid(request.conversationId(), "conversationId");
        if (request.questionId() == null || request.questionId() <= 0) {
            throw AppException.badRequest("questionId 参数错误");
        }
        String input = requireText(request.input(), "input", MAX_INPUT_BYTES);
        String model = requireOption(request.model(), "model", MAX_MODEL_BYTES);
        String effort = requireOption(request.reasoningEffort(), "reasoningEffort", MAX_EFFORT_BYTES);
        return new ValidatedRun(
                clientRequestId,
                conversationId,
                request.questionId(),
                input,
                model,
                effort
        );
    }

    private String requireText(String value, String field, int maxBytes) {
        if (value == null || value.isBlank()) {
            throw AppException.badRequest(field + " 不能为空");
        }
        if (utf8Length(value) > maxBytes || value.indexOf('\0') >= 0) {
            throw AppException.badRequest(field + " 格式无效或内容过长");
        }
        return value;
    }

    private String requireOption(String value, String field, int maxBytes) {
        if (value == null || value.isBlank() || !value.equals(value.trim())
                || utf8Length(value) > maxBytes || value.codePoints().anyMatch(Character::isISOControl)) {
            throw AppException.badRequest(field + " 参数错误");
        }
        return value;
    }

    private int utf8Length(String value) {
        return value.getBytes(StandardCharsets.UTF_8).length;
    }

    private Long requireUserId(UserPrincipal principal) {
        if (principal == null || principal.id() == null || principal.id() <= 0) {
            throw AppException.unauthorized("未登录或登录已过期");
        }
        return principal.id();
    }

    private record ValidatedRun(
            String clientRequestId,
            String conversationId,
            Long questionId,
            String input,
            String model,
            String reasoningEffort
    ) {
    }
}
