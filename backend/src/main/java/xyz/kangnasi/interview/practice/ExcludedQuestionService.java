package xyz.kangnasi.interview.practice;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.kangnasi.interview.auth.UserPrincipal;
import xyz.kangnasi.interview.common.AppException;
import xyz.kangnasi.interview.question.Question;
import xyz.kangnasi.interview.question.QuestionRepository;
import xyz.kangnasi.interview.question.QuestionStatus;
import xyz.kangnasi.interview.user.AppUser;
import xyz.kangnasi.interview.user.UserRepository;

@Service
public class ExcludedQuestionService {

    private final ExcludedQuestionRepository excludedQuestionRepository;
    private final QuestionRepository questionRepository;
    private final UserRepository userRepository;

    public ExcludedQuestionService(
            ExcludedQuestionRepository excludedQuestionRepository,
            QuestionRepository questionRepository,
            UserRepository userRepository
    ) {
        this.excludedQuestionRepository = excludedQuestionRepository;
        this.questionRepository = questionRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public Page<ExcludedQuestionResponse> listExcludedQuestions(
            UserPrincipal principal,
            int page,
            int pageSize
    ) {
        Long userId = requireUserId(principal);
        PageRequest pageRequest = PageRequest.of(
                normalizePage(page),
                normalizePageSize(pageSize),
                Sort.by(Sort.Direction.DESC, "createdAt")
        );
        return excludedQuestionRepository.findByUser_Id(userId, pageRequest)
                .map(ExcludedQuestionResponse::from);
    }

    @Transactional
    public ExcludedQuestionResponse excludeQuestion(UserPrincipal principal, Long questionId) {
        Long userId = requireUserId(principal);
        Long normalizedQuestionId = normalizeQuestionId(questionId);
        Question question = findActiveQuestion(normalizedQuestionId);
        ExcludedQuestion excludedQuestion = excludedQuestionRepository
                .findByUser_IdAndQuestion_Id(userId, normalizedQuestionId)
                .orElseGet(() -> excludedQuestionRepository.save(ExcludedQuestion.create(
                        findUser(userId),
                        question
                )));
        return ExcludedQuestionResponse.from(excludedQuestion);
    }

    @Transactional
    public void restoreQuestion(UserPrincipal principal, Long questionId) {
        Long userId = requireUserId(principal);
        excludedQuestionRepository.deleteExcludedQuestion(userId, normalizeQuestionId(questionId));
    }

    private Question findActiveQuestion(Long questionId) {
        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> AppException.notFound("题目不存在或已下线"));
        if (question.getStatus() != QuestionStatus.ACTIVE) {
            throw AppException.notFound("题目不存在或已下线");
        }
        return question;
    }

    private AppUser findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> AppException.unauthorized("未登录或登录已过期"));
    }

    private Long requireUserId(UserPrincipal principal) {
        if (principal == null || principal.id() == null) {
            throw AppException.unauthorized("未登录或登录已过期");
        }
        return principal.id();
    }

    private Long normalizeQuestionId(Long questionId) {
        if (questionId == null || questionId < 1) {
            throw AppException.badRequest("questionId 参数错误");
        }
        return questionId;
    }

    private int normalizePage(int page) {
        return Math.max(page, 1) - 1;
    }

    private int normalizePageSize(int pageSize) {
        if (pageSize < 1) {
            return 20;
        }
        return Math.min(pageSize, 100);
    }
}
