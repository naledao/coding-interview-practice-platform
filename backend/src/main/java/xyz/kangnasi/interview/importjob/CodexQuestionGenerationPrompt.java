package xyz.kangnasi.interview.importjob;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public class CodexQuestionGenerationPrompt {

    static final String RESOURCE_PATH = "prompts/codex-question-generation.md";
    private static final String IMPORT_JOB_ID_PLACEHOLDER = "{{importJobId}}";
    private static final String MCP_SERVER_NAME_PLACEHOLDER = "{{mcpServerName}}";

    private final String template;

    public CodexQuestionGenerationPrompt() {
        this.template = loadTemplate();
    }

    String render(Long importJobId, String mcpServerName) {
        if (importJobId == null || importJobId < 1) {
            throw new IllegalArgumentException("importJobId 必须是正整数");
        }
        if (mcpServerName == null || mcpServerName.isBlank()) {
            throw new IllegalArgumentException("MCP server 名称不能为空");
        }

        String prompt = template
                .replace(IMPORT_JOB_ID_PLACEHOLDER, importJobId.toString())
                .replace(MCP_SERVER_NAME_PLACEHOLDER, mcpServerName);
        if (prompt.contains(IMPORT_JOB_ID_PLACEHOLDER) || prompt.contains(MCP_SERVER_NAME_PLACEHOLDER)) {
            throw new IllegalStateException("Codex 产题提示词存在未替换的占位符");
        }
        return prompt.strip();
    }

    private static String loadTemplate() {
        try {
            return new ClassPathResource(RESOURCE_PATH).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("无法读取 Codex 产题提示词：" + RESOURCE_PATH, exception);
        }
    }
}
