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
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "import_job_logs")
public class ImportJobLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long importJobId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ImportJobLogLevel level;

    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String payload;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected ImportJobLog() {
    }

    private ImportJobLog(Long importJobId, ImportJobLogLevel level, String message, String payload) {
        this.importJobId = importJobId;
        this.level = level;
        this.message = message;
        this.payload = payload;
    }

    public static ImportJobLog create(Long importJobId, ImportJobLogLevel level, String message, String payload) {
        return new ImportJobLog(importJobId, level, message, payload);
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getImportJobId() {
        return importJobId;
    }

    public ImportJobLogLevel getLevel() {
        return level;
    }

    public String getMessage() {
        return message;
    }

    public String getPayload() {
        return payload;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
