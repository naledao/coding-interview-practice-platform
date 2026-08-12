package xyz.kangnasi.interview.codextool;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.kangnasi.interview.common.AppException;
import xyz.kangnasi.interview.document.KnowledgeDocument;
import xyz.kangnasi.interview.document.KnowledgeDocumentRepository;
import xyz.kangnasi.interview.importjob.ImportJob;
import xyz.kangnasi.interview.importjob.ImportJobLog;
import xyz.kangnasi.interview.importjob.ImportJobLogLevel;
import xyz.kangnasi.interview.importjob.ImportJobLogRepository;
import xyz.kangnasi.interview.importjob.ImportJobRepository;
import xyz.kangnasi.interview.importjob.ImportJobStatus;
import xyz.kangnasi.interview.question.Question;
import xyz.kangnasi.interview.question.QuestionDifficulty;
import xyz.kangnasi.interview.question.QuestionOption;
import xyz.kangnasi.interview.question.QuestionRepository;
import xyz.kangnasi.interview.question.QuestionTag;
import xyz.kangnasi.interview.question.QuestionTagRepository;
import xyz.kangnasi.interview.question.TagCategory;

@Service
public class CodexToolService {

    private static final int MAX_BATCH_SIZE = 50;
    private static final Set<String> REQUIRED_OPTION_KEYS = Set.of("A", "B", "C", "D");
    private static final List<String> RECOMMENDED_TAGS = List.of(
            "Java基础",
            "面向对象",
            "异常处理",
            "Java集合",
            "泛型",
            "JVM",
            "类加载",
            "垃圾回收",
            "Java并发",
            "JMM",
            "线程池",
            "锁",
            "Spring",
            "Spring Boot",
            "MyBatis",
            "MySQL",
            "Redis",
            "网络",
            "设计模式",
            "分布式",
            "Java后端",
            "微服务",
            "Kafka",
            "RabbitMQ",
            "Elasticsearch",
            "Java智能体",
            "Spring AI",
            "LangChain4j",
            "大模型",
            "提示工程",
            "结构化输出",
            "工具调用",
            "MCP",
            "RAG",
            "Embedding",
            "向量数据库",
            "对话记忆",
            "智能体工作流",
            "多智能体",
            "智能体评测",
            "AI安全",
            "AI可观测性"
    );
    private static final Map<String, TagCategory> TAG_CATEGORY_OVERRIDES = Map.ofEntries(
            Map.entry("java基础", TagCategory.JAVA),
            Map.entry("面向对象", TagCategory.JAVA),
            Map.entry("异常处理", TagCategory.JAVA),
            Map.entry("java集合", TagCategory.JAVA),
            Map.entry("泛型", TagCategory.JAVA),
            Map.entry("jvm", TagCategory.JAVA),
            Map.entry("类加载", TagCategory.JAVA),
            Map.entry("垃圾回收", TagCategory.JAVA),
            Map.entry("java并发", TagCategory.JAVA),
            Map.entry("jmm", TagCategory.JAVA),
            Map.entry("线程池", TagCategory.JAVA),
            Map.entry("锁", TagCategory.JAVA),
            Map.entry("spring", TagCategory.FRAMEWORK),
            Map.entry("spring boot", TagCategory.FRAMEWORK),
            Map.entry("mybatis", TagCategory.FRAMEWORK),
            Map.entry("mysql", TagCategory.DATABASE),
            Map.entry("redis", TagCategory.MIDDLEWARE),
            Map.entry("网络", TagCategory.NETWORK),
            Map.entry("设计模式", TagCategory.DESIGN),
            Map.entry("分布式", TagCategory.OTHER),
            Map.entry("java后端", TagCategory.JAVA),
            Map.entry("微服务", TagCategory.OTHER),
            Map.entry("kafka", TagCategory.MIDDLEWARE),
            Map.entry("rabbitmq", TagCategory.MIDDLEWARE),
            Map.entry("elasticsearch", TagCategory.MIDDLEWARE),
            Map.entry("java智能体", TagCategory.AI),
            Map.entry("spring ai", TagCategory.AI),
            Map.entry("langchain4j", TagCategory.AI),
            Map.entry("大模型", TagCategory.AI),
            Map.entry("提示工程", TagCategory.AI),
            Map.entry("结构化输出", TagCategory.AI),
            Map.entry("工具调用", TagCategory.AI),
            Map.entry("mcp", TagCategory.AI),
            Map.entry("rag", TagCategory.AI),
            Map.entry("embedding", TagCategory.AI),
            Map.entry("向量数据库", TagCategory.AI),
            Map.entry("对话记忆", TagCategory.AI),
            Map.entry("智能体工作流", TagCategory.AI),
            Map.entry("多智能体", TagCategory.AI),
            Map.entry("智能体评测", TagCategory.AI),
            Map.entry("ai安全", TagCategory.AI),
            Map.entry("ai可观测性", TagCategory.AI)
    );

