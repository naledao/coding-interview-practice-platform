package xyz.kangnasi.interview.document;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.mockito.ArgumentCaptor;

@SpringBootTest
@AutoConfigureMockMvc
class AdminDocumentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DocumentParseTaskService documentParseTaskService;

    @MockBean
    private DocumentParseTaskPublisher documentParseTaskPublisher;

    @BeforeEach
    void resetMocks() {
        reset(documentParseTaskPublisher);
    }

    @Test
    void adminCanUploadMarkdownAndReadDocumentDetail() throws Exception {
        String token = adminToken();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "jvm.md",
                "text/markdown",
                "# JVM\n\nGC Roots".getBytes(StandardCharsets.UTF_8)
        );

        MvcResult result = mockMvc.perform(multipart("/api/admin/documents")
                        .file(file)
                        .param("autoStart", "true")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.uploadType").value("MARKDOWN"))
                .andExpect(jsonPath("$.data.parseStatus").value("QUEUED"))
                .andExpect(jsonPath("$.data.parseTaskStatus").value("PENDING"))
                .andExpect(jsonPath("$.data.documents", hasSize(0)))
                .andReturn();

        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        long uploadId = data.path("uploadId").asLong();
        long parseTaskId = data.path("parseTaskId").asLong();
        assertParseTaskQueued(parseTaskId, uploadId);

        mockMvc.perform(get("/api/admin/document-uploads/{uploadId}", uploadId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.parseStatus").value("QUEUED"))
                .andExpect(jsonPath("$.data.documents", hasSize(0)));

        documentParseTaskService.parse(parseTaskId);

        MvcResult uploadDetail = mockMvc.perform(get("/api/admin/document-uploads/{uploadId}", uploadId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.parseStatus").value("PARSED"))
                .andExpect(jsonPath("$.data.parseTaskStatus").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.documents", hasSize(1)))
                .andReturn();

        JsonNode documentNode = objectMapper.readTree(uploadDetail.getResponse().getContentAsString())
                .at("/data/documents/0");
        long documentId = documentNode.path("documentId").asLong();
        long jobId = documentNode.at("/latestJob/id").asLong();

        mockMvc.perform(get("/api/admin/documents/{documentId}", documentId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.originalFilename").value("jvm.md"))
                .andExpect(jsonPath("$.data.sourceType").value("MARKDOWN"))
                .andExpect(jsonPath("$.data.content").value("# JVM\n\nGC Roots"))
                .andExpect(jsonPath("$.data.latestJob.id").value(jobId));
    }

    @Test
    void adminCanUploadZipAndSeeArchiveEntries() throws Exception {
        String token = adminToken();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "java-notes.zip",
                "application/zip",
                zipBytes()
        );

        MvcResult result = mockMvc.perform(multipart("/api/admin/documents")
                        .file(file)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.uploadType").value("ZIP"))
                .andExpect(jsonPath("$.data.parseStatus").value("QUEUED"))
                .andExpect(jsonPath("$.data.documentCount").value(0))
                .andExpect(jsonPath("$.data.documents", hasSize(0)))
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        long uploadId = response.path("uploadId").asLong();
        long parseTaskId = response.path("parseTaskId").asLong();
        assertParseTaskQueued(parseTaskId, uploadId);

        documentParseTaskService.parse(parseTaskId);

        mockMvc.perform(get("/api/admin/document-uploads/{uploadId}", uploadId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.originalFilename").value("java-notes.zip"))
                .andExpect(jsonPath("$.data.parseStatus").value("PARSED"))
                .andExpect(jsonPath("$.data.documentCount").value(2))
                .andExpect(jsonPath("$.data.ignoredFileCount").value(1))
                .andExpect(jsonPath("$.data.skippedFileCount").value(1))
                .andExpect(jsonPath("$.data.documents", hasSize(2)))
                .andExpect(jsonPath("$.data.documents[0].archiveEntryPath").value("jvm/gc.md"))
                .andExpect(jsonPath("$.data.skippedFiles[0].reason").value("EMPTY_MARKDOWN"));
    }

    @Test
    void zipWithoutMarkdownFailsParseTaskAfterUploadIsQueued() throws Exception {
        String token = adminToken();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "empty.zip",
                "application/zip",
                zipWithTextOnly()
        );

        MvcResult result = mockMvc.perform(multipart("/api/admin/documents")
                        .file(file)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.parseStatus").value("QUEUED"))
                .andReturn();

        long uploadId = objectMapper.readTree(result.getResponse().getContentAsString())
                .at("/data/uploadId")
                .asLong();
        long parseTaskId = objectMapper.readTree(result.getResponse().getContentAsString())
                .at("/data/parseTaskId")
                .asLong();

        documentParseTaskService.parse(parseTaskId);

        mockMvc.perform(get("/api/admin/document-uploads/{uploadId}", uploadId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.parseStatus").value("FAILED"))
                .andExpect(jsonPath("$.data.parseTaskStatus").value("FAILED"))
                .andExpect(jsonPath("$.data.parseFailedReason").value("压缩包内未找到 Markdown 文件"));
    }

    @Test
    void failedZipParseDoesNotPersistPartialDocuments() throws Exception {
        String token = adminToken();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "too-many.zip",
                "application/zip",
                zipThatExceedsEntryLimitAfterValidMarkdown()
        );

        MvcResult result = mockMvc.perform(multipart("/api/admin/documents")
                        .file(file)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.parseStatus").value("QUEUED"))
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        long uploadId = response.path("uploadId").asLong();
        long parseTaskId = response.path("parseTaskId").asLong();

        documentParseTaskService.parse(parseTaskId);

        mockMvc.perform(get("/api/admin/document-uploads/{uploadId}", uploadId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.parseStatus").value("FAILED"))
                .andExpect(jsonPath("$.data.parseTaskStatus").value("FAILED"))
                .andExpect(jsonPath("$.data.parseFailedReason").value("压缩包内文件数量超过限制"))
                .andExpect(jsonPath("$.data.documentCount").value(0))
                .andExpect(jsonPath("$.data.documents", hasSize(0)));
    }

    @Test
    void nonMarkdownOrZipIsRejected() throws Exception {
        String token = adminToken();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "notes.txt",
                "text/plain",
                "hello".getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/api/admin/documents")
                        .file(file)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("仅支持 Markdown 文件或 ZIP 压缩包"));
    }

    private byte[] zipBytes() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            writeZipEntry(zip, "jvm/gc.md", "# GC\n\nGC Roots");
            writeZipEntry(zip, "spring/ioc.md", "# IoC\n\nBean lifecycle");
            writeZipEntry(zip, "readme.txt", "ignored");
            writeZipEntry(zip, "empty.md", "   ");
        }
        return bytes.toByteArray();
    }

    private byte[] zipWithTextOnly() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            writeZipEntry(zip, "readme.txt", "ignored");
        }
        return bytes.toByteArray();
    }

    private byte[] zipThatExceedsEntryLimitAfterValidMarkdown() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            writeZipEntry(zip, "valid.md", "# Valid");
            for (int index = 0; index < 200; index++) {
                writeZipEntry(zip, "ignored-" + index + ".txt", "ignored");
            }
        }
        return bytes.toByteArray();
    }

    private void writeZipEntry(ZipOutputStream zip, String name, String content) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
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

    private void assertParseTaskQueued(long parseTaskId, long uploadId) {
        ArgumentCaptor<DocumentParseMessage> captor = ArgumentCaptor.forClass(DocumentParseMessage.class);
        verify(documentParseTaskPublisher).publish(captor.capture());
        assertEquals(parseTaskId, captor.getValue().parseTaskId());
        assertEquals(uploadId, captor.getValue().uploadId());
    }
}
