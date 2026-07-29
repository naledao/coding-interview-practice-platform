package xyz.kangnasi.interview.document;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "knowledge_documents")
public class KnowledgeDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long uploadId;

    @Column(nullable = false, length = 255)
    private String originalFilename;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private UploadType sourceType;

    @Column(length = 255)
    private String archiveOriginalFilename;

    @Column(length = 600)
    private String archiveEntryPath;

    @Column(nullable = false)
    private long fileSize;

    @Column(nullable = false, length = 64)
    private String contentSha256;

    @Column(nullable = false, length = 600)
    private String storedPath;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private KnowledgeDocumentStatus status;

    @Column(nullable = false)
    private Long uploadedById;

    @Column(nullable = false, length = 128)
    private String uploadedByEmail;

    @Column(nullable = false, length = 64)
    private String uploadedByName;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected KnowledgeDocument() {
    }

    private KnowledgeDocument(
            Long uploadId,
            String originalFilename,
            UploadType sourceType,
            String archiveOriginalFilename,
            String archiveEntryPath,
            long fileSize,
            String contentSha256,
            String storedPath,
            Long uploadedById,
            String uploadedByEmail,
            String uploadedByName
    ) {
        this.uploadId = uploadId;
        this.originalFilename = originalFilename;
        this.sourceType = sourceType;
        this.archiveOriginalFilename = archiveOriginalFilename;
        this.archiveEntryPath = archiveEntryPath;
        this.fileSize = fileSize;
        this.contentSha256 = contentSha256;
        this.storedPath = storedPath;
        this.status = KnowledgeDocumentStatus.UPLOADED;
        this.uploadedById = uploadedById;
        this.uploadedByEmail = uploadedByEmail;
        this.uploadedByName = uploadedByName;
    }

    public static KnowledgeDocument create(
            DocumentUpload upload,
            String originalFilename,
            UploadType sourceType,
            String archiveOriginalFilename,
            String archiveEntryPath,
            long fileSize,
            String contentSha256,
            String storedPath
    ) {
        return new KnowledgeDocument(
                upload.getId(),
                originalFilename,
                sourceType,
                archiveOriginalFilename,
                archiveEntryPath,
                fileSize,
                contentSha256,
                storedPath,
                upload.getUploadedById(),
                upload.getUploadedByEmail(),
                upload.getUploadedByName()
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

    public void markProcessing() {
        status = KnowledgeDocumentStatus.PROCESSING;
    }

    public void markProcessed() {
        status = KnowledgeDocumentStatus.PROCESSED;
    }

    public void markFailed() {
        status = KnowledgeDocumentStatus.FAILED;
    }

    public void changeStoredPath(String storedPath) {
        this.storedPath = storedPath;
    }

    public Long getId() {
        return id;
    }

    public Long getUploadId() {
        return uploadId;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public UploadType getSourceType() {
        return sourceType;
    }

    public String getArchiveOriginalFilename() {
        return archiveOriginalFilename;
    }

    public String getArchiveEntryPath() {
        return archiveEntryPath;
    }

    public long getFileSize() {
        return fileSize;
    }

    public String getContentSha256() {
        return contentSha256;
    }

    public String getStoredPath() {
        return storedPath;
    }

    public KnowledgeDocumentStatus getStatus() {
        return status;
    }

    public Long getUploadedById() {
        return uploadedById;
    }

    public String getUploadedByEmail() {
        return uploadedByEmail;
    }

    public String getUploadedByName() {
        return uploadedByName;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
