package xyz.kangnasi.interview.importjob;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class CodexQuestionGenerationPromptTest {

    private final CodexQuestionGenerationPrompt promptTemplate = new CodexQuestionGenerationPrompt();

    @Test
    void rendersScopedPromptAndMcpWorkflow() {
        String prompt = promptTemplate.render(42L, "interview_practice");

        assertThat(prompt)
                .contains("`importJobId=42`")
                .contains("Java 后端工程")
                .contains("Java 智能体工程")
                .contains("Spring AI")
                .contains("LangChain4j")
                .contains("名为 `interview_practice` 的 MCP server")
                .contains("允许生成 0 道题")
                .doesNotContain("{{importJobId}}", "{{mcpServerName}}");
        assertThat(prompt.indexOf("`validate_question_batch`"))
                .isLessThan(prompt.indexOf("`create_question_batch`"));
    }

    @Test
    void rejectsInvalidTemplateArguments() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> promptTemplate.render(0L, "interview_practice"));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> promptTemplate.render(1L, " "));
    }
}
