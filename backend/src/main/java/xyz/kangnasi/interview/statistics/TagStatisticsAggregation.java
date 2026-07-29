package xyz.kangnasi.interview.statistics;

public record TagStatisticsAggregation(
        Long tagId,
        String tagName,
        Long answeredTotal,
        Long correctTotal
) {
    public TagStatisticsAggregation {
        answeredTotal = answeredTotal == null ? 0L : answeredTotal;
        correctTotal = correctTotal == null ? 0L : correctTotal;
    }
}