    private final ImportJobRepository importJobRepository;
    private final ImportJobLogRepository importJobLogRepository;
    private final KnowledgeDocumentRepository documentRepository;
    private final QuestionRepository questionRepository;
    private final QuestionTagRepository tagRepository;
    private final ObjectMapper objectMapper;
    private final long maxDocumentBytes;

    public CodexToolService(
            ImportJobRepository importJobRepository,
            ImportJobLogRepository importJobLogRepository,
            KnowledgeDocumentRepository documentRepository,
            QuestionRepository questionRepository,
            QuestionTagRepository tagRepository,
            ObjectMapper objectMapper,
            @Value("${app.upload.max-markdown-bytes:10485760}") long maxDocumentBytes
    ) {
        this.importJobRepository = importJobRepository;
        this.importJobLogRepository = importJobLogRepository;
        this.documentRepository = documentRepository;
        this.questionRepository = questionRepository;
        this.tagRepository = tagRepository;
        this.objectMapper = objectMapper;
        this.maxDocumentBytes = maxDocumentBytes;
    }

    @Transactional(readOnly = true)
    public GetImportJobToolResponse getImportJob(GetImportJobToolRequest request) {
        return GetImportJobToolResponse.from(findJob(requiredId(request.importJobId(), "importJobId 不能为空")));
    }

    @Transactional(readOnly = true)
    public GetGeneratedQuestionCountToolResponse getGeneratedQuestionCount(GetGeneratedQuestionCountToolRequest request) {
        Long importJobId = requiredId(request.importJobId(), "importJobId 不能为空");
        if (!importJobRepository.existsById(importJobId)) {
            throw AppException.notFound("导入任务不存在");
        }
        return new GetGeneratedQuestionCountToolResponse(importJobId, questionRepository.countBySourceImportJobId(importJobId));
    }

    @Transactional(readOnly = true)
    public GetRecommendedTagsToolResponse getRecommendedTags() {
        Map<String, QuestionTag> existingTags = new LinkedHashMap<>();
        tagRepository.findAll().forEach(tag -> existingTags.put(tag.getNormalizedName(), tag));
        List<RecommendedTagToolResponse> tags = RECOMMENDED_TAGS.stream()
                .map(tagName -> {
                    String normalizedName = normalizeTag(tagName);
                    QuestionTag existingTag = existingTags.get(normalizedName);
                    if (existingTag != null) {
                        return new RecommendedTagToolResponse(
                                existingTag.getId(),
                                existingTag.getName(),
                                existingTag.getNormalizedName(),
                                existingTag.getCategory(),
                                existingTag.getQuestionCount()
                        );
                    }
                    return new RecommendedTagToolResponse(
                            null,
                            tagName,
                            normalizedName,
                            inferCategory(normalizedName),
                            0
                    );
                })
                .toList();
        return new GetRecommendedTagsToolResponse(tags);
    }

    @Transactional(readOnly = true)
    public ReadImportDocumentToolResponse readImportDocument(ReadImportDocumentToolRequest request) {
        ImportJob job = findJob(requiredId(request.importJobId(), "importJobId 不能为空"));
        KnowledgeDocument document = findDocument(job.getDocumentId());
        Path path = Path.of(document.getStoredPath()).toAbsolutePath().normalize();

        try {
            long size = Files.size(path);
            if (size > maxDocumentBytes) {
                throw AppException.badRequest("文档超过可读取大小限制");
            }
            String content = Files.readString(path, StandardCharsets.UTF_8);
            return new ReadImportDocumentToolResponse(job.getId(), document.getId(), document.getOriginalFilename(), content);
        } catch (IOException exception) {
            throw AppException.notFound("导入文档无法读取");
        }
    }

