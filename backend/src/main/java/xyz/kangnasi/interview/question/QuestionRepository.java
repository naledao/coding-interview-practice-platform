package xyz.kangnasi.interview.question;

import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuestionRepository extends JpaRepository<Question, Long> {

    boolean existsBySourceImportJobIdAndStemHash(Long sourceImportJobId, String stemHash);

    int countBySourceImportJobId(Long sourceImportJobId);

    Page<Question> findBySourceImportJobId(Long sourceImportJobId, Pageable pageable);

    Page<Question> findByStatus(QuestionStatus status, Pageable pageable);

    Page<Question> findByStatusAndStemContainingIgnoreCase(QuestionStatus status, String stem, Pageable pageable);

    List<Question> findBySourceImportJobIdOrderByIdAsc(Long sourceImportJobId);

    @Query("""
            select distinct q
            from Question q
            left join fetch q.tags
            where q.status = :status
              and q.id in :questionIds
            """)
    List<Question> findActiveQuestionsWithTagsByIdIn(
            @Param("status") QuestionStatus status,
            @Param("questionIds") Collection<Long> questionIds
    );

    @Query(
            value = """
                    select distinct q
                    from Question q
                    left join q.tags filterTag
                    where q.status = :status
                      and (:difficulty is null or q.difficulty = :difficulty)
                      and (:tagId is null or filterTag.id = :tagId)
                      and (:keyword is null
                          or q.stem like concat('%', :keyword, '%')
                          or lower(q.knowledgePoint) like lower(concat('%', :keyword, '%')))
                    """,
            countQuery = """
                    select count(distinct q)
                    from Question q
                    left join q.tags filterTag
                    where q.status = :status
                      and (:difficulty is null or q.difficulty = :difficulty)
                      and (:tagId is null or filterTag.id = :tagId)
                      and (:keyword is null
                          or q.stem like concat('%', :keyword, '%')
                          or lower(q.knowledgePoint) like lower(concat('%', :keyword, '%')))
                    """
    )
    Page<Question> searchUserQuestions(
            @Param("status") QuestionStatus status,
            @Param("difficulty") QuestionDifficulty difficulty,
            @Param("tagId") Long tagId,
            @Param("keyword") String keyword,
            Pageable pageable
    );

    @Query(
            value = """
                    select count(distinct q)
                    from Question q
                    left join q.tags filterTag
                    where q.status = :status
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
                      and not exists (
                          select 1
                          from ExcludedQuestion excluded
                          where excluded.user.id = :userId
                            and excluded.question.id = q.id
                      )
                      and (:excludedQuestionId is null or q.id <> :excludedQuestionId)
                    """
    )
    long countPracticeQuestionIds(
            @Param("status") QuestionStatus status,
            @Param("difficulty") QuestionDifficulty difficulty,
            @Param("tagId") Long tagId,
            @Param("keyword") String keyword,
            @Param("excludeAnswered") boolean excludeAnswered,
            @Param("userId") Long userId,
            @Param("excludedQuestionId") Long excludedQuestionId
    );

    @Query("""
            select distinct q.id
            from Question q
            left join q.tags filterTag
            where q.status = :status
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
              and not exists (
                  select 1
                  from ExcludedQuestion excluded
                  where excluded.user.id = :userId
                    and excluded.question.id = q.id
              )
              and (:excludedQuestionId is null or q.id <> :excludedQuestionId)
            order by q.id asc
            """)
    List<Long> findPracticeQuestionIds(
            @Param("status") QuestionStatus status,
            @Param("difficulty") QuestionDifficulty difficulty,
            @Param("tagId") Long tagId,
            @Param("keyword") String keyword,
            @Param("excludeAnswered") boolean excludeAnswered,
            @Param("userId") Long userId,
            @Param("excludedQuestionId") Long excludedQuestionId,
            Pageable pageable
    );

    @Query("""
            select distinct q.id
            from Question q
            left join q.tags filterTag
            where q.status = :status
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
              and not exists (
                  select 1
                  from ExcludedQuestion excluded
                  where excluded.user.id = :userId
                    and excluded.question.id = q.id
              )
              and (:currentQuestionId is null or q.id > :currentQuestionId)
            order by q.id asc
            """)
    List<Long> findNextPracticeQuestionIds(
            @Param("status") QuestionStatus status,
            @Param("difficulty") QuestionDifficulty difficulty,
            @Param("tagId") Long tagId,
            @Param("keyword") String keyword,
            @Param("excludeAnswered") boolean excludeAnswered,
            @Param("userId") Long userId,
            @Param("currentQuestionId") Long currentQuestionId,
            Pageable pageable
    );
}
