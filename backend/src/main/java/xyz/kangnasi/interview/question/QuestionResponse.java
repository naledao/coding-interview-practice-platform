package xyz.kangnasi.interview.question;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

public record QuestionResponse(
        Long id,
        QuestionType type,
        String stem,
        QuestionDifficulty difficulty,
        String knowledgePoint,
        String answerAnalysis,
        String codexReviewSummary,
        Long sourceDocumentId,
        Long sourceImportJobId,
        QuestionStatus status,
        List<QuestionOptionResponse> options,
        List<QuestionTagResponse> tags,
        Boolean favorite,
        Boolean answered,
        Instant createdAt
) {

    public static QuestionResponse from(Question question, boolean includeAnswer) {
        return from(question, includeAnswer, false, includeAnswer);
    }

    public static QuestionResponse from(Question question, boolean includeAnswer, boolean favorite, boolean answered) {
        return new QuestionResponse(
                question.getId(),
                question.getType(),
                question.getStem(),
                question.getDifficulty(),
                question.getKnowledgePoint(),
                includeAnswer ? question.getAnswerAnalysis() : null,
                includeAnswer ? question.getCodexReviewSummary() : null,
                question.getSourceDocumentId(),
                question.getSourceImportJobId(),
                question.getStatus(),
                question.getOptions().stream()
                        .sorted(Comparator.comparing(QuestionOption::getOptionKey))
                        .map(option -> QuestionOptionResponse.from(option, includeAnswer))
                        .toList(),
                question.getTags().stream()
                        .sorted(Comparator.comparing(QuestionTag::getName))
                        .map(QuestionTagResponse::from)
                        .toList(),
                favorite,
                answered,
                question.getCreatedAt()
        );
    }
}