    @Transactional
    public CodexToolOkResponse appendGenerationLog(AppendGenerationLogToolRequest request) {
        Long importJobId = requiredId(request.importJobId(), "importJobId 不能为空");
        if (!importJobRepository.existsById(importJobId)) {
            throw AppException.notFound("导入任务不存在");
        }
        ImportJobLogLevel level = request.level() == null ? ImportJobLogLevel.INFO : request.level();
        String message = requireText(request.message(), "日志内容不能为空", 1000);
        importJobLogRepository.save(ImportJobLog.create(importJobId, level, message, payloadToJson(request.payload())));
        return CodexToolOkResponse.success();
    }

    @Transactional
    public CodexToolOkResponse markImportJobRunning(MarkImportJobRunningToolRequest request) {
        ImportJob job = findJob(requiredId(request.importJobId(), "importJobId 不能为空"));
        KnowledgeDocument document = findDocument(job.getDocumentId());
        if (job.getStatus() != ImportJobStatus.PENDING && job.getStatus() != ImportJobStatus.RUNNING) {
            throw AppException.conflict("导入任务当前状态不能标记为运行中");
        }
        job.markRunning();
        document.markProcessing();
        importJobLogRepository.save(ImportJobLog.create(job.getId(), ImportJobLogLevel.INFO, "Codex 已标记任务运行中", null));
        return CodexToolOkResponse.success();
    }

    @Transactional
    public CodexToolOkResponse markImportJobSucceeded(MarkImportJobSucceededToolRequest request) {
        ImportJob job = findJob(requiredId(request.importJobId(), "importJobId 不能为空"));
        KnowledgeDocument document = findDocument(job.getDocumentId());
        int generatedQuestionCount = request.generatedQuestionCount() == null
                ? questionRepository.countBySourceImportJobId(job.getId())
                : Math.max(request.generatedQuestionCount(), questionRepository.countBySourceImportJobId(job.getId()));

        job.markSucceeded(generatedQuestionCount);
        document.markProcessed();
        importJobLogRepository.save(ImportJobLog.create(
                job.getId(),
                ImportJobLogLevel.INFO,
                blankToDefault(request.summary(), "Codex 已标记任务成功"),
                null
        ));
        return CodexToolOkResponse.success();
    }

    @Transactional
    public CodexToolOkResponse markImportJobFailed(MarkImportJobFailedToolRequest request) {
        ImportJob job = findJob(requiredId(request.importJobId(), "importJobId 不能为空"));
        KnowledgeDocument document = findDocument(job.getDocumentId());
        String reason = requireText(request.reason(), "失败原因不能为空", 1000);
        job.markFailed(reason);
        document.markFailed();
        importJobLogRepository.save(ImportJobLog.create(job.getId(), ImportJobLogLevel.ERROR, reason, null));
        return CodexToolOkResponse.success();
    }

    @Transactional(readOnly = true)
    public ValidateQuestionBatchToolResponse validateQuestionBatch(CreateQuestionBatchToolRequest request) {
        ImportJob job = findJob(requiredId(request.importJobId(), "importJobId 不能为空"));
        List<CodexQuestionPayload> payloads = validatePayloadList(request.questions());

        List<String> errors = new ArrayList<>();
        int skippedCount = 0;
        int validCount = 0;
        Set<String> stemsInBatch = new LinkedHashSet<>();

        for (int index = 0; index < payloads.size(); index++) {
            CodexQuestionPayload payload = payloads.get(index);
            try {
                String normalizedStem = normalizeStem(requireText(payload.stem(), "题干不能为空", 4000));
                String stemHash = sha256(normalizedStem);
                validateQuestionPayload(payload);
                if (!stemsInBatch.add(stemHash) || questionRepository.existsBySourceImportJobIdAndStemHash(job.getId(), stemHash)) {
                    skippedCount++;
                    continue;
                }
                validCount++;
            } catch (IllegalArgumentException exception) {
                errors.add("questions[" + index + "]: " + exception.getMessage());
            }
        }

        return new ValidateQuestionBatchToolResponse(errors.isEmpty(), validCount, skippedCount, errors);
    }

