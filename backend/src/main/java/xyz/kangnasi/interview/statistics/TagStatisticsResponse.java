package xyz.kangnasi.interview.statistics;

public record TagStatisticsResponse(
        Long tagId,
        String tagName,
        long answeredTotal,
        long correctTotal,
        long wrongTotal,
        double accuracy
) {
    public static TagStatisticsResponse from(TagStatisticsAggregation aggregation) {
        long wrongTotal = aggregation.answeredTotal() - aggregation.correctTotal();
        return new TagStatisticsResponse(
                aggregation.tagId(),
                aggregation.tagName(),
                aggregation.answeredTotal(),
                aggregation.correctTotal(),
                wrongTotal,
                StatisticsService.accuracy(aggregation.correctTotal(), aggregation.answeredTotal())
        );
    }
}
