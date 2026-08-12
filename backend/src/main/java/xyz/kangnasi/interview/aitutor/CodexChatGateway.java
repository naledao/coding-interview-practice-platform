package xyz.kangnasi.interview.aitutor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import feign.Response;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import xyz.kangnasi.interview.common.AppException;
import xyz.kangnasi.interview.feignclient.CodexChatClient;

@Component
public class CodexChatGateway {

    private final CodexChatClient client;
    private final ObjectMapper objectMapper;

    public CodexChatGateway(CodexChatClient client, ObjectMapper objectMapper) {
        this.client = client;
        this.objectMapper = objectMapper;
    }

    public AiTutorModelCatalog listModels() {
        return call(client::listModels);
    }

    public AiTutorRunAccepted createRun(CodexRunCreateRequest request) {
        return call(() -> client.createRun(request));
    }

    public CodexRunReference getRun(String runId) {
        return call(() -> client.getRun(runId));
    }

    public Response streamEvents(String runId, long afterId) {
        return call(() -> client.streamEvents(runId, afterId));
    }

    public AiTutorRunCancelResult cancelRun(String runId) {
        return call(() -> client.cancelRun(runId));
    }

    private <T> T call(Supplier<T> action) {
        try {
            return action.get();
        } catch (FeignException exception) {
            throw translate(exception);
        }
    }

    private AppException translate(FeignException exception) {
        int status = exception.status();
        String upstreamMessage = upstreamMessage(exception);
        if (status == 400) {
            return AppException.badRequest(upstreamMessage);
        }
        if (status == 404) {
            return AppException.notFound("AI 助教会话或轮次不存在");
        }
        if (status == 409) {
            return AppException.conflict(upstreamMessage);
        }
        return AppException.serviceUnavailable("AI 助教服务暂时不可用，请稍后重试");
    }

    private String upstreamMessage(FeignException exception) {
        String content = exception.contentUTF8();
        if (content != null && !content.isBlank()) {
            try {
                JsonNode payload = objectMapper.readTree(content);
                String message = payload.path("message").asText("").trim();
                if (!message.isEmpty()) {
                    return limit(message);
                }
            } catch (Exception ignored) {
                // Only structured, explicitly returned messages are exposed.
            }
        }
        return exception.status() == 400 ? "AI 助教请求参数错误" : "AI 助教请求冲突";
    }

    private String limit(String message) {
        return message.length() > 400 ? message.substring(0, 400) + "…" : message;
    }
}