    @Transactional
    public CreateQuestionBatchToolResponse createQuestionBatch(CreateQuestionBatchToolRequest request) {
        ImportJob job = findJob(requiredId(request.importJobId(), "importJobId 不能为空"));
        if (job.getStatus() != ImportJobStatus.RUNNING) {
            throw AppException.conflict("导入任务不是运行中状态");
        }
        List<CodexQuestionPayload> payloads = validatePayloadList(request.questions());

        List<Long> createdQuestionIds = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        int skippedCount = 0;
        Set<String> stemsInBatch = new LinkedHashSet<>();

        for (int index = 0; index < payloads.size(); index++) {
            CodexQuestionPayload payload = payloads.get(index);
            try {
                String normalizedStem = normalizeStem(requireText(payload.stem(), "题干不能为空", 4000));
                String stemHash = sha256(normalizedStem);
                if (!stemsInBatch.add(stemHash) || questionRepository.existsBySourceImportJobIdAndStemHash(job.getId(), stemHash)) {
                    skippedCount++;
                    continue;
                }
                Question question = buildQuestion(job, payload, normalizedStem, stemHash);
                questionRepository.save(question);
                createdQuestionIds.add(question.getId());
            } catch (IllegalArgumentException | DataIntegrityViolationException exception) {
                errors.add("questions[" + index + "]: " + exception.getMessage());
            }
        }

        job.updateGeneratedQuestionCount(questionRepository.countBySourceImportJobId(job.getId()));
        importJobLogRepository.save(ImportJobLog.create(
                job.getId(),
                errors.isEmpty() ? ImportJobLogLevel.INFO : ImportJobLogLevel.WARN,
                "批量写入题目：成功 " + createdQuestionIds.size() + "，跳过 " + skippedCount + "，错误 " + errors.size(),
                null
        ));

        return new CreateQuestionBatchToolResponse(
                errors.isEmpty(),
                createdQuestionIds,
                createdQuestionIds.size(),
                skippedCount,
                errors
        );
    }

    private List<CodexQuestionPayload> validatePayloadList(List<CodexQuestionPayload> payloads) {
        List<CodexQuestionPayload> checkedPayloads = payloads == null ? List.of() : payloads;
        if (checkedPayloads.isEmpty()) {
            throw AppException.badRequest("题目列表不能为空");
        }
        if (checkedPayloads.size() > MAX_BATCH_SIZE) {
            throw AppException.badRequest("每批题目数量不能超过 " + MAX_BATCH_SIZE);
        }
        return checkedPayloads;
    }

    private Question buildQuestion(ImportJob job, CodexQuestionPayload payload, String normalizedStem, String stemHash) {
        QuestionDifficulty difficulty = parseDifficulty(payload.difficulty());
        String knowledgePoint = requireText(payload.knowledgePoint(), "知识点不能为空", 255);
        String answerAnalysis = requireText(payload.answerAnalysis(), "答案解析不能为空", 4000);
        String reviewSummary = requireText(payload.codexReviewSummary(), "Codex review 摘要不能为空", 2000);
        List<CodexQuestionOptionPayload> options = validateOptions(payload.options());
        List<QuestionTag> tags = validateTags(payload.tags());

        Question question = Question.create(
                normalizedStem,
                stemHash,
                difficulty,
                knowledgePoint,
                answerAnalysis,
                reviewSummary,
                job.getDocumentId(),
                job.getId()
        );
        for (CodexQuestionOptionPayload optionPayload : options) {
            question.addOption(QuestionOption.create(
                    optionPayload.optionKey().trim().toUpperCase(Locale.ROOT),
                    requireText(optionPayload.content(), "选项内容不能为空", 2000),
                    optionPayload.correct()
            ));
        }
        for (QuestionTag tag : tags) {
            tag.incrementQuestionCount();
            question.addTag(tag);
        }
        return question;
    }

    private void validateQuestionPayload(CodexQuestionPayload payload) {
        parseDifficulty(payload.difficulty());
        requireText(payload.knowledgePoint(), "知识点不能为空", 255);
        requireText(payload.answerAnalysis(), "答案解析不能为空", 4000);
        requireText(payload.codexReviewSummary(), "Codex review 摘要不能为空", 2000);
        validateOptions(payload.options());
        validateTagNames(payload.tags());
    }

