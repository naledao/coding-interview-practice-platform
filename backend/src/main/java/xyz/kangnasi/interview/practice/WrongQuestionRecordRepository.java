package xyz.kangnasi.interview.practice;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import xyz.kangnasi.interview.question.QuestionDifficulty;
import xyz.kangnasi.interview.question.QuestionStatus;

public interface WrongQuestionRecordRepository extends JpaRepository<WrongQuestionRecord, Long> {

    @Query("""
            select wrong
            from WrongQuestionRecord wrong
            join fetch wrong.user user
            join fetch wrong.question question
            where user.id = :userId
              and question.id = :questionId
            """)
    Optional<WrongQuestionRecord> findByUser_IdAndQuestion_Id(
            @Param("userId") Long userId,
            @Param("questionId") Long questionId
    );

    long countByUser_IdAndMasteredFalse(Long userId);

    @Query(
            value = """
                    select wrong
                    from WrongQuestionRecord wrong
                    join fetch wrong.user user
                    join fetch wrong.question q
                    where user.id = :userId
                      and q.status = :status
                      and (:mastered is null or wrong.mastered = :mastered)
                    """,
            countQuery = """
                    select count(wrong)
                    from WrongQuestionRecord wrong
                    join wrong.question q
                    where wrong.user.id = :userId
                      and q.status = :status
                      and (:mastered is null or wrong.mastered = :mastered)
                    """
    )
    Page<WrongQuestionRecord> findWrongQuestionPage(
            @Param("userId") Long userId,
            @Param("status") QuestionStatus status,
            @Param("mastered") Boolean mastered,
            Pageable pageable
    );

    @Query("""
            select count(distinct wrong.question.id)
            from WrongQuestionRecord wrong
            join wrong.question q
            left join q.tags filterTag
            where wrong.user.id = :userId
              and wrong.mastered = false
              and q.status = :status
              and (:difficulty is null or q.difficulty = :difficulty)
              and (:tagId is null or filterTag.id = :tagId)
              and (:keyword is null
                  or q.stem like concat('%', :keyword, '%')
                  or lower(q.knowledgePoint) like lower(concat('%', :keyword, '%')))
              and (:excludeAnswered = false or not exists (
                  select 1
                  from PracticeAnswerRecord record
                  where record.user.id = :userId
                    and record.question.id = q.id
              ))
              and (:excludedQuestionId is null or q.id <> :excludedQuestionId)
            """)
    long countUnmasteredPracticeQuestionIds(
            @Param("userId") Long userId,
            @Param("status") QuestionStatus status,
            @Param("difficulty") QuestionDifficulty difficulty,
            @Param("tagId") Long tagId,
            @Param("keyword") String keyword,
            @Param("excludeAnswered") boolean excludeAnswered,
            @Param("excludedQuestionId") Long excludedQuestionId
    );

    @Query("""
            select distinct wrong.question.id
            from WrongQuestionRecord wrong
            join wrong.question q
            left join q.tags filterTag
            where wrong.user.id = :userId
              and wrong.mastered = false
              and q.status = :status
              and (:difficulty is null or q.difficulty = :difficulty)
              and (:tagId is null or filterTag.id = :tagId)
              and (:keyword is null
                  or q.stem like concat('%', :keyword, '%')
                  or lower(q.knowledgePoint) like lower(concat('%', :keyword, '%')))
              and (:excludeAnswered = false or not exists (
                  select 1
                  from PracticeAnswerRecord record
                  where record.user.id = :userId
                    and record.question.id = q.id
              ))
              and (:excludedQuestionId is null or q.id <> :excludedQuestionId)
            order by wrong.lastWrongAt asc
            """)
    List<Long> findUnmasteredPracticeQuestionIds(
            @Param("userId") Long userId,
            @Param("status") QuestionStatus status,
            @Param("difficulty") QuestionDifficulty difficulty,
            @Param("tagId") Long tagId,
            @Param("keyword") String keyword,
            @Param("excludeAnswered") boolean excludeAnswered,
            @Param("excludedQuestionId") Long excludedQuestionId,
            Pageable pageable
    );

    @Query("""
            select distinct wrong.question.id
            from WrongQuestionRecord wrong
            join wrong.question q
            left join q.tags filterTag
            where wrong.user.id = :userId
              and wrong.mastered = false
              and q.status = :status
              and (:difficulty is null or q.difficulty = :difficulty)
              and (:tagId is null or filterTag.id = :tagId)
              and (:keyword is null
                  or q.stem like concat('%', :keyword, '%')
                  or lower(q.knowledgePoint) like lower(concat('%', :keyword, '%')))
              and (:excludeAnswered = false or not exists (
                  select 1
                  from PracticeAnswerRecord record
                  where record.user.id = :userId
                    and record.question.id = q.id
              ))
              and (:currentQuestionId is null or q.id > :currentQuestionId)
            order by wrong.lastWrongAt asc
            """)
    List<Long> findNextUnmasteredPracticeQuestionIds(
            @Param("userId") Long userId,
            @Param("status") QuestionStatus status,
            @Param("difficulty") QuestionDifficulty difficulty,
            @Param("tagId") Long tagId,
            @Param("keyword") String keyword,
            @Param("excludeAnswered") boolean excludeAnswered,
            @Param("currentQuestionId") Long currentQuestionId,
            Pageable pageable
    );
}
