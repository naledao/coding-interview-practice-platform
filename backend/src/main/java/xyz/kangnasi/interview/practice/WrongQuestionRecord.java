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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import xyz.kangnasi.interview.question.Question;
import xyz.kangnasi.interview.user.AppUser;

@Entity
@Table(
        name = "wrong_question_records",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_wrong_question_user_question",
                columnNames = {"user_id", "question_id"}
        )
)
public class WrongQuestionRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    @Column(nullable = false)
    private int wrongCount;

    @Column(nullable = false)
    private int correctAfterWrongCount;

    @Column(nullable = false)
    private boolean mastered;

    @Column(nullable = false)
    private Instant lastWrongAt;

    @Column(nullable = false)
    private Instant lastAnsweredAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected WrongQuestionRecord() {
    }

    private WrongQuestionRecord(AppUser user, Question question, Instant answeredAt) {
        this.user = user;
        this.question = question;
        this.wrongCount = 1;
        this.correctAfterWrongCount = 0;
        this.mastered = false;
        this.lastWrongAt = answeredAt;
        this.lastAnsweredAt = answeredAt;
    }

    public static WrongQuestionRecord create(AppUser user, Question question, Instant answeredAt) {
        return new WrongQuestionRecord(user, question, answeredAt);
    }

    public void markWrong(Instant answeredAt) {
        wrongCount++;
        mastered = false;
        lastWrongAt = answeredAt;
        lastAnsweredAt = answeredAt;
    }

    public void markCorrectAfterWrong(Instant answeredAt) {
        correctAfterWrongCount++;
        lastAnsweredAt = answeredAt;
    }

    public void markMastered() {
        mastered = true;
    }

    public void unmarkMastered() {
        mastered = false;
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

    public Long getId() {
        return id;
    }

    public AppUser getUser() {
        return user;
    }

    public Question getQuestion() {
        return question;
    }

    public int getWrongCount() {
        return wrongCount;
    }

    public int getCorrectAfterWrongCount() {
        return correctAfterWrongCount;
    }

    public boolean isMastered() {
        return mastered;
    }

    public Instant getLastWrongAt() {
        return lastWrongAt;
    }

    public Instant getLastAnsweredAt() {
        return lastAnsweredAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
