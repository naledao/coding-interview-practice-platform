package xyz.kangnasi.interview.practice;

import java.time.Instant;

public record AnsweredQuestionAggregate(
        Long questionId,
        Long answerCount,
        Long correctCount,
        Instant lastAnsweredAt
) {
}
