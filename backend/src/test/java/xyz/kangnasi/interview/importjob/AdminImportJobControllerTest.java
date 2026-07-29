package xyz.kangnasi.interview.importjob;

import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import xyz.kangnasi.interview.document.DocumentParseTaskPublisher;
import xyz.kangnasi.interview.document.DocumentParseTaskService;

@SpringBootTest
@AutoConfigureMockMvc
class AdminImportJobControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DocumentParseTaskService documentParseTaskService;

    @MockBean
    private DocumentParseTaskPublisher documentParseTaskPublisher;

    @Test
    void adminCanFilterImportJobsByStatusDocumentNameAndCreatedTime() throws Exception {
        String token = adminToken();
        long failedJobId = createParsedImportJob(token, "jvm.md", "# JVM\n\nGC Roots");
        long pendingJobId = createParsedImportJob(token, "collections.md", "# Collections\n\nHashMap");

        postTool("/api/codex-tools/mark_import_job_failed", Map.of(
                        "importJobId", failedJobId,
                        "reason", "Codex 进程异常退出，退出码：1"
                ))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/import-jobs")
                        .queryParam("status", "FAILED")
                        .queryParam("documentName", "jvm")
                        .queryParam("createdFrom", "2000-01-01T00:00:00Z")
                        .queryParam("createdTo", "2999-01-01T00:00:00Z")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].id").value(failedJobId))
                .andExpect(jsonPath("$.data.items[0].documentName").value("jvm.md"))
                .andExpect(jsonPath("$.data.items[0].status").value("FAILED"))
                .andExpect(jsonPath("$.data.items[0].failedReason").value("Codex 进程异常退出，退出码：1"));

        mockMvc.perform(get("/api/admin/import-jobs")
                        .queryParam("documentName", "collections")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].id").value(pendingJobId));
    }

    @Test
    void adminCanInspectLogsQuestionsAndRetryFailedImportJob() throws Exception {
        String token = adminToken();
        long importJobId = createParsedImportJob(token, "thread.md", "# Java 并发\n\nThreadLocal");

        postTool("/api/codex-tools/mark_import_job_running", Map.of("importJobId", importJobId))
                .andExpect(status().isOk());

        postTool("/api/codex-tools/append_generation_log", Map.of(
                        "importJobId", importJobId,
                        "level", "INFO",
                        "message", "提取到 1 个知识点",
                        "payload", Map.of("knowledgePoints", List.of("ThreadLocal"))
                ))
                .andExpect(status().isOk());

        postTool("/api/codex-tools/create_question_batch", Map.of(
                        "importJobId", importJobId,
                        "questions", List.of(validQuestion("关于 ThreadLocal 的说法，哪一项是正确的？"))
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.createdCount").value(1));

        postTool("/api/codex-tools/mark_import_job_failed", Map.of(
                        "importJobId", importJobId,
                        "reason", "联网搜索失败"
                ))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/import-jobs/{jobId}", importJobId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("FAILED"))
                .andExpect(jsonPath("$.data.generatedQuestionCount").value(1))
                .andExpect(jsonPath("$.data.failedReason").value("联网搜索失败"));

        mockMvc.perform(get("/api/admin/import-jobs/{jobId}/logs", importJobId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(6)))
                .andExpect(jsonPath("$.data[3].message").value("提取到 1 个知识点"))
                .andExpect(jsonPath("$.data[3].payload").value("{\"knowledgePoints\":[\"ThreadLocal\"]}"))
                .andExpect(jsonPath("$.data[5].level").value("ERROR"))
                .andExpect(jsonPath("$.data[5].message").value("联网搜索失败"));

        mockMvc.perform(get("/api/admin/import-jobs/{jobId}/questions", importJobId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].stem").value("关于 ThreadLocal 的说法，哪一项是正确的？"));

        MvcResult retryResult = mockMvc.perform(post("/api/admin/import-jobs/{jobId}/retry", importJobId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.newImportJobId").isNumber())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andReturn();

        long retryJobId = objectMapper.readTree(retryResult.getResponse().getContentAsString())
                .at("/data/newImportJobId")
                .asLong();
        mockMvc.perform(get("/api/admin/import-jobs/{jobId}", retryJobId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.documentName").value("thread.md"))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    void retryNonFailedJobReturnsConflict() throws Exception {
        String token = adminToken();
        long importJobId = createParsedImportJob(token, "pending.md", "# Java\n\n泛型");

        mockMvc.perform(post("/api/admin/import-jobs/{jobId}/retry", importJobId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("只有失败任务可以重试"));
    }

    @Test
    void missingImportJobReturnsNotFound() throws Exception {
        String token = adminToken();

        mockMvc.perform(get("/api/admin/import-jobs/{jobId}", 999999)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("导入任务不存在"));
    }

    private long createParsedImportJob(String token, String filename, String markdown) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                filename,
                "text/markdown",
                markdown.getBytes(StandardCharsets.UTF_8)
        );

        MvcResult uploadResult = mockMvc.perform(multipart("/api/admin/documents")
                        .file(file)
                        .param("autoStart", "true")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode upload = objectMapper.readTree(uploadResult.getResponse().getContentAsString()).path("data");
        long uploadId = upload.path("uploadId").asLong();
        long parseTaskId = upload.path("parseTaskId").asLong();
        documentParseTaskService.parse(parseTaskId);

        MvcResult detailResult = mockMvc.perform(get("/api/admin/document-uploads/{uploadId}", uploadId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(detailResult.getResponse().getContentAsString())
                .at("/data/documents/0/latestJob/id")
                .asLong();
    }

    private org.springframework.test.web.servlet.ResultActions postTool(String path, Object body) throws Exception {
        return mockMvc.perform(post(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private Map<String, Object> validQuestion(String stem) {
        return new java.util.LinkedHashMap<>(Map.of(
                "stem", stem,
                "difficulty", "MEDIUM",
                "knowledgePoint", "ThreadLocal",
                "answerAnalysis", "ThreadLocal 为每个线程提供独立变量副本，使用后应注意清理以避免在线程池场景中残留。",
                "codexReviewSummary", "已检查唯一答案、解析一致性和 Java 版本边界。",
                "tags", List.of("Java并发", "ThreadLocal"),
                "options", List.of(
                        option("A", "ThreadLocal 会让所有线程共享同一个变量实例", false),
                        option("B", "ThreadLocal 为每个线程提供独立变量副本", true),
                        option("C", "ThreadLocal 可以替代所有锁机制", false),
                        option("D", "ThreadLocal 只能用于主线程", false)
                )
        ));
    }

    private Map<String, Object> option(String optionKey, String content, boolean correct) {
        return Map.of(
                "optionKey", optionKey,
                "content", content,
                "correct", correct
        );
    }

    private String adminToken() throws Exception {
        sendLoginCode("admin@example.com");
        return login("admin@example.com", "123456");
    }

    private void sendLoginCode(String email) throws Exception {
        mockMvc.perform(post("/api/auth/send-login-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email))))
                .andExpect(status().isOk());
    }

    private String login(String email, String code) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "code", code
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token", not(blankOrNullString())))
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString())
                .at("/data/token")
                .asText();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
