package xyz.kangnasi.interview.statistics;

public record StatisticsOverviewResponse(
        long answeredTotal,
        long correctTotal,
        long wrongTotal,
        double accuracy,
        long todayAnswered,
        long wrongBookCount,
        long favoriteCount,
        int streakDays
) {
}
