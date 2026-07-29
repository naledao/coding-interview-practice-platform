package xyz.kangnasi.interview.practice;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import xyz.kangnasi.interview.question.Question;
import xyz.kangnasi.interview.question.QuestionDifficulty;
import xyz.kangnasi.interview.question.QuestionTag;
import xyz.kangnasi.interview.question.QuestionTagResponse;

public record AnsweredQuestionResponse(
        Long questionId,
        String stem,
        QuestionDifficulty difficulty,
        List<QuestionTagResponse> tags,
        long answerCount,
        long correctCount,
        long wrongCount,
        Boolean lastCorrect,
        String lastSelectedOptionKey,
        Instant lastAnsweredAt,
        boolean favorite
) {

    public static AnsweredQuestionResponse from(
            Question question,
            AnsweredQuestionAggregate aggregate,
            AnsweredQuestionLatestRecord latestRecord,
            boolean favorite
    ) {
        long answerCount = aggregate.answerCount() == null ? 0 : aggregate.answerCount();
        long correctCount = aggregate.correctCount() == null ? 0 : aggregate.correctCount();
        return new AnsweredQuestionResponse(
                question.getId(),
                question.getStem(),
                question.getDifficulty(),
                question.getTags().stream()
                        .sorted(Comparator.comparing(QuestionTag::getName))
                        .map(QuestionTagResponse::from)
                        .toList(),
                answerCount,
                correctCount,
                Math.max(0, answerCount - correctCount),
                latestRecord == null ? null : latestRecord.correct(),
                latestRecord == null ? null : latestRecord.selectedOptionKey(),
                aggregate.lastAnsweredAt(),
                favorite
        );
    }
}
