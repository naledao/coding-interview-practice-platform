package xyz.kangnasi.interview.question;

import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import xyz.kangnasi.interview.practice.PracticeAnswerRecordRepository;
import xyz.kangnasi.interview.practice.WrongQuestionRecordRepository;

@SpringBootTest
@AutoConfigureMockMvc
class QuestionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private QuestionRepository questionRepository;

    @Autowired
    private QuestionTagRepository tagRepository;

    @Autowired
    private PracticeAnswerRecordRepository answerRecordRepository;

    @Autowired
    private WrongQuestionRecordRepository wrongQuestionRecordRepository;

    @Test
    void userQuestionListCanFilterByTagAndDifficultyWithoutLeakingAnswer() throws Exception {
        String token = userToken();
        String suffix = String.valueOf(System.nanoTime());
        QuestionTag jvm = createTag("JVM测试" + suffix, "jvm-test-" + suffix, TagCategory.JAVA);
        QuestionTag spring = createTag("Spring测试" + suffix, "spring-test-" + suffix, TagCategory.FRAMEWORK);
        Question jvmQuestion = createQuestion(
                "关于 JVM 运行时数据区的说法，哪一项是正确的？",
                QuestionDifficulty.MEDIUM,
                List.of(jvm)
        );
        createQuestion(
                "关于 Spring Bean 生命周期的说法，哪一项是正确的？",
                QuestionDifficulty.EASY,
                List.of(spring)
        );

        mockMvc.perform(get("/api/questions")
                        .param("page", "1")
                        .param("pageSize", "20")
                        .param("tagId", String.valueOf(jvm.getId()))
                        .param("difficulty", "MEDIUM")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].id").value(jvmQuestion.getId()))
                .andExpect(jsonPath("$.data.items[0].tags[0].name").value(jvm.getName()));

        mockMvc.perform(get("/api/questions/{questionId}", jvmQuestion.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.answerAnalysis").doesNotExist())
                .andExpect(jsonPath("$.data.codexReviewSummary").doesNotExist())
                .andExpect(jsonPath("$.data.options[0].correct").doesNotExist())
                .andExpect(jsonPath("$.data.favorite").value(false))
                .andExpect(jsonPath("$.data.answered").value(false));
    }

    @Test
    void userCanSubmitAnswerAndThenSeeAnalysis() throws Exception {
        String token = userToken();
        Question question = createQuestion(
                "关于 volatile 的说法，哪一项是正确的？",
                QuestionDifficulty.MEDIUM,
                List.of(createTag("Java并发", "java并发", TagCategory.JAVA))
        );

        mockMvc.perform(post("/api/questions/{questionId}/answer", question.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("selectedOptionKey", "B"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.questionId").value(question.getId()))
                .andExpect(jsonPath("$.data.selectedOptionKey").value("B"))
                .andExpect(jsonPath("$.data.correct").value(true))
                .andExpect(jsonPath("$.data.correctOptionKey").value("B"))
                .andExpect(jsonPath("$.data.answerAnalysis").value("B 是正确答案。"))
                .andExpect(jsonPath("$.data.wrongBookUpdated").value(false))
                .andExpect(jsonPath("$.data.recordId").isNumber());

        org.assertj.core.api.Assertions.assertThat(answerRecordRepository.count()).isPositive();

        mockMvc.perform(get("/api/questions/{questionId}/analysis", question.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.answerAnalysis").value("B 是正确答案。"))
                .andExpect(jsonPath("$.data.options[1].correct").value(true))
                .andExpect(jsonPath("$.data.answered").value(true));
    }

    @Test
    void disabledQuestionIsHiddenFromUsersButVisibleToAdmin() throws Exception {
        String userToken = userToken();
        String adminToken = adminToken();
        Question question = createQuestion(
                "关于 HashMap 的说法，哪一项是正确的？",
                QuestionDifficulty.EASY,
                List.of(createTag("Java集合", "java集合", TagCategory.JAVA))
        );

        mockMvc.perform(post("/api/admin/questions/{questionId}/disable", question.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DISABLED"));

        mockMvc.perform(get("/api/questions/{questionId}", question.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(userToken)))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/admin/questions/{questionId}", question.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DISABLED"))
                .andExpect(jsonPath("$.data.answerAnalysis").value("B 是正确答案。"));
    }

    @Test
    void userCanFetchNextQuestionWithoutAnswerAndExcludeAnsweredQuestions() throws Exception {
        String token = userToken();
        String suffix = String.valueOf(System.nanoTime());
        QuestionTag tag = createTag("顺序刷题" + suffix, "practice-seq-" + suffix, TagCategory.JAVA);
        Question first = createQuestion("第一道顺序刷题题目？", QuestionDifficulty.EASY, List.of(tag));
        Question second = createQuestion("第二道顺序刷题题目？", QuestionDifficulty.EASY, List.of(tag));

        mockMvc.perform(get("/api/practice/next")
                        .param("mode", "SEQUENTIAL")
                        .param("tagId", String.valueOf(tag.getId()))
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(first.getId()))
                .andExpect(jsonPath("$.data.answerAnalysis").doesNotExist())
                .andExpect(jsonPath("$.data.options[1].correct").doesNotExist())
                .andExpect(jsonPath("$.data.answered").value(false));

        mockMvc.perform(post("/api/questions/{questionId}/answer", first.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "selectedOptionKey", "B",
                                "mode", "SEQUENTIAL",
                                "timeSpentSeconds", 12
                        ))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/practice/next")
                        .param("mode", "SEQUENTIAL")
                        .param("tagId", String.valueOf(tag.getId()))
                        .param("excludeAnswered", "true")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(second.getId()));

        mockMvc.perform(get("/api/practice/count")
                        .param("mode", "SEQUENTIAL")
                        .param("tagId", String.valueOf(tag.getId()))
                        .param("excludeAnswered", "true")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    void wrongPracticeCanRespectExcludeAnswered() throws Exception {
        String token = tokenFor("wrong-practice-exclude-" + System.nanoTime() + "@example.com");
        Question question = createQuestion(
                "关于错题跳过已做的说法，哪一项是正确的？",
                QuestionDifficulty.MEDIUM,
                List.of(createTag("错题跳过已做测试", "wrong-exclude-" + System.nanoTime(), TagCategory.JAVA))
        );

        submitAnswer(token, question.getId(), "A", "WRONG");

        mockMvc.perform(get("/api/practice/next")
                        .param("mode", "WRONG")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(question.getId()));

        mockMvc.perform(get("/api/practice/next")
                        .param("mode", "WRONG")
                        .param("excludeAnswered", "true")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").doesNotExist());

        mockMvc.perform(get("/api/practice/count")
                        .param("mode", "WRONG")
                        .param("excludeAnswered", "true")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    void wrongAnswerCreatesOrUpdatesWrongBookRecord() throws Exception {
        String token = userToken();
        Question question = createQuestion(
                "关于 synchronized 的说法，哪一项是正确的？",
                QuestionDifficulty.MEDIUM,
                List.of(createTag("锁测试", "lock-test-" + System.nanoTime(), TagCategory.JAVA))
        );

        mockMvc.perform(post("/api/questions/{questionId}/answer", question.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "selectedOptionKey", "A",
                                "mode", "RANDOM",
                                "timeSpentSeconds", 8
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.correct").value(false))
                .andExpect(jsonPath("$.data.wrongBookUpdated").value(true))
                .andExpect(jsonPath("$.data.recordId").isNumber());

        mockMvc.perform(post("/api/questions/{questionId}/answer", question.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "selectedOptionKey", "A",
                                "mode", "RANDOM"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.wrongBookUpdated").value(true));

        org.assertj.core.api.Assertions.assertThat(wrongQuestionRecordRepository.findAll())
                .filteredOn(record -> record.getQuestion().getId().equals(question.getId()))
                .singleElement()
                .satisfies(record -> org.assertj.core.api.Assertions.assertThat(record.getWrongCount()).isEqualTo(2));
    }

    @Test
    void userCanListMasterAndRemoveWrongQuestion() throws Exception {
        String token = tokenFor("wrong-book-" + System.nanoTime() + "@example.com");
        Question question = createQuestion(
                "关于 wait notify 的说法，哪一项是正确的？",
                QuestionDifficulty.HARD,
                List.of(createTag("错题列表测试", "wrong-list-test-" + System.nanoTime(), TagCategory.JAVA))
        );

        submitAnswer(token, question.getId(), "A", "RANDOM");

        mockMvc.perform(get("/api/wrong-questions")
                        .param("mastered", "false")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].questionId").value(question.getId()))
                .andExpect(jsonPath("$.data.items[0].wrongCount").value(1))
                .andExpect(jsonPath("$.data.items[0].mastered").value(false));

        mockMvc.perform(post("/api/wrong-questions/{questionId}/master", question.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mastered").value(true));

        mockMvc.perform(get("/api/wrong-questions")
                        .param("mastered", "false")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(0)));

        mockMvc.perform(get("/api/wrong-questions")
                        .param("mastered", "true")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].questionId").value(question.getId()));

        mockMvc.perform(post("/api/wrong-questions/{questionId}/unmaster", question.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mastered").value(false));

        mockMvc.perform(delete("/api/wrong-questions/{questionId}", question.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/wrong-questions")
                        .param("mastered", "")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(0)));
    }

    @Test
    void userCanFavoriteQuestionIdempotentlyAndPracticeFavorites() throws Exception {
        String token = tokenFor("favorite-" + System.nanoTime() + "@example.com");
        Question question = createQuestion(
                "关于 ArrayList 扩容的说法，哪一项是正确的？",
                QuestionDifficulty.EASY,
                List.of(createTag("收藏测试", "favorite-test-" + System.nanoTime(), TagCategory.JAVA))
        );

        mockMvc.perform(post("/api/favorites/{questionId}", question.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.questionId").value(question.getId()));

        mockMvc.perform(post("/api/favorites/{questionId}", question.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.questionId").value(question.getId()));

        mockMvc.perform(get("/api/favorites")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].questionId").value(question.getId()));

        mockMvc.perform(get("/api/practice/next")
                        .param("mode", "FAVORITE")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(question.getId()))
                .andExpect(jsonPath("$.data.favorite").value(true));

        mockMvc.perform(delete("/api/favorites/{questionId}", question.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/favorites/{questionId}", question.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/favorites")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(0)));
    }

    @Test
    void userCanListAnsweredQuestionsAggregatedByQuestion() throws Exception {
        String token = tokenFor("answered-list-" + System.nanoTime() + "@example.com");
        Question question = createQuestion(
                "关于已做题目列表的说法，哪一项是正确的？",
                QuestionDifficulty.MEDIUM,
                List.of(createTag("已做列表测试", "answered-list-" + System.nanoTime(), TagCategory.JAVA))
        );

        submitAnswer(token, question.getId(), "A", "RANDOM");
        submitAnswer(token, question.getId(), "B", "RANDOM");

        mockMvc.perform(post("/api/favorites/{questionId}", question.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/practice/answered-questions")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].questionId").value(question.getId()))
                .andExpect(jsonPath("$.data.items[0].answerCount").value(2))
                .andExpect(jsonPath("$.data.items[0].correctCount").value(1))
                .andExpect(jsonPath("$.data.items[0].wrongCount").value(1))
                .andExpect(jsonPath("$.data.items[0].lastCorrect").value(true))
                .andExpect(jsonPath("$.data.items[0].lastSelectedOptionKey").value("B"))
                .andExpect(jsonPath("$.data.items[0].favorite").value(true));
    }

    private Question createQuestion(String stem, QuestionDifficulty difficulty, List<QuestionTag> tags) {
        long suffix = System.nanoTime();
        Question question = Question.create(
                stem + " " + suffix,
                "hash-" + suffix,
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

    private QuestionTag createTag(String name, String normalizedName, TagCategory category) {
        return tagRepository.findByNormalizedName(normalizedName)
                .orElseGet(() -> tagRepository.save(QuestionTag.create(name, normalizedName, category)));
    }

    private String userToken() throws Exception {
        sendLoginCode("user1@example.com");
        return login("user1@example.com", "123456");
    }

    private String adminToken() throws Exception {
        sendLoginCode("admin@example.com");
        return login("admin@example.com", "123456");
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

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private void submitAnswer(String token, Long questionId, String selectedOptionKey, String mode) throws Exception {
        mockMvc.perform(post("/api/questions/{questionId}/answer", questionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "selectedOptionKey", selectedOptionKey,
                                "mode", mode
                        ))))
                .andExpect(status().isOk());
    }
}
