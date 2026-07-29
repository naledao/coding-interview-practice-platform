package xyz.kangnasi.interview.practice;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import xyz.kangnasi.interview.question.Question;
import xyz.kangnasi.interview.user.AppUser;

@Entity
@Table(
        name = "favorite_questions",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_favorite_question_user_question",
                columnNames = {"user_id", "question_id"}
        )
)
public class FavoriteQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected FavoriteQuestion() {
    }

    private FavoriteQuestion(AppUser user, Question question) {
        this.user = user;
        this.question = question;
    }

    public static FavoriteQuestion create(AppUser user, Question question) {
        return new FavoriteQuestion(user, question);
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public AppUser getUser() {
        return user;
    }

    public Question getQuestion() {
        return question;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
