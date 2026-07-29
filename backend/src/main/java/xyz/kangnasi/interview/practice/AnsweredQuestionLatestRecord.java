package xyz.kangnasi.interview.practice;

import java.time.Instant;

record AnsweredQuestionLatestRecord(
        Long questionId,
        String selectedOptionKey,
        Boolean correct,
        Instant answeredAt
) {
}
