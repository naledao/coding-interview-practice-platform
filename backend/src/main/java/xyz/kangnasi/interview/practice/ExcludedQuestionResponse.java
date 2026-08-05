package xyz.kangnasi.interview.practice;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import xyz.kangnasi.interview.question.Question;
import xyz.kangnasi.interview.question.QuestionDifficulty;
import xyz.kangnasi.interview.question.QuestionStatus;
import xyz.kangnasi.interview.question.QuestionTag;
import xyz.kangnasi.interview.question.QuestionTagResponse;

public record ExcludedQuestionResponse(
        Long questionId,
        String stem,
        QuestionDifficulty difficulty,
        String knowledgePoint,
        QuestionStatus status,
        List<QuestionTagResponse> tags,
        Instant excludedAt
) {

    public static ExcludedQuestionResponse from(ExcludedQuestion excludedQuestion) {
        Question question = excludedQuestion.getQuestion();
        return new ExcludedQuestionResponse(
                question.getId(),
                question.getStem(),
                question.getDifficulty(),
                question.getKnowledgePoint(),
                question.getStatus(),
                question.getTags().stream()
                        .sorted(Comparator.comparing(QuestionTag::getName))
                        .map(QuestionTagResponse::from)
                        .toList(),
                excludedQuestion.getCreatedAt()
        );
    }
}
