package xyz.kangnasi.interview.codextool;

import java.util.List;

public record CreateQuestionBatchToolRequest(
        Long importJobId,
        List<CodexQuestionPayload> questions
) {
}
