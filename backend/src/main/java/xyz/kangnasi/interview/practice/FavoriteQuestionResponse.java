package xyz.kangnasi.interview.practice;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import xyz.kangnasi.interview.question.Question;
import xyz.kangnasi.interview.question.QuestionDifficulty;
import xyz.kangnasi.interview.question.QuestionTag;
import xyz.kangnasi.interview.question.QuestionTagResponse;

public record FavoriteQuestionResponse(
        Long questionId,
        String stem,
        QuestionDifficulty difficulty,
        List<QuestionTagResponse> tags,
        Instant favoriteAt
) {

    public static FavoriteQuestionResponse from(FavoriteQuestion favorite) {
        Question question = favorite.getQuestion();
        return new FavoriteQuestionResponse(
                question.getId(),
                question.getStem(),
                question.getDifficulty(),
                question.getTags().stream()
                        .sorted(Comparator.comparing(QuestionTag::getName))
                        .map(QuestionTagResponse::from)
                        .toList(),
                favorite.getCreatedAt()
        );
    }
}