    private List<CodexQuestionOptionPayload> validateOptions(List<CodexQuestionOptionPayload> options) {
        if (options == null || options.size() != 4) {
            throw new IllegalArgumentException("每题必须恰好有 4 个选项");
        }

        Set<String> optionKeys = new LinkedHashSet<>();
        int correctCount = 0;
        for (CodexQuestionOptionPayload option : options) {
            String key = requireText(option.optionKey(), "选项 key 不能为空", 1).toUpperCase(Locale.ROOT);
            if (!REQUIRED_OPTION_KEYS.contains(key)) {
                throw new IllegalArgumentException("选项 key 必须是 A/B/C/D");
            }
            if (!optionKeys.add(key)) {
                throw new IllegalArgumentException("选项 key 不能重复");
            }
            requireText(option.content(), "选项内容不能为空", 2000);
            if (option.correct()) {
                correctCount++;
            }
        }
        if (!optionKeys.equals(REQUIRED_OPTION_KEYS)) {
            throw new IllegalArgumentException("选项必须完整包含 A/B/C/D");
        }
        if (correctCount != 1) {
            throw new IllegalArgumentException("每题必须且只能有 1 个正确选项");
        }
        return options.stream()
                .sorted(java.util.Comparator.comparing(option -> option.optionKey().trim().toUpperCase(Locale.ROOT)))
                .toList();
    }

    private List<QuestionTag> validateTags(List<String> tagNames) {
        Map<String, String> normalizedToDisplayName = validateTagNames(tagNames);
        return normalizedToDisplayName.entrySet().stream()
                .map(entry -> tagRepository.findByNormalizedName(entry.getKey())
                        .orElseGet(() -> tagRepository.save(QuestionTag.create(entry.getValue(), entry.getKey(), inferCategory(entry.getKey())))))
                .toList();
    }

    private Map<String, String> validateTagNames(List<String> tagNames) {
        if (tagNames == null || tagNames.isEmpty()) {
            throw new IllegalArgumentException("至少需要 1 个标签");
        }

        Map<String, String> normalizedToDisplayName = new LinkedHashMap<>();
        for (String rawTag : tagNames) {
            String tagName = requireText(rawTag, "标签不能为空", 64);
            String normalizedName = normalizeTag(tagName);
            if (normalizedName.isBlank() || normalizedToDisplayName.containsKey(normalizedName)) {
                continue;
            }
            normalizedToDisplayName.put(normalizedName, tagName);
        }

        if (normalizedToDisplayName.isEmpty()) {
            throw new IllegalArgumentException("至少需要 1 个标签");
        }
        if (normalizedToDisplayName.size() > 5) {
            throw new IllegalArgumentException("每题标签不能超过 5 个");
        }
        return normalizedToDisplayName;
    }

    private QuestionDifficulty parseDifficulty(String rawDifficulty) {
        String value = requireText(rawDifficulty, "难度不能为空", 16).toUpperCase(Locale.ROOT);
        try {
            return QuestionDifficulty.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("难度必须是 EASY/MEDIUM/HARD");
        }
    }

    private TagCategory inferCategory(String normalizedName) {
        return TAG_CATEGORY_OVERRIDES.getOrDefault(normalizedName, TagCategory.OTHER);
    }

    private ImportJob findJob(Long importJobId) {
        return importJobRepository.findById(importJobId)
                .orElseThrow(() -> AppException.notFound("导入任务不存在"));
    }

    private KnowledgeDocument findDocument(Long documentId) {
        return documentRepository.findById(documentId)
                .orElseThrow(() -> AppException.notFound("知识文档不存在"));
    }

    private Long requiredId(Long value, String message) {
        if (value == null || value < 1) {
            throw AppException.badRequest(message);
        }
        return value;
    }

    private String requireText(String value, String message, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw new IllegalArgumentException(message + "，长度不能超过 " + maxLength);
        }
        return trimmed;
    }

    private String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String normalizeStem(String stem) {
        return stem.replaceAll("\\s+", " ").trim();
    }

    private String normalizeTag(String tag) {
        return Normalizer.normalize(tag, Normalizer.Form.NFKC)
                .trim()
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);
    }

    private String payloadToJson(JsonNode payload) {
        if (payload == null || payload.isNull()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            return payload.toString();
        }
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 不可用", exception);
        }
    }
}
