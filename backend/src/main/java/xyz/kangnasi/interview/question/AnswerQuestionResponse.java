package xyz.kangnasi.interview.question;

public record AnswerQuestionResponse(
        Long questionId,
        String selectedOptionKey,
        boolean correct,
        String correctOptionKey,
        String answerAnalysis,
        boolean wrongBookUpdated,
        Long recordId
) {
}
