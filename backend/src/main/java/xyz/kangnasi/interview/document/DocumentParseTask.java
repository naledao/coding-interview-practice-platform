package xyz.kangnasi.interview.document;

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

@Entity
@Table(name = "document_parse_tasks")
public class DocumentParseTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long uploadId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private DocumentParseTaskStatus status;

    @Column(nullable = false)
    private boolean autoStartImport;

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

    protected DocumentParseTask() {
    }

    private DocumentParseTask(Long uploadId, boolean autoStartImport) {
        this.uploadId = uploadId;
        this.status = DocumentParseTaskStatus.PENDING;
        this.autoStartImport = autoStartImport;
    }

    public static DocumentParseTask create(Long uploadId, boolean autoStartImport) {
        return new DocumentParseTask(uploadId, autoStartImport);
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
        status = DocumentParseTaskStatus.RUNNING;
        startedAt = Instant.now();
        failedReason = null;
    }

    public void markSucceeded() {
        status = DocumentParseTaskStatus.SUCCEEDED;
        finishedAt = Instant.now();
    }

    public void markFailed(String failedReason) {
        status = DocumentParseTaskStatus.FAILED;
        this.failedReason = failedReason;
        finishedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getUploadId() {
        return uploadId;
    }

    public DocumentParseTaskStatus getStatus() {
        return status;
    }

    public boolean isAutoStartImport() {
        return autoStartImport;
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
