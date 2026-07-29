package xyz.kangnasi.interview.question;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(
        name = "questions",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_questions_import_job_stem_hash",
                columnNames = {"source_import_job_id", "stem_hash"}
        )
)
public class Question {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private QuestionType type;

    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    private String stem;

    @Column(name = "stem_hash", nullable = false, length = 64)
    private String stemHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private QuestionDifficulty difficulty;

    @Column(nullable = false, length = 255)
    private String knowledgePoint;

    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    private String answerAnalysis;

    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    private String codexReviewSummary;

    @Column(name = "source_document_id", nullable = false)
    private Long sourceDocumentId;

    @Column(name = "source_import_job_id", nullable = false)
    private Long sourceImportJobId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private QuestionStatus status;

    @OneToMany(mappedBy = "question", cascade = jakarta.persistence.CascadeType.ALL, orphanRemoval = true)
    @OrderBy("optionKey ASC")
    private List<QuestionOption> options = new ArrayList<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "question_tags",
            joinColumns = @JoinColumn(name = "question_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id")
    )
    private Set<QuestionTag> tags = new LinkedHashSet<>();

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected Question() {
    }

    private Question(
            String stem,
            String stemHash,
            QuestionDifficulty difficulty,
            String knowledgePoint,
            String answerAnalysis,
            String codexReviewSummary,
            Long sourceDocumentId,
            Long sourceImportJobId
    ) {
        this.type = QuestionType.SINGLE_CHOICE;
        this.stem = stem;
        this.stemHash = stemHash;
        this.difficulty = difficulty;
        this.knowledgePoint = knowledgePoint;
        this.answerAnalysis = answerAnalysis;
        this.codexReviewSummary = codexReviewSummary;
        this.sourceDocumentId = sourceDocumentId;
        this.sourceImportJobId = sourceImportJobId;
        this.status = QuestionStatus.ACTIVE;
    }

    public static Question create(
            String stem,
            String stemHash,
            QuestionDifficulty difficulty,
            String knowledgePoint,
            String answerAnalysis,
            String codexReviewSummary,
            Long sourceDocumentId,
            Long sourceImportJobId
    ) {
        return new Question(
                stem,
                stemHash,
                difficulty,
                knowledgePoint,
                answerAnalysis,
                codexReviewSummary,
                sourceDocumentId,
                sourceImportJobId
        );
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public void addOption(QuestionOption option) {
        options.add(option);
        option.attachTo(this);
    }

    public void addTag(QuestionTag tag) {
        tags.add(tag);
    }

    public void disable() {
        status = QuestionStatus.DISABLED;
    }

    public void enable() {
        status = QuestionStatus.ACTIVE;
    }

    public Long getId() {
        return id;
    }

    public QuestionType getType() {
        return type;
    }

    public String getStem() {
        return stem;
    }

    public String getStemHash() {
        return stemHash;
    }

    public QuestionDifficulty getDifficulty() {
        return difficulty;
    }

    public String getKnowledgePoint() {
        return knowledgePoint;
    }

    public String getAnswerAnalysis() {
        return answerAnalysis;
    }

    public String getCodexReviewSummary() {
        return codexReviewSummary;
    }

    public Long getSourceDocumentId() {
        return sourceDocumentId;
    }

    public Long getSourceImportJobId() {
        return sourceImportJobId;
    }

    public QuestionStatus getStatus() {
        return status;
    }

    public List<QuestionOption> getOptions() {
        return options;
    }

    public Set<QuestionTag> getTags() {
        return tags;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
