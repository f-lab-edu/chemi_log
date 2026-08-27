package edu.flab.chemilog.room;

import edu.flab.chemilog.question.Question;
import edu.flab.chemilog.question.QuestionCategory;
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
import jakarta.persistence.Table;

/**
 * 방에 고정된 질문 스냅샷. 관계 테이블이 아니다.
 *
 * sourceQuestionId 는 Question 을 가리키는 연관관계가 아니라 원본 추적용 참고값이다.
 * 매핑을 @ManyToOne 으로 바꾸지 마라. FK 가 없어야 원본 질문을 지울 때 기존 방이 걸리지 않는다
 * (docs/database.md 의 room_question 절). 값을 복사해 두었으므로 원본이 바뀌어도
 * 이미 만들어진 방의 문항은 그대로다 (PRD 8장).
 *
 * 방의 질문을 조회할 때 question 과 조인하지 않는다. 조인하면 스냅샷이 의미를 잃는다.
 */
@Entity
@Table(name = "room_question")
public class RoomQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @Column(name = "source_question_id", nullable = false)
    private Long sourceQuestionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private QuestionCategory category;

    @Column(nullable = false, length = 200)
    private String content;

    @Column(name = "option_a", nullable = false, length = 100)
    private String optionA;

    @Column(name = "option_b", nullable = false, length = 100)
    private String optionB;

    /** 1~12. ck_room_question_order 가 범위를, uk_room_question_order 가 방 안의 중복을 막는다. */
    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    protected RoomQuestion() {
    }

    private RoomQuestion(Room room, Question source, int displayOrder) {
        this.room = room;
        this.sourceQuestionId = source.getId();
        this.category = source.getCategory();
        this.content = source.getContent();
        this.optionA = source.getOptionA();
        this.optionB = source.getOptionB();
        this.displayOrder = displayOrder;
    }

    public static RoomQuestion copyOf(Room room, Question source, int displayOrder) {
        return new RoomQuestion(room, source, displayOrder);
    }

    public Long getId() {
        return id;
    }

    public Room getRoom() {
        return room;
    }

    public Long getSourceQuestionId() {
        return sourceQuestionId;
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

    public int getDisplayOrder() {
        return displayOrder;
    }
}
