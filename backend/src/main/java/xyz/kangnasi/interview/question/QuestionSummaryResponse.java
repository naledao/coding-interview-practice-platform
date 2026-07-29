package xyz.kangnasi.interview.question;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

public record QuestionSummaryResponse(
        Long id,
        QuestionType type,
        String stem,
        QuestionDifficulty difficulty,
        String knowledgePoint,
        Long sourceDocumentId,
        Long sourceImportJobId,
        QuestionStatus status,
        List<QuestionTagResponse> tags,
        boolean favorite,
        boolean answered,
        Instant createdAt
) {

    public static QuestionSummaryResponse from(Question question) {
        return from(question, false, false);
    }

    public static QuestionSummaryResponse from(Question question, boolean favorite, boolean answered) {
        return new QuestionSummaryResponse(
                question.getId(),
                question.getType(),
                question.getStem(),
                question.getDifficulty(),
                question.getKnowledgePoint(),
                question.getSourceDocumentId(),
                question.getSourceImportJobId(),
                question.getStatus(),
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
