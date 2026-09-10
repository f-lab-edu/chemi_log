package edu.flab.chemilog.room;

import edu.flab.chemilog.question.Question;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * 방에 고정된 질문 12개 중 하나.
 *
 * **질문 값을 복사하지 않고 question 을 참조합니다.** 2026-08-27 재설계로 뒤집힌 부분입니다
 * (docs/database.md). 옛 설계는 문구를 복사해 두고 "question 과 조인하지 마라" 는 규칙으로
 * 스냅샷을 지켰는데, 그 규칙은 DB 로 강제할 수 없어서 누가 조인 한 줄을 쓰면 조용히 깨졌습니다.
 *
 * 참조만 해도 방의 질문이 고정되는 근거는 **원본이 불변**이라는 데 있습니다. question 의
 * category, content, option_a, option_b 는 한 번 넣으면 고치지 않고, 문구를 바꾸려면 새 행을
 * 넣고 옛 행을 active = FALSE 로 내립니다 (docs/domain.md). 그래서 조인해도 옛 방에 새 문구가
 * 붙지 않습니다. 값 복사로 되돌리면 위의 조인 문제가 다시 생깁니다.
 *
 * 표시 순서 컬럼이 없습니다. 순서는 id 오름차순이고, 방을 만들 때 12개를 무작위로 섞어
 * INSERT 해서 그 순서를 만듭니다. 조회할 때 ORDER BY id 를 빠뜨리면 순서가 보장되지 않습니다.
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

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    protected RoomQuestion() {
    }

    private RoomQuestion(Room room, Question question) {
        this.room = room;
        this.question = question;
    }

    public static RoomQuestion of(Room room, Question question) {
        return new RoomQuestion(room, question);
    }

    public Long getId() {
        return id;
    }

    public Room getRoom() {
        return room;
    }

    public Question getQuestion() {
        return question;
    }
}
