package edu.flab.chemilog.question;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

/**
 * 원본 질문 Pool. 방과 무관하게 존재한다.
 *
 * 애플리케이션은 이 테이블을 읽기만 한다. 질문 추가·비활성화는 시드 SQL 로 한다.
 * 방 생성 시 값을 RoomQuestion 으로 복사하며, 복사 후에는 원본이 바뀌어도 방은 영향받지 않는다
 * (docs/domain.md 의 RoomQuestion 절).
 */
@Entity
@Table(name = "question")
public class Question {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private QuestionCategory category;

    @Column(nullable = false, length = 200)
    private String content;

    @Column(name = "option_a", nullable = false, length = 100)
    private String optionA;

    @Column(name = "option_b", nullable = false, length = 100)
    private String optionB;

    @Column(nullable = false)
    private boolean active;

    // 값은 스키마의 DEFAULT CURRENT_TIMESTAMP(6) 가 만든다. JPA 가 쓰지 않는다.
    // @Generated 는 INSERT 에서만 컬럼을 뺀다. UPDATE 까지 막으려면 updatable = false 가 필요하다.
    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    protected Question() {
    }

    public Long getId() {
        return id;
    }

    public QuestionCategory getCategory() {
        return category;
    }

    public String getContent() {
        return content;
    }

    public String getOptionA() {
        return optionA;
    }

    public String getOptionB() {
        return optionB;
    }

    public boolean isActive() {
        return active;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
