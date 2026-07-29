package xyz.kangnasi.interview.statistics;

import java.time.LocalDate;

public record DailyStatisticsResponse(
        LocalDate date,
        long answeredTotal,
        long correctTotal
) {
}
