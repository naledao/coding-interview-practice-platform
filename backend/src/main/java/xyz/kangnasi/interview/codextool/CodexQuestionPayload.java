package xyz.kangnasi.interview.codextool;

import java.util.List;

public record CodexQuestionPayload(
        String stem,
        String difficulty,
        String knowledgePoint,
        String answerAnalysis,
        String codexReviewSummary,
        List<String> tags,
        List<CodexQuestionOptionPayload> options
) {
}
