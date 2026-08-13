package xyz.kangnasi.interview.practice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.kangnasi.interview.auth.UserPrincipal;
import xyz.kangnasi.interview.common.AppException;
import xyz.kangnasi.interview.question.AnswerQuestionRequest;
import xyz.kangnasi.interview.question.AnswerQuestionResponse;
import xyz.kangnasi.interview.question.Question;
import xyz.kangnasi.interview.question.QuestionDifficulty;
import xyz.kangnasi.interview.question.QuestionOption;
import xyz.kangnasi.interview.question.QuestionRepository;
import xyz.kangnasi.interview.question.QuestionResponse;
import xyz.kangnasi.interview.question.QuestionStatus;
import xyz.kangnasi.interview.user.AppUser;
import xyz.kangnasi.interview.user.UserRepository;

@Service
public class PracticeService {

    private static final PageRequest SINGLE_RESULT = PageRequest.of(0, 1);
    private static final List<Long> NO_TAG_FILTER_VALUES = List.of(-1L);

    private final QuestionRepository questionRepository;
    private final PracticeAnswerRecordRepository answerRecordRepository;
    private final WrongQuestionRecordRepository wrongQuestionRecordRepository;
    private final FavoriteQuestionRepository favoriteQuestionRepository;
    private final UserRepository userRepository;

