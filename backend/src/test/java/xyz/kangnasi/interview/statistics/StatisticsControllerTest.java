package xyz.kangnasi.interview.statistics;

import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import xyz.kangnasi.interview.question.Question;
import xyz.kangnasi.interview.question.QuestionDifficulty;
import xyz.kangnasi.interview.question.QuestionOption;
import xyz.kangnasi.interview.question.QuestionRepository;
import xyz.kangnasi.interview.question.QuestionTag;
import xyz.kangnasi.interview.question.QuestionTagRepository;
import xyz.kangnasi.interview.question.TagCategory;

@SpringBootTest
@AutoConfigureMockMvc
class StatisticsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private QuestionRepository questionRepository;

    @Autowired
    private QuestionTagRepository tagRepository;

    @Test
    void newUserStatisticsReturnZeroValues() throws Exception {
        String token = tokenFor("statistics-new-" + System.nanoTime() + "@example.com");

        mockMvc.perform(get("/api/statistics/overview")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.answeredTotal").value(0))
                .andExpect(jsonPath("$.data.correctTotal").value(0))
                .andExpect(jsonPath("$.data.wrongTotal").value(0))
                .andExpect(jsonPath("$.data.accuracy").value(0.0))
                .andExpect(jsonPath("$.data.todayAnswered").value(0))
                .andExpect(jsonPath("$.data.wrongBookCount").value(0))
                .andExpect(jsonPath("$.data.favoriteCount").value(0))
                .andExpect(jsonPath("$.data.streakDays").value(0));

        mockMvc.perform(get("/api/statistics/tags")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));

        mockMvc.perform(get("/api/statistics/daily")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(7)))
                .andExpect(jsonPath("$.data[6].date").value(LocalDate.now(ZoneId.systemDefault()).toString()))
                .andExpect(jsonPath("$.data[6].answeredTotal").value(0))
                .andExpect(jsonPath("$.data[6].correctTotal").value(0));
    }

    @Test
    void overviewReflectsAnswersWrongBookAndFavorites() throws Exception {
        String token = tokenFor("statistics-overview-" + System.nanoTime() + "@example.com");
        Question correctQuestion = createQuestion(
                "统计概览正确题？",
                QuestionDifficulty.EASY,
                List.of(createTag("统计概览A" + System.nanoTime(), "statistics-overview-a-" + System.nanoTime()))
        );
        Question wrongQuestion = createQuestion(
                "统计概览错误题？",
                QuestionDifficulty.MEDIUM,
                List.of(createTag("统计概览B" + System.nanoTime(), "statistics-overview-b-" + System.nanoTime()))
        );

        submitAnswer(token, correctQuestion.getId(), "B");
        submitAnswer(token, wrongQuestion.getId(), "A");
        mockMvc.perform(post("/api/favorites/{questionId}", correctQuestion.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/statistics/overview")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.answeredTotal").value(2))
                .andExpect(jsonPath("$.data.correctTotal").value(1))
                .andExpect(jsonPath("$.data.wrongTotal").value(1))
                .andExpect(jsonPath("$.data.accuracy").value(0.5))
                .andExpect(jsonPath("$.data.todayAnswered").value(2))
                .andExpect(jsonPath("$.data.wrongBookCount").value(1))
                .andExpect(jsonPath("$.data.favoriteCount").value(1))
                .andExpect(jsonPath("$.data.streakDays").value(1));
    }

    @Test
    void tagStatisticsCanSortByAnsweredTotalOrWeakAccuracy() throws Exception {
        String token = tokenFor("statistics-tags-" + System.nanoTime() + "@example.com");
        QuestionTag javaTag = createTag("统计 Java " + System.nanoTime(), "statistics-java-" + System.nanoTime());
        QuestionTag jvmTag = createTag("统计 JVM " + System.nanoTime(), "statistics-jvm-" + System.nanoTime());
        Question javaCorrect = createQuestion("Java 标签正确题？", QuestionDifficulty.EASY, List.of(javaTag));
        Question javaWrong = createQuestion("Java 标签错误题？", QuestionDifficulty.EASY, List.of(javaTag));
        Question jvmWrong = createQuestion("JVM 标签错误题？", QuestionDifficulty.EASY, List.of(jvmTag));

        submitAnswer(token, javaCorrect.getId(), "B");
        submitAnswer(token, javaWrong.getId(), "A");
        submitAnswer(token, jvmWrong.getId(), "A");

        mockMvc.perform(get("/api/statistics/tags")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].tagId").value(javaTag.getId()))
                .andExpect(jsonPath("$.data[0].answeredTotal").value(2))
                .andExpect(jsonPath("$.data[0].correctTotal").value(1))
                .andExpect(jsonPath("$.data[0].wrongTotal").value(1))
                .andExpect(jsonPath("$.data[0].accuracy").value(0.5));

        mockMvc.perform(get("/api/statistics/tags")
                        .param("sort", "accuracy_asc")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].tagId").value(jvmTag.getId()))
                .andExpect(jsonPath("$.data[0].accuracy").value(0.0));
    }

    @Test
    void dailyStatisticsDefaultsInvalidDaysToSeven() throws Exception {
        String token = tokenFor("statistics-daily-" + System.nanoTime() + "@example.com");
        Question question = createQuestion(
                "每日统计题？",
                QuestionDifficulty.EASY,
                List.of(createTag("每日统计" + System.nanoTime(), "statistics-daily-" + System.nanoTime()))
        );

        submitAnswer(token, question.getId(), "B");

        mockMvc.perform(get("/api/statistics/daily")
                        .param("days", "0")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(7)))
                .andExpect(jsonPath("$.data[6].date").value(LocalDate.now(ZoneId.systemDefault()).toString()))
                .andExpect(jsonPath("$.data[6].answeredTotal").value(1))
                .andExpect(jsonPath("$.data[6].correctTotal").value(1));
    }

    private Question createQuestion(String stem, QuestionDifficulty difficulty, List<QuestionTag> tags) {
        long suffix = System.nanoTime();
        Question question = Question.create(
                stem + " " + suffix,
                "hash-statistics-" + suffix,
                difficulty,
                "测试知识点",
                "B 是正确答案。",
                "已检查唯一答案。",
                suffix,
                suffix
        );
        question.addOption(QuestionOption.create("A", "错误选项 A", false));
        question.addOption(QuestionOption.create("B", "正确选项 B", true));
        question.addOption(QuestionOption.create("C", "错误选项 C", false));
        question.addOption(QuestionOption.create("D", "错误选项 D", false));
        for (QuestionTag tag : tags) {
            tag.incrementQuestionCount();
            question.addTag(tag);
        }
        return questionRepository.save(question);
    }

    private QuestionTag createTag(String name, String normalizedName) {
        return tagRepository.findByNormalizedName(normalizedName)
                .orElseGet(() -> tagRepository.save(QuestionTag.create(name, normalizedName, TagCategory.JAVA)));
    }

    private String tokenFor(String email) throws Exception {
        sendLoginCode(email);
        return login(email, "123456");
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

    private void submitAnswer(String token, Long questionId, String selectedOptionKey) throws Exception {
        mockMvc.perform(post("/api/questions/{questionId}/answer", questionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "selectedOptionKey", selectedOptionKey,
                                "mode", "RANDOM"
                        ))))
                .andExpect(status().isOk());
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
