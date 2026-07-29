package xyz.kangnasi.interview.codextool;

import com.fasterxml.jackson.databind.JsonNode;
import xyz.kangnasi.interview.importjob.ImportJobLogLevel;

public record AppendGenerationLogToolRequest(
        Long importJobId,
        ImportJobLogLevel level,
        String message,
        JsonNode payload
) {
}