    public PracticeService(
            QuestionRepository questionRepository,
            PracticeAnswerRecordRepository answerRecordRepository,
            WrongQuestionRecordRepository wrongQuestionRecordRepository,
            FavoriteQuestionRepository favoriteQuestionRepository,
            UserRepository userRepository
    ) {
        this.questionRepository = questionRepository;
        this.answerRecordRepository = answerRecordRepository;
        this.wrongQuestionRecordRepository = wrongQuestionRecordRepository;
        this.favoriteQuestionRepository = favoriteQuestionRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public Page<AnsweredQuestionResponse> listAnsweredQuestions(UserPrincipal principal, int page, int pageSize) {
        Long userId = requireUserId(principal);
        PageRequest pageRequest = PageRequest.of(normalizePage(page), normalizePageSize(pageSize));
        Page<Object[]> aggregatePage = answerRecordRepository.findAnsweredQuestionAggregates(
                userId,
                QuestionStatus.ACTIVE,
                pageRequest
        );
        List<AnsweredQuestionAggregate> aggregates = aggregatePage.getContent().stream()
                .map(this::toAnsweredQuestionAggregate)
                .toList();
        List<Long> questionIds = aggregates.stream()
                .map(AnsweredQuestionAggregate::questionId)
                .toList();
        if (questionIds.isEmpty()) {
            return new PageImpl<>(List.of(), pageRequest, aggregatePage.getTotalElements());
        }

        Map<Long, Question> questionById = questionRepository.findActiveQuestionsWithTagsByIdIn(QuestionStatus.ACTIVE, questionIds)
                .stream()
                .collect(Collectors.toMap(Question::getId, Function.identity()));
        Map<Long, AnsweredQuestionLatestRecord> latestRecordByQuestionId = answerRecordRepository
                .findLatestRecordRowsByUserIdAndQuestionIds(userId, questionIds)
                .stream()
                .map(this::toAnsweredQuestionLatestRecord)
                .collect(Collectors.toMap(
                        AnsweredQuestionLatestRecord::questionId,
                        Function.identity(),
                        (existing, ignored) -> existing,
                        LinkedHashMap::new
                ));
        Set<Long> favoriteQuestionIds = favoriteQuestionRepository.findFavoriteQuestionIds(userId, questionIds);

        List<AnsweredQuestionResponse> responses = aggregates.stream()
                .map(aggregate -> {
                    Question question = questionById.get(aggregate.questionId());
                    if (question == null) {
                        return null;
                    }
                    return AnsweredQuestionResponse.from(
                            question,
                            aggregate,
                            latestRecordByQuestionId.get(aggregate.questionId()),
                            favoriteQuestionIds.contains(aggregate.questionId())
                    );
                })
                .filter(Objects::nonNull)
                .toList();
        return new PageImpl<>(responses, pageRequest, aggregatePage.getTotalElements());
    }

    private AnsweredQuestionAggregate toAnsweredQuestionAggregate(Object[] row) {
        return new AnsweredQuestionAggregate(
                (Long) row[0],
                ((Number) row[1]).longValue(),
                ((Number) row[2]).longValue(),
                (Instant) row[3]
        );
    }

    private AnsweredQuestionLatestRecord toAnsweredQuestionLatestRecord(Object[] row) {
        return new AnsweredQuestionLatestRecord(
                (Long) row[0],
                (String) row[1],
                (Boolean) row[2],
                (Instant) row[3]
        );
    }

    @Transactional(readOnly = true)
    public long countQuestions(
            UserPrincipal principal,
            String modeText,
            QuestionDifficulty difficulty,
            List<Long> tagIds,
            String keyword,
            boolean excludeAnswered
    ) {
        Long userId = requireUserId(principal);
        PracticeMode mode = normalizeMode(modeText);
        List<Long> normalizedTagIds = normalizeTagIds(tagIds);
        String normalizedKeyword = normalizeKeyword(keyword);

        if (mode == PracticeMode.TAG && normalizedTagIds.isEmpty()) {
            throw AppException.badRequest("请选择标签");
        }

        return countQuestionIds(
                userId,
                mode,
                difficulty,
                queryTagIds(normalizedTagIds),
                !normalizedTagIds.isEmpty(),
                normalizedKeyword,
                excludeAnswered,
                null
        );
    }

    @Transactional(readOnly = true)
    public QuestionResponse nextQuestion(
            UserPrincipal principal,
            String modeText,
            QuestionDifficulty difficulty,
            List<Long> tagIds,
            String keyword,
            boolean excludeAnswered,
            Long currentQuestionId
    ) {
        Long userId = requireUserId(principal);
        PracticeMode mode = normalizeMode(modeText);
        List<Long> normalizedTagIds = normalizeTagIds(tagIds);
        String normalizedKeyword = normalizeKeyword(keyword);

        if (mode == PracticeMode.TAG && normalizedTagIds.isEmpty()) {
            throw AppException.badRequest("请选择标签");
        }

        Long nextQuestionId = findNextQuestionId(
                userId,
                mode,
                difficulty,
                queryTagIds(normalizedTagIds),
                !normalizedTagIds.isEmpty(),
                normalizedKeyword,
                excludeAnswered,
                currentQuestionId
        );
        if (nextQuestionId == null) {
            return null;
        }

        Question question = findActiveQuestion(nextQuestionId);
        boolean answered = answerRecordRepository.existsByUser_IdAndQuestion_Id(userId, question.getId());
        boolean favorite = favoriteQuestionRepository.existsByUser_IdAndQuestion_Id(userId, question.getId());
        return QuestionResponse.from(question, false, favorite, answered);
    }

    @Transactional
    public AnswerQuestionResponse answerQuestion(UserPrincipal principal, Long questionId, AnswerQuestionRequest request) {
        Long userId = requireUserId(principal);
        AppUser user = findUser(userId);
        Question question = findActiveQuestion(questionId);
        String selectedOptionKey = normalizeOptionKey(request == null ? null : request.selectedOptionKey());
        PracticeMode mode = normalizeMode(request == null ? null : request.mode());
        Integer timeSpentSeconds = normalizeTimeSpentSeconds(request == null ? null : request.timeSpentSeconds());

        QuestionOption selectedOption = question.getOptions().stream()
                .filter(option -> option.getOptionKey().equals(selectedOptionKey))
                .findFirst()
                .orElseThrow(() -> AppException.badRequest("选项无效"));
        String correctOptionKey = question.getOptions().stream()
                .filter(QuestionOption::isCorrect)
                .map(QuestionOption::getOptionKey)
                .findFirst()
                .orElseThrow(() -> AppException.conflict("题目没有正确答案"));

        PracticeAnswerRecord record = answerRecordRepository.save(PracticeAnswerRecord.create(
                user,
                question,
                selectedOption.getOptionKey(),
                selectedOption.isCorrect(),
                mode,
                timeSpentSeconds
        ));

        boolean wrongBookUpdated = updateWrongBook(userId, user, question, selectedOption.isCorrect(), record.getAnsweredAt());

        return new AnswerQuestionResponse(
                question.getId(),
                selectedOption.getOptionKey(),
                selectedOption.isCorrect(),
                correctOptionKey,
                question.getAnswerAnalysis(),
                wrongBookUpdated,
                record.getId()
        );
    }

    private Long findNextQuestionId(
            Long userId,
            PracticeMode mode,
            QuestionDifficulty difficulty,
            List<Long> tagIds,
            boolean filterByTags,
            String keyword,
            boolean excludeAnswered,
            Long currentQuestionId
    ) {
        if (mode == PracticeMode.RANDOM) {
            return findRandomQuestionId(userId, difficulty, tagIds, filterByTags, keyword, excludeAnswered, currentQuestionId);
        }
        Long nextQuestionId = findSequentialQuestionId(userId, mode, difficulty, tagIds, filterByTags, keyword, excludeAnswered, currentQuestionId);
        if (nextQuestionId != null || currentQuestionId == null) {
            return nextQuestionId;
        }
        return findSequentialQuestionId(userId, mode, difficulty, tagIds, filterByTags, keyword, excludeAnswered, null);
    }

    private Long findSequentialQuestionId(
            Long userId,
            PracticeMode mode,
            QuestionDifficulty difficulty,
            List<Long> tagIds,
            boolean filterByTags,
            String keyword,
            boolean excludeAnswered,
            Long currentQuestionId
    ) {
        if (mode == PracticeMode.WRONG) {
            return firstOrNull(wrongQuestionRecordRepository.findNextUnmasteredPracticeQuestionIds(
                    userId,
                    QuestionStatus.ACTIVE,
                    difficulty,
                    tagIds,
                    filterByTags,
                    keyword,
                    excludeAnswered,
                    currentQuestionId,
                    SINGLE_RESULT
            ));
        }
        if (mode == PracticeMode.FAVORITE) {
            return firstOrNull(favoriteQuestionRepository.findNextFavoritePracticeQuestionIds(
                    userId,
                    QuestionStatus.ACTIVE,
                    difficulty,
                    tagIds,
                    filterByTags,
                    keyword,
                    excludeAnswered,
                    currentQuestionId,
                    SINGLE_RESULT
            ));
        }
        return firstOrNull(questionRepository.findNextPracticeQuestionIds(
                QuestionStatus.ACTIVE,
                difficulty,
                tagIds,
                filterByTags,
                keyword,
                excludeAnswered,
                userId,
                currentQuestionId,
                SINGLE_RESULT
        ));
    }

    private Long findRandomQuestionId(
            Long userId,
            QuestionDifficulty difficulty,
            List<Long> tagIds,
            boolean filterByTags,
            String keyword,
            boolean excludeAnswered,
            Long currentQuestionId
    ) {
        Long excludedQuestionId = currentQuestionId == null ? null : currentQuestionId;
        long candidateCount = countQuestionIds(userId, PracticeMode.RANDOM, difficulty, tagIds, filterByTags, keyword, excludeAnswered, excludedQuestionId);
        if (candidateCount == 0 && excludedQuestionId != null) {
            excludedQuestionId = null;
            candidateCount = countQuestionIds(userId, PracticeMode.RANDOM, difficulty, tagIds, filterByTags, keyword, excludeAnswered, null);
        }
        if (candidateCount == 0) {
            return null;
        }
        int offset = ThreadLocalRandom.current().nextInt(Math.toIntExact(candidateCount));
        return firstOrNull(findQuestionIds(userId, PracticeMode.RANDOM, difficulty, tagIds, filterByTags, keyword, excludeAnswered, excludedQuestionId, PageRequest.of(offset, 1)));
    }

    private long countQuestionIds(
            Long userId,
            PracticeMode mode,
            QuestionDifficulty difficulty,
            List<Long> tagIds,
            boolean filterByTags,
            String keyword,
            boolean excludeAnswered,
            Long excludedQuestionId
    ) {
        if (mode == PracticeMode.WRONG) {
            return wrongQuestionRecordRepository.countUnmasteredPracticeQuestionIds(
                    userId,
                    QuestionStatus.ACTIVE,
                    difficulty,
                    tagIds,
                    filterByTags,
                    keyword,
                    excludeAnswered,
                    excludedQuestionId
            );
        }
        if (mode == PracticeMode.FAVORITE) {
            return favoriteQuestionRepository.countFavoritePracticeQuestionIds(
                    userId,
                    QuestionStatus.ACTIVE,
                    difficulty,
                    tagIds,
                    filterByTags,
                    keyword,
                    excludeAnswered,
                    excludedQuestionId
            );
        }
        return questionRepository.countPracticeQuestionIds(
                QuestionStatus.ACTIVE,
                difficulty,
                tagIds,
                filterByTags,
                keyword,
                excludeAnswered,
                userId,
                excludedQuestionId
        );
    }

    private List<Long> findQuestionIds(
            Long userId,
            PracticeMode mode,
            QuestionDifficulty difficulty,
            List<Long> tagIds,
            boolean filterByTags,
            String keyword,
            boolean excludeAnswered,
            Long excludedQuestionId,
            PageRequest pageRequest
    ) {
        if (mode == PracticeMode.WRONG) {
            return wrongQuestionRecordRepository.findUnmasteredPracticeQuestionIds(
                    userId,
                    QuestionStatus.ACTIVE,
                    difficulty,
                    tagIds,
                    filterByTags,
                    keyword,
                    excludeAnswered,
                    excludedQuestionId,
                    pageRequest
            );
        }
        if (mode == PracticeMode.FAVORITE) {
            return favoriteQuestionRepository.findFavoritePracticeQuestionIds(
                    userId,
                    QuestionStatus.ACTIVE,
                    difficulty,
                    tagIds,
                    filterByTags,
                    keyword,
                    excludeAnswered,
                    excludedQuestionId,
                    pageRequest
            );
        }
        return questionRepository.findPracticeQuestionIds(
                QuestionStatus.ACTIVE,
                difficulty,
                tagIds,
                filterByTags,
                keyword,
                excludeAnswered,
                userId,
                excludedQuestionId,
                pageRequest
        );
    }

    private Long firstOrNull(List<Long> questionIds) {
        return questionIds.isEmpty() ? null : questionIds.getFirst();
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

    private boolean updateWrongBook(Long userId, AppUser user, Question question, boolean correct, Instant answeredAt) {
        if (!correct) {
            WrongQuestionRecord wrongRecord = wrongQuestionRecordRepository.findByUser_IdAndQuestion_Id(userId, question.getId())
                    .orElseGet(() -> wrongQuestionRecordRepository.save(WrongQuestionRecord.create(user, question, answeredAt)));
            if (wrongRecord.getWrongCount() > 1 || !wrongRecord.getLastWrongAt().equals(answeredAt)) {
                wrongRecord.markWrong(answeredAt);
            }
            return true;
        }

        wrongQuestionRecordRepository.findByUser_IdAndQuestion_Id(userId, question.getId())
                .ifPresent(wrongRecord -> wrongRecord.markCorrectAfterWrong(answeredAt));
        return false;
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

    private PracticeMode normalizeMode(String modeText) {
        if (modeText == null || modeText.isBlank()) {
            return PracticeMode.SEQUENTIAL;
        }
        String normalized = modeText.trim().toUpperCase(Locale.ROOT);
        if ("ORDER".equals(normalized)) {
            return PracticeMode.SEQUENTIAL;
        }
        try {
            return PracticeMode.valueOf(normalized);
        } catch (IllegalArgumentException exception) {
            throw AppException.badRequest("刷题模式无效");
        }
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

    private List<Long> normalizeTagIds(List<Long> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) {
            return List.of();
        }
        return tagIds.stream()
                .map(tagId -> normalizeOptionalId(tagId, "标签参数错误"))
                .distinct()
                .toList();
    }

    private List<Long> queryTagIds(List<Long> tagIds) {
        return tagIds.isEmpty() ? NO_TAG_FILTER_VALUES : tagIds;
    }

    private String normalizeKeyword(String keyword) {
        return keyword == null || keyword.isBlank() ? null : keyword.trim();
    }

    private String normalizeOptionKey(String optionKey) {
        if (optionKey == null || optionKey.isBlank()) {
            throw AppException.badRequest("请选择一个答案");
        }
        String normalized = optionKey.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("A", "B", "C", "D").contains(normalized)) {
            throw AppException.badRequest("选项无效");
        }
        return normalized;
    }

    private Integer normalizeTimeSpentSeconds(Integer timeSpentSeconds) {
        if (timeSpentSeconds == null) {
            return null;
        }
        if (timeSpentSeconds < 0 || timeSpentSeconds > 86_400) {
            throw AppException.badRequest("答题耗时无效");
        }
        return timeSpentSeconds;
    }
}
