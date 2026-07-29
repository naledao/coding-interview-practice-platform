package xyz.kangnasi.interview.practice;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import xyz.kangnasi.interview.question.Question;
import xyz.kangnasi.interview.user.AppUser;

@Entity
@Table(name = "practice_answer_records")
public class PracticeAnswerRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    @Column(name = "selected_option_key", nullable = false, length = 1)
    private String selectedOptionKey;

    @Column(nullable = false)
    private boolean correct;

    @Enumerated(EnumType.STRING)
    @Column(name = "practice_mode", nullable = false, length = 24)
    private PracticeMode practiceMode;

    @Column(name = "time_spent_seconds")
    private Integer timeSpentSeconds;

    @Column(nullable = false, updatable = false)
    private Instant answeredAt;

    protected PracticeAnswerRecord() {
    }

    private PracticeAnswerRecord(
            AppUser user,
            Question question,
            String selectedOptionKey,
            boolean correct,
            PracticeMode practiceMode,
            Integer timeSpentSeconds
    ) {
        this.user = user;
        this.question = question;
        this.selectedOptionKey = selectedOptionKey;
        this.correct = correct;
        this.practiceMode = practiceMode;
        this.timeSpentSeconds = timeSpentSeconds;
    }

    public static PracticeAnswerRecord create(
            AppUser user,
            Question question,
            String selectedOptionKey,
            boolean correct,
            PracticeMode practiceMode,
            Integer timeSpentSeconds
    ) {
        return new PracticeAnswerRecord(user, question, selectedOptionKey, correct, practiceMode, timeSpentSeconds);
    }

    @PrePersist
    void onCreate() {
        answeredAt = Instant.now();
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

    public String getSelectedOptionKey() {
        return selectedOptionKey;
    }

    public boolean isCorrect() {
        return correct;
    }

    public PracticeMode getPracticeMode() {
        return practiceMode;
    }

    public Integer getTimeSpentSeconds() {
        return timeSpentSeconds;
    }

    public Instant getAnsweredAt() {
        return answeredAt;
    }
}
