package xyz.kangnasi.interview.statistics;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.kangnasi.interview.auth.UserPrincipal;
import xyz.kangnasi.interview.common.AppException;
import xyz.kangnasi.interview.practice.FavoriteQuestionRepository;
import xyz.kangnasi.interview.practice.PracticeAnswerRecordRepository;
import xyz.kangnasi.interview.practice.WrongQuestionRecordRepository;

@Service
public class StatisticsService {

    private static final int DEFAULT_DAILY_DAYS = 7;
    private static final int MAX_DAILY_DAYS = 31;

    private final PracticeAnswerRecordRepository answerRecordRepository;
    private final WrongQuestionRecordRepository wrongQuestionRecordRepository;
    private final FavoriteQuestionRepository favoriteQuestionRepository;
    private final ZoneId zoneId;

    public StatisticsService(
            PracticeAnswerRecordRepository answerRecordRepository,
            WrongQuestionRecordRepository wrongQuestionRecordRepository,
            FavoriteQuestionRepository favoriteQuestionRepository
    ) {
        this.answerRecordRepository = answerRecordRepository;
        this.wrongQuestionRecordRepository = wrongQuestionRecordRepository;
        this.favoriteQuestionRepository = favoriteQuestionRepository;
        this.zoneId = ZoneId.systemDefault();
    }

    @Transactional(readOnly = true)
    public StatisticsOverviewResponse overview(UserPrincipal principal) {
        Long userId = requireUserId(principal);
        long answeredTotal = answerRecordRepository.countByUser_Id(userId);
        long correctTotal = answerRecordRepository.countByUser_IdAndCorrectTrue(userId);
        long wrongTotal = answeredTotal - correctTotal;
        LocalDate today = LocalDate.now(zoneId);

        return new StatisticsOverviewResponse(
                answeredTotal,
                correctTotal,
                wrongTotal,
                accuracy(correctTotal, answeredTotal),
                answerRecordRepository.countByUser_IdAndAnsweredAtGreaterThanEqualAndAnsweredAtLessThan(
                        userId,
                        startOfDay(today),
                        startOfDay(today.plusDays(1))
                ),
                wrongQuestionRecordRepository.countByUser_IdAndMasteredFalse(userId),
                favoriteQuestionRepository.countByUser_Id(userId),
                streakDays(userId, today)
        );
    }

    @Transactional(readOnly = true)
    public List<TagStatisticsResponse> tags(UserPrincipal principal, String sort) {
        Long userId = requireUserId(principal);
        Comparator<TagStatisticsResponse> comparator = tagComparator(sort);
        return answerRecordRepository.findTagStatistics(userId).stream()
                .map(TagStatisticsResponse::from)
                .sorted(comparator)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DailyStatisticsResponse> daily(UserPrincipal principal, Integer days) {
        Long userId = requireUserId(principal);
        int normalizedDays = normalizeDays(days);
        LocalDate endDate = LocalDate.now(zoneId);
        LocalDate startDate = endDate.minusDays(normalizedDays - 1L);
        Map<LocalDate, DailyCounter> counters = new LinkedHashMap<>();
        for (int i = 0; i < normalizedDays; i++) {
            counters.put(startDate.plusDays(i), new DailyCounter());
        }

        List<Object[]> records = answerRecordRepository
                .findDailyAnswerRecords(
                        userId,
                        startOfDay(startDate),
                        startOfDay(endDate.plusDays(1))
                );
        for (Object[] record : records) {
            Instant answeredAt = (Instant) record[0];
            boolean correct = Boolean.TRUE.equals(record[1]);
            LocalDate date = LocalDate.ofInstant(answeredAt, zoneId);
            DailyCounter counter = counters.get(date);
            if (counter != null) {
                counter.record(correct);
            }
        }

        return counters.entrySet().stream()
                .map(entry -> new DailyStatisticsResponse(
                        entry.getKey(),
                        entry.getValue().answeredTotal(),
                        entry.getValue().correctTotal()
                ))
                .toList();
    }

    static double accuracy(long correctTotal, long answeredTotal) {
        if (answeredTotal == 0) {
            return 0;
        }
        return BigDecimal.valueOf(correctTotal)
                .divide(BigDecimal.valueOf(answeredTotal), 4, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private int streakDays(Long userId, LocalDate today) {
        Set<LocalDate> answeredDates = answerRecordRepository.findAnsweredAtByUserId(userId).stream()
                .map(answeredAt -> LocalDate.ofInstant(answeredAt, zoneId))
                .collect(Collectors.toSet());
        int streak = 0;
        LocalDate cursor = today;
        while (answeredDates.contains(cursor)) {
            streak++;
            cursor = cursor.minusDays(1);
        }
        return streak;
    }

    private Comparator<TagStatisticsResponse> tagComparator(String sort) {
        String normalized = sort == null ? "" : sort.trim().toLowerCase(Locale.ROOT);
        if (Set.of("accuracy_asc", "accuracy", "weak").contains(normalized)) {
            return Comparator
                    .comparingDouble(TagStatisticsResponse::accuracy)
                    .thenComparing(Comparator.comparingLong(TagStatisticsResponse::answeredTotal).reversed())
                    .thenComparing(TagStatisticsResponse::tagName);
        }
        return Comparator
                .comparingLong(TagStatisticsResponse::answeredTotal)
                .reversed()
                .thenComparingDouble(TagStatisticsResponse::accuracy)
                .thenComparing(TagStatisticsResponse::tagName);
    }

    private int normalizeDays(Integer days) {
        if (days == null) {
            return DEFAULT_DAILY_DAYS;
        }
        if (days < 1 || days > MAX_DAILY_DAYS) {
            return DEFAULT_DAILY_DAYS;
        }
        return days;
    }

    private Instant startOfDay(LocalDate date) {
        return date.atStartOfDay(zoneId).toInstant();
    }

    private Long requireUserId(UserPrincipal principal) {
        if (principal == null || principal.id() == null) {
            throw AppException.unauthorized("未登录或登录已过期");
        }
        return principal.id();
    }

    private static final class DailyCounter {
        private long answeredTotal;
        private long correctTotal;

        void record(boolean correct) {
            answeredTotal++;
            if (correct) {
                correctTotal++;
            }
        }

        long answeredTotal() {
            return answeredTotal;
        }

        long correctTotal() {
            return correctTotal;
        }
    }
}
