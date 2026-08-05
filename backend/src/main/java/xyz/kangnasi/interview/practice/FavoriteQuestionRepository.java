package xyz.kangnasi.interview.practice;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import xyz.kangnasi.interview.question.QuestionDifficulty;
import xyz.kangnasi.interview.question.QuestionStatus;

public interface FavoriteQuestionRepository extends JpaRepository<FavoriteQuestion, Long> {

    boolean existsByUser_IdAndQuestion_Id(Long userId, Long questionId);

    long countByUser_Id(Long userId);

    @Query("""
            select favorite
            from FavoriteQuestion favorite
            join fetch favorite.user user
            join fetch favorite.question question
            where user.id = :userId
              and question.id = :questionId
            """)
    Optional<FavoriteQuestion> findByUser_IdAndQuestion_Id(
            @Param("userId") Long userId,
            @Param("questionId") Long questionId
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            delete from FavoriteQuestion favorite
            where favorite.user.id = :userId
              and favorite.question.id = :questionId
            """)
    int deleteFavorite(
            @Param("userId") Long userId,
            @Param("questionId") Long questionId
    );

    @Query("""
            select favorite.question.id
            from FavoriteQuestion favorite
            where favorite.user.id = :userId
              and favorite.question.id in :questionIds
            """)
    Set<Long> findFavoriteQuestionIds(
            @Param("userId") Long userId,
            @Param("questionIds") Collection<Long> questionIds
    );

    @Query(
            value = """
                    select favorite
                    from FavoriteQuestion favorite
                    join fetch favorite.user user
                    join fetch favorite.question q
                    where user.id = :userId
                      and q.status = :status
                    """,
            countQuery = """
                    select count(favorite)
                    from FavoriteQuestion favorite
                    join favorite.question q
                    where favorite.user.id = :userId
                      and q.status = :status
                    """
    )
    Page<FavoriteQuestion> findFavoritePage(
            @Param("userId") Long userId,
            @Param("status") QuestionStatus status,
            Pageable pageable
    );

    @Query("""
            select count(distinct favorite.question.id)
            from FavoriteQuestion favorite
            join favorite.question q
            left join q.tags filterTag
            where favorite.user.id = :userId
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
              and not exists (
                  select 1
                  from ExcludedQuestion excluded
                  where excluded.user.id = :userId
                    and excluded.question.id = q.id
              )
              and (:excludedQuestionId is null or q.id <> :excludedQuestionId)
            """)
    long countFavoritePracticeQuestionIds(
            @Param("userId") Long userId,
            @Param("status") QuestionStatus status,
            @Param("difficulty") QuestionDifficulty difficulty,
            @Param("tagId") Long tagId,
            @Param("keyword") String keyword,
            @Param("excludeAnswered") boolean excludeAnswered,
            @Param("excludedQuestionId") Long excludedQuestionId
    );

    @Query("""
            select distinct favorite.question.id
            from FavoriteQuestion favorite
            join favorite.question q
            left join q.tags filterTag
            where favorite.user.id = :userId
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
              and not exists (
                  select 1
                  from ExcludedQuestion excluded
                  where excluded.user.id = :userId
                    and excluded.question.id = q.id
              )
              and (:excludedQuestionId is null or q.id <> :excludedQuestionId)
            order by favorite.createdAt asc
            """)
    List<Long> findFavoritePracticeQuestionIds(
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
            select distinct favorite.question.id
            from FavoriteQuestion favorite
            join favorite.question q
            left join q.tags filterTag
            where favorite.user.id = :userId
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
              and not exists (
                  select 1
                  from ExcludedQuestion excluded
                  where excluded.user.id = :userId
                    and excluded.question.id = q.id
              )
              and (:currentQuestionId is null or q.id > :currentQuestionId)
            order by favorite.createdAt asc
            """)
    List<Long> findNextFavoritePracticeQuestionIds(
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
