package xyz.kangnasi.interview.codextool;

import java.util.List;

public record CreateQuestionBatchToolResponse(
        boolean ok,
        List<Long> createdQuestionIds,
        int createdCount,
        int skippedCount,
        List<String> errors
) {
}
