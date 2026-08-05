package xyz.kangnasi.interview.practice;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExcludedQuestionRepository extends JpaRepository<ExcludedQuestion, Long> {

    @Query("""
            select excluded
            from ExcludedQuestion excluded
            join fetch excluded.user user
            join fetch excluded.question question
            where user.id = :userId
              and question.id = :questionId
            """)
    Optional<ExcludedQuestion> findByUser_IdAndQuestion_Id(
            @Param("userId") Long userId,
            @Param("questionId") Long questionId
    );

    @Query(
            value = """
                    select excluded
                    from ExcludedQuestion excluded
                    join fetch excluded.user user
                    join fetch excluded.question question
                    where user.id = :userId
                    """,
            countQuery = """
                    select count(excluded)
                    from ExcludedQuestion excluded
                    where excluded.user.id = :userId
                    """
    )
    Page<ExcludedQuestion> findByUser_Id(
            @Param("userId") Long userId,
            Pageable pageable
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            delete from ExcludedQuestion excluded
            where excluded.user.id = :userId
              and excluded.question.id = :questionId
            """)
    int deleteExcludedQuestion(
            @Param("userId") Long userId,
            @Param("questionId") Long questionId
    );
}
