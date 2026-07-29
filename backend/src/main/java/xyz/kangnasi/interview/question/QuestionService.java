package xyz.kangnasi.interview.question;

import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.kangnasi.interview.auth.UserPrincipal;
import xyz.kangnasi.interview.common.AppException;
import xyz.kangnasi.interview.practice.FavoriteQuestionRepository;
import xyz.kangnasi.interview.practice.PracticeAnswerRecordRepository;

@Service
public class QuestionService {

    private final QuestionRepository questionRepository;
    private final QuestionTagRepository tagRepository;
    private final FavoriteQuestionRepository favoriteQuestionRepository;
    private final PracticeAnswerRecordRepository answerRecordRepository;

    public QuestionService(
            QuestionRepository questionRepository,
            QuestionTagRepository tagRepository,
            FavoriteQuestionRepository favoriteQuestionRepository,
            PracticeAnswerRecordRepository answerRecordRepository
    ) {
        this.questionRepository = questionRepository;
        this.tagRepository = tagRepository;
        this.favoriteQuestionRepository = favoriteQuestionRepository;
        this.answerRecordRepository = answerRecordRepository;
    }

    @Transactional(readOnly = true)
    public Page<QuestionSummaryResponse> listUserQuestions(
            UserPrincipal principal,
            int page,
            int pageSize,
            QuestionDifficulty difficulty,
            Long tagId,
            String keyword
    ) {
        PageRequest pageRequest = PageRequest.of(normalizePage(page), normalizePageSize(pageSize), Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Question> questionPage = questionRepository.searchUserQuestions(
                        QuestionStatus.ACTIVE,
                        difficulty,
                        normalizeOptionalId(tagId, "tagId 参数错误"),
                        normalizeKeyword(keyword),
                        pageRequest
                );

        Long userId = currentUserId(principal);
        List<Long> questionIds = questionPage.getContent().stream()
                .map(Question::getId)
                .toList();
        Set<Long> favoriteQuestionIds = userId == null || questionIds.isEmpty()
                ? Set.of()
                : favoriteQuestionRepository.findFavoriteQuestionIds(userId, questionIds);
        Set<Long> answeredQuestionIds = userId == null || questionIds.isEmpty()
                ? Set.of()
                : answerRecordRepository.findAnsweredQuestionIds(userId, questionIds);

        return questionPage.map(question -> QuestionSummaryResponse.from(
                question,
                favoriteQuestionIds.contains(question.getId()),
                answeredQuestionIds.contains(question.getId())
        ));
    }

    @Transactional(readOnly = true)
    public QuestionResponse questionDetail(UserPrincipal principal, Long questionId, boolean admin) {
        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> AppException.notFound("题目不存在或已下线"));
        if (!admin && question.getStatus() != QuestionStatus.ACTIVE) {
            throw AppException.notFound("题目不存在或已下线");
        }
        return QuestionResponse.from(
                question,
                admin,
                isFavorite(principal, question.getId()),
                admin || isAnswered(principal, question.getId())
        );
    }

    @Transactional(readOnly = true)
    public QuestionResponse questionAnalysis(UserPrincipal principal, Long questionId) {
        Question question = findActiveQuestion(questionId);
        return QuestionResponse.from(question, true, isFavorite(principal, question.getId()), true);
    }

    @Transactional(readOnly = true)
    public Page<QuestionSummaryResponse> listAdminQuestions(int page, int pageSize, QuestionStatus status, Long importJobId) {
        PageRequest pageRequest = PageRequest.of(normalizePage(page), normalizePageSize(pageSize), Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Question> questions;
        if (importJobId != null) {
            questions = questionRepository.findBySourceImportJobId(importJobId, pageRequest);
        } else if (status != null) {
            questions = questionRepository.findByStatus(status, pageRequest);
        } else {
            questions = questionRepository.findAll(pageRequest);
        }
        return questions.map(QuestionSummaryResponse::from);
    }

    @Transactional(readOnly = true)
    public Page<QuestionSummaryResponse> listImportJobQuestions(Long importJobId, int page, int pageSize) {
        PageRequest pageRequest = PageRequest.of(normalizePage(page), normalizePageSize(pageSize), Sort.by(Sort.Direction.DESC, "createdAt"));
        return questionRepository.findBySourceImportJobId(importJobId, pageRequest)
                .map(QuestionSummaryResponse::from);
    }

    @Transactional(readOnly = true)
    public java.util.List<QuestionTagResponse> listTags() {
        return tagRepository.findAllByOrderByQuestionCountDescNameAsc().stream()
                .map(QuestionTagResponse::from)
                .toList();
    }

    @Transactional
    public QuestionResponse disable(Long questionId) {
        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> AppException.notFound("题目不存在"));
        question.disable();
        return QuestionResponse.from(question, true);
    }

    @Transactional
    public QuestionResponse enable(Long questionId) {
        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> AppException.notFound("题目不存在"));
        question.enable();
        return QuestionResponse.from(question, true);
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

    private Question findActiveQuestion(Long questionId) {
        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> AppException.notFound("题目不存在或已下线"));
        if (question.getStatus() != QuestionStatus.ACTIVE) {
            throw AppException.notFound("题目不存在或已下线");
        }
        return question;
    }

    private Long normalizeOptionalId(Long value, String message) {
        if (value == null) {
            return null;
        }
        if (value < 1) {
            throw AppException.badRequest(message);
        }
        return value;
    }

    private String normalizeKeyword(String keyword) {
        return keyword == null || keyword.isBlank() ? null : keyword.trim();
    }

    private boolean isFavorite(UserPrincipal principal, Long questionId) {
        Long userId = currentUserId(principal);
        return userId != null && favoriteQuestionRepository.existsByUser_IdAndQuestion_Id(userId, questionId);
    }

    private boolean isAnswered(UserPrincipal principal, Long questionId) {
        Long userId = currentUserId(principal);
        return userId != null && answerRecordRepository.existsByUser_IdAndQuestion_Id(userId, questionId);
    }

    private Long currentUserId(UserPrincipal principal) {
        return principal == null ? null : principal.id();
    }

}
