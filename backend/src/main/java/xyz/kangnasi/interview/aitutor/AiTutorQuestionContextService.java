package xyz.kangnasi.interview.aitutor;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.kangnasi.interview.common.AppException;
import xyz.kangnasi.interview.question.Question;
import xyz.kangnasi.interview.question.QuestionOption;
import xyz.kangnasi.interview.question.QuestionRepository;
import xyz.kangnasi.interview.question.QuestionStatus;

@Service
public class AiTutorQuestionContextService {

    private static final String INSTRUCTIONS = """
            你是 Java 面试刷题应用中的 AI 助教。请围绕当前题目和用户追问进行准确、清晰的讲解。
            使用 Markdown 组织回答；涉及代码时使用带语言标识的代码块。
            不要假设用户已经提交答案；除非用户明确要求，否则先解释思路，再给出结论。
            """;

    private final QuestionRepository questionRepository;

    public AiTutorQuestionContextService(QuestionRepository questionRepository) {
        this.questionRepository = questionRepository;
    }

    @Transactional(readOnly = true)
    public CodexRunContext build(Long questionId) {
        if (questionId == null || questionId <= 0) {
            throw AppException.badRequest("questionId 参数错误");
        }
        Question question = questionRepository.findById(questionId)
                .filter(item -> item.getStatus() == QuestionStatus.ACTIVE)
                .orElseThrow(() -> AppException.notFound("题目不存在或已下线"));

        StringBuilder content = new StringBuilder(INSTRUCTIONS)
                .append("\n## 当前题目\n")
                .append(question.getStem())
                .append("\n\n## 选项\n");
        for (QuestionOption option : question.getOptions()) {
            content.append("- **")
                    .append(option.getOptionKey())
                    .append("**：")
                    .append(option.getContent())
                    .append('\n');
        }

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("source", "interview-practice");
        metadata.put("questionId", question.getId());
        return new CodexRunContext(content.toString().trim(), metadata);
    }
}
