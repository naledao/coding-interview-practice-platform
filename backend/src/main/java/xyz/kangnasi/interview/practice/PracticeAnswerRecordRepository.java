package xyz.kangnasi.interview.practice;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import xyz.kangnasi.interview.question.QuestionStatus;
import xyz.kangnasi.interview.statistics.TagStatisticsAggregation;

public interface PracticeAnswerRecordRepository extends JpaRepository<PracticeAnswerRecord, Long> {

    boolean existsByUser_IdAndQuestion_Id(Long userId, Long questionId);

    long countByUser_Id(Long userId);

    long countByUser_IdAndCorrectTrue(Long userId);

    @Query(
            value = """
                    select record.question.id,
                           count(record),
                           coalesce(sum(case when record.correct = true then 1L else 0L end), 0L),
                           max(record.answeredAt)
                    from PracticeAnswerRecord record
                    join record.question question
                    where record.user.id = :userId
                      and question.status = :status
                    group by record.question.id
                    order by max(record.answeredAt) desc, max(record.id) desc
                    """,
            countQuery = """
                    select count(distinct record.question.id)
                    from PracticeAnswerRecord record
                    join record.question question
                    where record.user.id = :userId
                      and question.status = :status
                    """
    )
    Page<Object[]> findAnsweredQuestionAggregates(
            @Param("userId") Long userId,
            @Param("status") QuestionStatus status,
            Pageable pageable
    );

    @Query("""
            select record.question.id,
                   record.selectedOptionKey,
                   record.correct,
                   record.answeredAt
            from PracticeAnswerRecord record
            where record.user.id = :userId
              and record.question.id in :questionIds
            order by record.answeredAt desc, record.id desc
            """)
    List<Object[]> findLatestRecordRowsByUserIdAndQuestionIds(
            @Param("userId") Long userId,
            @Param("questionIds") Collection<Long> questionIds
    );

    @Query("""
            select distinct record.question.id
            from PracticeAnswerRecord record
            where record.user.id = :userId
              and record.question.id in :questionIds
            """)
    Set<Long> findAnsweredQuestionIds(
            @Param("userId") Long userId,
            @Param("questionIds") Collection<Long> questionIds
    );

    long countByUser_IdAndAnsweredAtGreaterThanEqualAndAnsweredAtLessThan(
            Long userId,
            Instant startInclusive,
            Instant endExclusive
    );

    @Query("""
            select record.answeredAt as answeredAt,
                   record.correct as correct
            from PracticeAnswerRecord record
            where record.user.id = :userId
              and record.answeredAt >= :startInclusive
              and record.answeredAt < :endExclusive
            """)
    List<Object[]> findDailyAnswerRecords(
            @Param("userId") Long userId,
            @Param("startInclusive") Instant startInclusive,
            @Param("endExclusive") Instant endExclusive
    );

    @Query("""
            select record.answeredAt
            from PracticeAnswerRecord record
            where record.user.id = :userId
            order by record.answeredAt desc
            """)
    List<Instant> findAnsweredAtByUserId(@Param("userId") Long userId);

    @Query("""
            select new xyz.kangnasi.interview.statistics.TagStatisticsAggregation(
                tag.id,
                tag.name,
                count(record),
                coalesce(sum(case when record.correct = true then 1L else 0L end), 0L)
            )
            from PracticeAnswerRecord record
            join record.question question
            join question.tags tag
            where record.user.id = :userId
            group by tag.id, tag.name
            """)
    List<TagStatisticsAggregation> findTagStatistics(@Param("userId") Long userId);
}
