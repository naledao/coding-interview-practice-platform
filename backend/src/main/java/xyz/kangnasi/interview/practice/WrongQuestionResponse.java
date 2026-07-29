package xyz.kangnasi.interview.practice;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import xyz.kangnasi.interview.question.Question;
import xyz.kangnasi.interview.question.QuestionDifficulty;
import xyz.kangnasi.interview.question.QuestionTag;
import xyz.kangnasi.interview.question.QuestionTagResponse;

public record WrongQuestionResponse(
        Long questionId,
        String stem,
        QuestionDifficulty difficulty,
        List<QuestionTagResponse> tags,
        int wrongCount,
        int correctAfterWrongCount,
        boolean mastered,
        Instant lastWrongAt,
        Instant lastAnsweredAt
) {

    public static WrongQuestionResponse from(WrongQuestionRecord wrongRecord) {
        Question question = wrongRecord.getQuestion();
        return new WrongQuestionResponse(
                question.getId(),
                question.getStem(),
                question.getDifficulty(),
                question.getTags().stream()
                        .sorted(Comparator.comparing(QuestionTag::getName))
                        .map(QuestionTagResponse::from)
                        .toList(),
                wrongRecord.getWrongCount(),
                wrongRecord.getCorrectAfterWrongCount(),
                wrongRecord.isMastered(),
                wrongRecord.getLastWrongAt(),
                wrongRecord.getLastAnsweredAt()
        );
    }
}
