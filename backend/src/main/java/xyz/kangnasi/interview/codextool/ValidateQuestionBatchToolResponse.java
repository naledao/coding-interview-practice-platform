package xyz.kangnasi.interview.codextool;

import java.util.List;

public record ValidateQuestionBatchToolResponse(
        boolean ok,
        int validCount,
        int skippedCount,
        List<String> errors
) {
}
