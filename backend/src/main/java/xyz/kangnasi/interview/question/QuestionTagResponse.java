package xyz.kangnasi.interview.question;

public record QuestionTagResponse(
        Long id,
        String name,
        TagCategory category,
        int questionCount
) {

    public static QuestionTagResponse from(QuestionTag tag) {
        return new QuestionTagResponse(
                tag.getId(),
                tag.getName(),
                tag.getCategory(),
                tag.getQuestionCount()
        );
    }
}
