package xyz.kangnasi.interview.question;

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
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

@Entity
@Table(
        name = "tags",
        uniqueConstraints = @UniqueConstraint(name = "uk_tags_normalized_name", columnNames = "normalized_name")
)
public class QuestionTag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String name;

    @Column(name = "normalized_name", nullable = false, length = 64)
    private String normalizedName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private TagCategory category;

    @Column(nullable = false)
    private int questionCount;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected QuestionTag() {
    }

    private QuestionTag(String name, String normalizedName, TagCategory category) {
        this.name = name;
        this.normalizedName = normalizedName;
        this.category = category;
        this.questionCount = 0;
    }

    public static QuestionTag create(String name, String normalizedName, TagCategory category) {
        return new QuestionTag(name, normalizedName, category);
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

    public void incrementQuestionCount() {
        questionCount++;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getNormalizedName() {
        return normalizedName;
    }

    public TagCategory getCategory() {
        return category;
    }

    public int getQuestionCount() {
        return questionCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
