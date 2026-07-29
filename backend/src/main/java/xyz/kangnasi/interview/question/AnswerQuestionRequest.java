package xyz.kangnasi.interview.question;

public record AnswerQuestionRequest(
        String selectedOptionKey,
        String mode,
        Integer timeSpentSeconds
) {
}
