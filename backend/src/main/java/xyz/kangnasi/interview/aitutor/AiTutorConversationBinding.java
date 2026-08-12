package xyz.kangnasi.interview.aitutor;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
        name = "ai_tutor_conversation_bindings",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_ai_tutor_conversation_id",
                columnNames = "conversation_id"
        )
)
public class AiTutorConversationBinding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "conversation_id", nullable = false, length = 36)
    private String conversationId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_active_at", nullable = false)
    private Instant lastActiveAt;

    protected AiTutorConversationBinding() {
    }

    private AiTutorConversationBinding(Long userId, String conversationId) {
        this.userId = userId;
        this.conversationId = conversationId;
    }

    public static AiTutorConversationBinding create(Long userId, String conversationId) {
        return new AiTutorConversationBinding(userId, conversationId);
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        lastActiveAt = now;
    }

    @PreUpdate
    void onUpdate() {
        lastActiveAt = Instant.now();
    }

    public Long getUserId() {
        return userId;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void touch() {
        lastActiveAt = Instant.now();
    }
}
