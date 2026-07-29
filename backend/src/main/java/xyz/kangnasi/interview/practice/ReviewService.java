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
public class ReviewService {

    private final WrongQuestionRecordRepository wrongQuestionRecordRepository;
    private final FavoriteQuestionRepository favoriteQuestionRepository;
    private final QuestionRepository questionRepository;
    private final UserRepository userRepository;

    public ReviewService(
            WrongQuestionRecordRepository wrongQuestionRecordRepository,
            FavoriteQuestionRepository favoriteQuestionRepository,
            QuestionRepository questionRepository,
            UserRepository userRepository
    ) {
        this.wrongQuestionRecordRepository = wrongQuestionRecordRepository;
        this.favoriteQuestionRepository = favoriteQuestionRepository;
        this.questionRepository = questionRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public Page<WrongQuestionResponse> listWrongQuestions(
            UserPrincipal principal,
            int page,
            int pageSize,
            Boolean mastered
    ) {
        Long userId = requireUserId(principal);
        PageRequest pageRequest = PageRequest.of(
                normalizePage(page),
                normalizePageSize(pageSize),
                Sort.by(Sort.Direction.DESC, "lastWrongAt")
        );
        return wrongQuestionRecordRepository.findWrongQuestionPage(
                        userId,
                        QuestionStatus.ACTIVE,
                        mastered,
                        pageRequest
                )
                .map(WrongQuestionResponse::from);
    }

    @Transactional
    public WrongQuestionResponse markMastered(UserPrincipal principal, Long questionId) {
        WrongQuestionRecord record = findWrongRecord(principal, questionId);
        record.markMastered();
        return WrongQuestionResponse.from(record);
    }

    @Transactional
    public WrongQuestionResponse unmarkMastered(UserPrincipal principal, Long questionId) {
        WrongQuestionRecord record = findWrongRecord(principal, questionId);
        record.unmarkMastered();
        return WrongQuestionResponse.from(record);
    }

    @Transactional
    public void removeWrongQuestion(UserPrincipal principal, Long questionId) {
        Long userId = requireUserId(principal);
        WrongQuestionRecord record = wrongQuestionRecordRepository.findByUser_IdAndQuestion_Id(userId, normalizeQuestionId(questionId))
                .orElseThrow(() -> AppException.notFound("错题记录不存在"));
        wrongQuestionRecordRepository.delete(record);
    }

    @Transactional(readOnly = true)
    public Page<FavoriteQuestionResponse> listFavorites(UserPrincipal principal, int page, int pageSize) {
        Long userId = requireUserId(principal);
        PageRequest pageRequest = PageRequest.of(
                normalizePage(page),
                normalizePageSize(pageSize),
                Sort.by(Sort.Direction.DESC, "createdAt")
        );
        return favoriteQuestionRepository.findFavoritePage(userId, QuestionStatus.ACTIVE, pageRequest)
                .map(FavoriteQuestionResponse::from);
    }

    @Transactional
    public FavoriteQuestionResponse favoriteQuestion(UserPrincipal principal, Long questionId) {
        Long userId = requireUserId(principal);
        Long normalizedQuestionId = normalizeQuestionId(questionId);
        Question question = findActiveQuestion(normalizedQuestionId);
        FavoriteQuestion favorite = favoriteQuestionRepository.findByUser_IdAndQuestion_Id(userId, normalizedQuestionId)
                .orElseGet(() -> {
                    AppUser user = findUser(userId);
                    return favoriteQuestionRepository.save(FavoriteQuestion.create(user, question));
                });
        return FavoriteQuestionResponse.from(favorite);
    }

    @Transactional
    public void unfavoriteQuestion(UserPrincipal principal, Long questionId) {
        Long userId = requireUserId(principal);
        favoriteQuestionRepository.deleteFavorite(userId, normalizeQuestionId(questionId));
    }

    private WrongQuestionRecord findWrongRecord(UserPrincipal principal, Long questionId) {
        Long userId = requireUserId(principal);
        Long normalizedQuestionId = normalizeQuestionId(questionId);
        return wrongQuestionRecordRepository.findByUser_IdAndQuestion_Id(userId, normalizedQuestionId)
                .orElseThrow(() -> AppException.notFound("错题记录不存在"));
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
