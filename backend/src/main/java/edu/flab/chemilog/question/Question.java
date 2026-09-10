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
 * 원본 질문 Pool. 방과 무관하게 존재합니다.
 *
 * 애플리케이션은 이 테이블을 읽기만 합니다. 질문 추가·비활성화는 시드 SQL 로 합니다.
 *
 * **이 행들은 불변입니다.** content, option_a, option_b 를 UPDATE 하면 이미 만들어진 방의
 * 질문이 바뀝니다. room_question 이 값을 복사하지 않고 이 행을 참조만 하기 때문입니다
 * (2026-08-27 재설계).
 *
 * 그러면 먼저 답한 사람과 나중에 답한 사람이 서로 다른 질문에 답하게 됩니다. 점수 계산은
 * 그것을 모른 채 choice 만 보고 일치를 셉니다. 오류도 안 나고 화면도 정상으로 보입니다.
 *
 * category 는 문구를 바꾸지 않습니다. 대신 카테고리 점수의 분류가 소급해 달라집니다.
 *
 * 문구를 바꾸려면 새 행을 넣고 옛 행을 active = FALSE 로 내립니다. 내려도 행은 남으므로
 * 그 질문을 쓰던 방은 그대로 보입니다. **DB 는 이 전제를 강제하지 않습니다.**
 * 근거는 docs/database.md 의 "질문은 불변입니다" 이고 임의 수정 금지 대상입니다.
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

    // 값은 스키마의 DEFAULT CURRENT_TIMESTAMP(6) 가 만듭니다. JPA 가 쓰지 않습니다.
    // @Generated 는 INSERT 에서만 컬럼을 뺍니다. UPDATE 까지 막으려면 updatable = false 가 필요합니다.
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
