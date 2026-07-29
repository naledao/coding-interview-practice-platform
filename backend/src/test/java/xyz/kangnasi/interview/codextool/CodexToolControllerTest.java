package xyz.kangnasi.interview.codextool;

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
class CodexToolControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DocumentParseTaskService documentParseTaskService;

    @MockBean
    private DocumentParseTaskPublisher documentParseTaskPublisher;

    @Test
    void codexToolCanReadDocumentAndCreateQuestions() throws Exception {
        long importJobId = createParsedImportJob("# JVM\n\nvolatile 和内存语义");

        postTool("/api/codex-tools/get_import_job", Map.of("importJobId", importJobId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.importJobId").value(importJobId))
                .andExpect(jsonPath("$.data.status").value("PENDING"));

        postTool("/api/codex-tools/mark_import_job_running", Map.of("importJobId", importJobId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ok").value(true));

        postTool("/api/codex-tools/read_import_document", Map.of("importJobId", importJobId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").value("# JVM\n\nvolatile 和内存语义"));

        postTool("/api/codex-tools/create_question_batch", Map.of(
                        "importJobId", importJobId,
                        "questions", List.of(validQuestion("关于 Java volatile 的说法，哪一项是正确的？"))
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ok").value(true))
                .andExpect(jsonPath("$.data.createdCount").value(1))
                .andExpect(jsonPath("$.data.createdQuestionIds", hasSize(1)));

        postTool("/api/codex-tools/get_generated_question_count", Map.of("importJobId", importJobId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.importJobId").value(importJobId))
                .andExpect(jsonPath("$.data.generatedQuestionCount").value(1));

        postTool("/api/codex-tools/mark_import_job_succeeded", Map.of(
                        "importJobId", importJobId,
                        "generatedQuestionCount", 1,
                        "summary", "生成 1 道题"
                ))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/import-jobs/{jobId}", importJobId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.generatedQuestionCount").value(1));

        mockMvc.perform(get("/api/admin/import-jobs/{jobId}/questions", importJobId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].stem").value("关于 Java volatile 的说法，哪一项是正确的？"));

        postTool("/api/codex-tools/mark_import_job_running", Map.of("importJobId", importJobId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("导入任务当前状态不能标记为运行中"));
    }

    @Test
    void invalidQuestionIsReportedAndNotWritten() throws Exception {
        long importJobId = createParsedImportJob("# Java 集合\n\nHashMap");

        postTool("/api/codex-tools/mark_import_job_running", Map.of("importJobId", importJobId))
                .andExpect(status().isOk());

        Map<String, Object> invalidQuestion = validQuestion("关于 HashMap 的说法，哪一项是正确的？");
        invalidQuestion.put("options", List.of(
                option("A", "HashMap 是线程安全的", true),
                option("B", "HashMap 允许 null key", true),
                option("C", "HashMap 是有序 Map", false),
                option("D", "HashMap 只能保存 String", false)
        ));

        postTool("/api/codex-tools/create_question_batch", Map.of(
                        "importJobId", importJobId,
                        "questions", List.of(invalidQuestion)
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ok").value(false))
                .andExpect(jsonPath("$.data.createdCount").value(0))
                .andExpect(jsonPath("$.data.errors", hasSize(1)));

        mockMvc.perform(get("/api/admin/import-jobs/{jobId}", importJobId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.generatedQuestionCount").value(0));
    }

    @Test
    void validateQuestionBatchDoesNotWriteQuestions() throws Exception {
        long importJobId = createParsedImportJob("# Java 并发\n\nThreadLocal");

        postTool("/api/codex-tools/mark_import_job_running", Map.of("importJobId", importJobId))
                .andExpect(status().isOk());

        postTool("/api/codex-tools/validate_question_batch", Map.of(
                        "importJobId", importJobId,
                        "questions", List.of(validQuestion("关于 ThreadLocal 的说法，哪一项是正确的？"))
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ok").value(true))
                .andExpect(jsonPath("$.data.validCount").value(1))
                .andExpect(jsonPath("$.data.skippedCount").value(0))
                .andExpect(jsonPath("$.data.errors", hasSize(0)));

        postTool("/api/codex-tools/get_generated_question_count", Map.of("importJobId", importJobId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.generatedQuestionCount").value(0));
    }

    @Test
    void recommendedTagsAreExposedToCodex() throws Exception {
        postTool("/api/codex-tools/get_recommended_tags", Map.of())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tags", hasSize(20)))
                .andExpect(jsonPath("$.data.tags[0].name").value("Java基础"))
                .andExpect(jsonPath("$.data.tags[0].category").value("JAVA"))
                .andExpect(jsonPath("$.data.tags[4].name").value("泛型"))
                .andExpect(jsonPath("$.data.tags[12].name").value("Spring"));
    }

    @Test
    void duplicateStemInSameImportJobIsSkipped() throws Exception {
        long importJobId = createParsedImportJob("# Java 并发\n\nsynchronized");

        postTool("/api/codex-tools/mark_import_job_running", Map.of("importJobId", importJobId))
                .andExpect(status().isOk());

        postTool("/api/codex-tools/create_question_batch", Map.of(
                        "importJobId", importJobId,
                        "questions", List.of(
                                validQuestion("关于 synchronized 的说法，哪一项是正确的？"),
                                validQuestion("关于 synchronized 的说法，哪一项是正确的？")
                        )
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.createdCount").value(1))
                .andExpect(jsonPath("$.data.skippedCount").value(1));
    }

    private org.springframework.test.web.servlet.ResultActions postTool(String path, Object body) throws Exception {
        return mockMvc.perform(post(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private long createParsedImportJob(String markdown) throws Exception {
        String token = adminToken();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "java-notes.md",
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

    private Map<String, Object> validQuestion(String stem) {
        return new java.util.LinkedHashMap<>(Map.of(
                "stem", stem,
                "difficulty", "MEDIUM",
                "knowledgePoint", "volatile 内存语义",
                "answerAnalysis", "volatile 能保证变量可见性，并提供相关内存语义，但不能保证复合操作的原子性。",
                "codexReviewSummary", "已检查唯一答案、解析一致性和 Java 版本边界。",
                "tags", List.of("Java并发", "JMM", "volatile"),
                "options", List.of(
                        option("A", "volatile 可以保证 i++ 的原子性", false),
                        option("B", "volatile 可以保证变量可见性，并提供相关内存语义", true),
                        option("C", "volatile 的作用完全等同于 synchronized", false),
                        option("D", "volatile 只能修饰局部变量", false)
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
