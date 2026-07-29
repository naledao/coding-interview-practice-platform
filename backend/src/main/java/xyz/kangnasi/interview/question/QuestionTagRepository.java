package xyz.kangnasi.interview.question;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionTagRepository extends JpaRepository<QuestionTag, Long> {

    Optional<QuestionTag> findByNormalizedName(String normalizedName);

    List<QuestionTag> findAllByOrderByQuestionCountDescNameAsc();
}
