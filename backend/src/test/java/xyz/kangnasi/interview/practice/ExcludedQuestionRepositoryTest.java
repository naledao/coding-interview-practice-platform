package xyz.kangnasi.interview.practice;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceUnitUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import xyz.kangnasi.interview.question.Question;
import xyz.kangnasi.interview.question.QuestionDifficulty;
import xyz.kangnasi.interview.question.QuestionRepository;
import xyz.kangnasi.interview.user.AppUser;
import xyz.kangnasi.interview.user.UserRepository;
import xyz.kangnasi.interview.user.UserRole;

@DataJpaTest(showSql = false)
class ExcludedQuestionRepositoryTest {

    @Autowired
    private ExcludedQuestionRepository excludedQuestionRepository;

    @Autowired
    private QuestionRepository questionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void excludedQuestionQueriesFetchToOneAssociationsWithoutRuntimeProxies() {
        String suffix = String.valueOf(System.nanoTime());
        AppUser user = userRepository.save(AppUser.create(
                "excluded-repository-" + suffix + "@example.com",
                "排除题目仓储测试",
                UserRole.USER
        ));
        Question question = questionRepository.save(Question.create(
                "原生镜像不应为排除题目生成懒加载代理",
                "excluded-repository-" + suffix,
                QuestionDifficulty.EASY,
                "Hibernate Native",
                "使用显式抓取查询。",
                "验证关联在查询返回时已经加载。",
                1L,
                1L
        ));
        excludedQuestionRepository.save(ExcludedQuestion.create(user, question));
        entityManager.flush();
        entityManager.clear();

        PersistenceUnitUtil persistenceUnitUtil =
                entityManager.getEntityManagerFactory().getPersistenceUnitUtil();

        Page<ExcludedQuestion> page =
                excludedQuestionRepository.findByUser_Id(user.getId(), PageRequest.of(0, 20));
        assertThat(page.getTotalElements()).isEqualTo(1);
        assertAssociationsLoaded(persistenceUnitUtil, page.getContent().getFirst());

        entityManager.clear();
        ExcludedQuestion single = excludedQuestionRepository
                .findByUser_IdAndQuestion_Id(user.getId(), question.getId())
                .orElseThrow();
        assertAssociationsLoaded(persistenceUnitUtil, single);
    }

    private void assertAssociationsLoaded(
            PersistenceUnitUtil persistenceUnitUtil,
            ExcludedQuestion excludedQuestion
    ) {
        assertThat(persistenceUnitUtil.isLoaded(excludedQuestion.getUser())).isTrue();
        assertThat(persistenceUnitUtil.isLoaded(excludedQuestion.getQuestion())).isTrue();
    }
}
