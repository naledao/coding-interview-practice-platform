package xyz.kangnasi.interview.question;

public record QuestionOptionResponse(
        Long id,
        String optionKey,
        String content,
        Boolean correct
) {

    public static QuestionOptionResponse from(QuestionOption option, boolean includeCorrect) {
        return new QuestionOptionResponse(
                option.getId(),
                option.getOptionKey(),
                option.getContent(),
                includeCorrect ? option.isCorrect() : null
        );
    }
}
