package xyz.kangnasi.interview.importjob;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import xyz.kangnasi.interview.document.KnowledgeDocument;

@Entity
@Table(name = "import_jobs")
public class ImportJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long documentId;

    @Column(nullable = false, length = 255)
    private String documentName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ImportJobStatus status;

    @Column(nullable = false)
    private int generatedQuestionCount;

    @Column(length = 128)
    private String codexSessionId;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String failedReason;

    @Column
    private Instant startedAt;

    @Column
    private Instant finishedAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected ImportJob() {
    }

    private ImportJob(KnowledgeDocument document) {
        this.documentId = document.getId();
        this.documentName = document.getOriginalFilename();
        this.status = ImportJobStatus.PENDING;
        this.generatedQuestionCount = 0;
    }

    public static ImportJob create(KnowledgeDocument document) {
        return new ImportJob(document);
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

    public void markRunning() {
        status = ImportJobStatus.RUNNING;
        startedAt = Instant.now();
        failedReason = null;
    }

    public void markSucceeded(int generatedQuestionCount) {
        status = ImportJobStatus.SUCCEEDED;
        this.generatedQuestionCount = generatedQuestionCount;
        finishedAt = Instant.now();
    }

    public void updateGeneratedQuestionCount(int generatedQuestionCount) {
        this.generatedQuestionCount = generatedQuestionCount;
    }

    public void markFailed(String failedReason) {
        status = ImportJobStatus.FAILED;
        this.failedReason = failedReason;
        finishedAt = Instant.now();
    }

    public void changeCodexSessionId(String codexSessionId) {
        this.codexSessionId = codexSessionId;
    }

    public Long getId() {
        return id;
    }

    public Long getDocumentId() {
        return documentId;
    }

    public String getDocumentName() {
        return documentName;
    }

    public ImportJobStatus getStatus() {
        return status;
    }

    public int getGeneratedQuestionCount() {
        return generatedQuestionCount;
    }

    public String getCodexSessionId() {
        return codexSessionId;
    }

    public String getFailedReason() {
        return failedReason;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
