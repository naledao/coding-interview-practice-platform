package xyz.kangnasi.interview.aitutor;

import java.util.List;

public record AiTutorModelOption(
        String model,
        String displayName,
        String defaultReasoningEffort,
        List<String> supportedReasoningEfforts
) {
}
