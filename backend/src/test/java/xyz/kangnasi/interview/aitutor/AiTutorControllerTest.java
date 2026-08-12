package xyz.kangnasi.interview.aitutor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Request;
import feign.Response;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import xyz.kangnasi.interview.auth.JwtService;
import xyz.kangnasi.interview.question.Question;
import xyz.kangnasi.interview.question.QuestionDifficulty;
import xyz.kangnasi.interview.question.QuestionOption;
import xyz.kangnasi.interview.question.QuestionRepository;
import xyz.kangnasi.interview.user.AppUser;
import xyz.kangnasi.interview.user.UserRepository;
import xyz.kangnasi.interview.user.UserRole;

@SpringBootTest
@AutoConfigureMockMvc
class AiTutorControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private QuestionRepository questionRepository;

    @Autowired
    private AiTutorConversationBindingRepository bindingRepository;

    @MockBean
    private CodexChatGateway gateway;

    @Test
    void modelCatalogRequiresLoginAndIsReturnedThroughBackend() throws Exception {
        when(gateway.listModels()).thenReturn(catalog());

        mockMvc.perform(get("/api/ai-tutor/models"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/ai-tutor/models")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenFor("models"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.defaultModel").value("gpt-test"))
                .andExpect(jsonPath("$.data.models[0].supportedReasoningEfforts[1]").value("high"));
    }

    @Test
    void createsRunWithTrustedQuestionContextAndBindsConversation() throws Exception {
        AppUser user = createUser("create");
        Question question = createQuestion();
        String clientRequestId = UUID.randomUUID().toString();
        String conversationId = UUID.randomUUID().toString();
        String runId = UUID.randomUUID().toString();
        when(gateway.createRun(any())).thenReturn(new AiTutorRunAccepted(
                conversationId,
                runId,
                clientRequestId,
                1,
                "running",
                false
        ));

        mockMvc.perform(post("/api/ai-tutor/runs")
                        .header(HttpHeaders.AUTHORIZATION, bearer(jwtService.generateToken(user)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "clientRequestId", clientRequestId,
                                "questionId", question.getId(),
                                "input", "请解释这道题。\n重点说明原因。",
                                "model", "gpt-test",
                                "reasoningEffort", "high"
                        ))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.conversationId").value(conversationId))
                .andExpect(jsonPath("$.data.runId").value(runId));

        ArgumentCaptor<CodexRunCreateRequest> requestCaptor = ArgumentCaptor.forClass(CodexRunCreateRequest.class);
        verify(gateway).createRun(requestCaptor.capture());
        CodexRunCreateRequest upstream = requestCaptor.getValue();
        assertThat(upstream.input().text()).isEqualTo("请解释这道题。\n重点说明原因。");
        assertThat(upstream.context().content())
                .contains(question.getStem(), "选项甲", "选项乙")
                .doesNotContain("答案解析不得进入上下文");
        assertThat(upstream.context().metadata()).containsEntry("questionId", question.getId());
        assertThat(upstream.options()).isEqualTo(new CodexRunOptions("gpt-test", "high"));
        assertThat(bindingRepository.findByConversationId(conversationId))
                .get()
                .extracting(AiTutorConversationBinding::getUserId)
                .isEqualTo(user.getId());
    }

    @Test
    void rejectsConversationOwnedByAnotherUserBeforeCallingChatService() throws Exception {
        AppUser owner = createUser("owner");
        AppUser attacker = createUser("attacker");
        Question question = createQuestion();
        String conversationId = UUID.randomUUID().toString();
        bindingRepository.save(AiTutorConversationBinding.create(owner.getId(), conversationId));

        mockMvc.perform(post("/api/ai-tutor/runs")
                        .header(HttpHeaders.AUTHORIZATION, bearer(jwtService.generateToken(attacker)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "clientRequestId", UUID.randomUUID().toString(),
                                "conversationId", conversationId,
                                "questionId", question.getId(),
                                "input", "继续追问",
                                "model", "gpt-test",
                                "reasoningEffort", "medium"
                        ))))
                .andExpect(status().isNotFound());

        verify(gateway, never()).createRun(any());
    }

    @Test
    void streamsOwnedRunAsNdjsonAndSupportsCancellation() throws Exception {
        AppUser user = createUser("stream");
        String conversationId = UUID.randomUUID().toString();
        String runId = UUID.randomUUID().toString();
        bindingRepository.save(AiTutorConversationBinding.create(user.getId(), conversationId));
        when(gateway.getRun(runId)).thenReturn(new CodexRunReference(conversationId));
        when(gateway.cancelRun(runId)).thenReturn(new AiTutorRunCancelResult(runId, "cancelling"));

        String ndjson = "{\"eventId\":1,\"type\":\"item.delta\",\"payload\":{\"delta\":\"你好\"}}\n"
                + "{\"eventId\":2,\"type\":\"run.completed\",\"payload\":{}}\n";
        Response stream = Response.builder()
                .status(200)
                .reason("OK")
                .request(Request.create(
                        Request.HttpMethod.GET,
                        "http://codex-chat-service/api/chat/runs/" + runId + "/events/stream",
                        Map.of(),
                        null,
                        StandardCharsets.UTF_8,
                        null
                ))
                .body(ndjson, StandardCharsets.UTF_8)
                .build();
        when(gateway.streamEvents(runId, 0)).thenReturn(stream);
        String token = jwtService.generateToken(user);

        MvcResult initial = mockMvc.perform(get("/api/ai-tutor/runs/{runId}/events/stream", runId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(request().asyncStarted())
                .andReturn();
        mockMvc.perform(asyncDispatch(initial))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/x-ndjson"))
                .andExpect(content().string(ndjson));

        mockMvc.perform(post("/api/ai-tutor/runs/{runId}/cancel", runId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("cancelling"));
    }

    private AiTutorModelCatalog catalog() {
        return new AiTutorModelCatalog(
                "gpt-test",
                List.of(new AiTutorModelOption(
                        "gpt-test",
                        "GPT Test",
                        "medium",
                        List.of("medium", "high")
                ))
        );
    }

    private AppUser createUser(String prefix) {
        return userRepository.save(AppUser.create(
                prefix + "-" + System.nanoTime() + "@example.com",
                "AI 助教测试用户",
                UserRole.USER
        ));
    }

    private String tokenFor(String prefix) {
        return jwtService.generateToken(createUser(prefix));
    }

    private Question createQuestion() {
        long suffix = System.nanoTime();
        Question question = Question.create(
                "AI 助教上下文测试题 " + suffix,
                "ai-tutor-hash-" + suffix,
                QuestionDifficulty.MEDIUM,
                "测试知识点",
                "答案解析不得进入上下文",
                "已完成审核",
                suffix,
                suffix
        );
        question.addOption(QuestionOption.create("A", "选项甲", false));
        question.addOption(QuestionOption.create("B", "选项乙", true));
        return questionRepository.save(question);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
