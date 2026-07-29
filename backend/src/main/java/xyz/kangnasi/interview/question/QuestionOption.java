package xyz.kangnasi.interview.question;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "question_options",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_question_options_question_key",
                columnNames = {"question_id", "option_key"}
        )
)
public class QuestionOption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    @Column(name = "option_key", nullable = false, length = 1)
    private String optionKey;

    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false)
    private boolean correct;

    protected QuestionOption() {
    }

    private QuestionOption(String optionKey, String content, boolean correct) {
        this.optionKey = optionKey;
        this.content = content;
        this.correct = correct;
    }

    public static QuestionOption create(String optionKey, String content, boolean correct) {
        return new QuestionOption(optionKey, content, correct);
    }

    void attachTo(Question question) {
        this.question = question;
    }

    public Long getId() {
        return id;
    }

    public Question getQuestion() {
        return question;
    }

    public String getOptionKey() {
        return optionKey;
    }

    public String getContent() {
        return content;
    }

    public boolean isCorrect() {
        return correct;
    }
}
