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
import jakarta.persistence.Table;
import java.time.Instant;
import xyz.kangnasi.interview.user.AppUser;

@Entity
@Table(name = "document_uploads")
public class DocumentUpload {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private UploadType uploadType;

    @Column(nullable = false, length = 255)
    private String originalFilename;

    @Column(nullable = false)
    private long fileSize;

    @Column(nullable = false, length = 64)
    private String uploadSha256;

    @Column(nullable = false, length = 600)
    private String storedPath;

    @Column(nullable = false)
    private Long uploadedById;

    @Column(nullable = false, length = 128)
    private String uploadedByEmail;

    @Column(nullable = false, length = 64)
    private String uploadedByName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private UploadParseStatus parseStatus;

    @Column(nullable = false)
    private int documentCount;

    @Column(nullable = false)
    private int ignoredFileCount;

    @Column(nullable = false)
    private int skippedFileCount;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String skippedFilesJson;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected DocumentUpload() {
    }

    private DocumentUpload(
            UploadType uploadType,
            String originalFilename,
            long fileSize,
            String uploadSha256,
            String storedPath,
            AppUser uploadedBy,
            UploadParseStatus parseStatus
    ) {
        this.uploadType = uploadType;
        this.originalFilename = originalFilename;
        this.fileSize = fileSize;
        this.uploadSha256 = uploadSha256;
        this.storedPath = storedPath;
        this.uploadedById = uploadedBy.getId();
        this.uploadedByEmail = uploadedBy.getEmail();
        this.uploadedByName = uploadedBy.getNickname();
        this.parseStatus = parseStatus;
    }

    public static DocumentUpload create(
            UploadType uploadType,
            String originalFilename,
            long fileSize,
            String uploadSha256,
            String storedPath,
            AppUser uploadedBy,
            UploadParseStatus parseStatus
    ) {
        return new DocumentUpload(uploadType, originalFilename, fileSize, uploadSha256, storedPath, uploadedBy, parseStatus);
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public void markParsed(int documentCount, int ignoredFileCount, int skippedFileCount, String skippedFilesJson) {
        this.parseStatus = UploadParseStatus.PARSED;
        this.documentCount = documentCount;
        this.ignoredFileCount = ignoredFileCount;
        this.skippedFileCount = skippedFileCount;
        this.skippedFilesJson = skippedFilesJson;
    }

    public void markParsing() {
        this.parseStatus = UploadParseStatus.PARSING;
    }

    public void markFailed(int ignoredFileCount, int skippedFileCount, String skippedFilesJson) {
        this.parseStatus = UploadParseStatus.FAILED;
        this.documentCount = 0;
        this.ignoredFileCount = ignoredFileCount;
        this.skippedFileCount = skippedFileCount;
        this.skippedFilesJson = skippedFilesJson;
    }

    public void changeStoredPath(String storedPath) {
        this.storedPath = storedPath;
    }

    public Long getId() {
        return id;
    }

    public UploadType getUploadType() {
        return uploadType;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public long getFileSize() {
        return fileSize;
    }

    public String getUploadSha256() {
        return uploadSha256;
    }

    public String getStoredPath() {
        return storedPath;
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

    public UploadParseStatus getParseStatus() {
        return parseStatus;
    }

    public int getDocumentCount() {
        return documentCount;
    }

    public int getIgnoredFileCount() {
        return ignoredFileCount;
    }

    public int getSkippedFileCount() {
        return skippedFileCount;
    }

    public String getSkippedFilesJson() {
        return skippedFilesJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
