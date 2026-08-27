package edu.flab.chemilog.participant;

import edu.flab.chemilog.room.Room;
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
import java.time.LocalDateTime;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

/**
 * 방에 들어온 사람. 방장도 참여자다 (docs/domain.md).
 *
 * 방장이 별도 테이블이 아닌 이유는 방장이 첫 번째 참여자이기 때문이다 (PRD 5장).
 * 어느 참여자가 방장인지는 room.host_participant_id 가 가리킨다.
 */
@Entity
@Table(name = "participant")
public class Participant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @Column(nullable = false, length = 12)
    private String nickname;

    // 중복 판정용. 정규화는 Nickname 이 끝내고 여기에는 결과만 들어온다.
    @Column(name = "nickname_key", nullable = false, length = 48)
    private String nicknameKey;

    // 원본 토큰은 저장하지 않는다. 발급 시 쿠키로 한 번만 내려간다.
    @Column(name = "access_token_hash", nullable = false, length = 32)
    private byte[] accessTokenHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "answer_status", nullable = false)
    private AnswerStatus answerStatus;

    // 값은 스키마의 DEFAULT CURRENT_TIMESTAMP(6) 가 만든다.
    // @Generated 는 INSERT 에서만 컬럼을 뺀다. 답변 제출로 참여자를 갱신할 때
    // created_at 이 SET 절에 실리지 않게 하려면 updatable = false 가 필요하다.
    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * answerStatus 와 짝을 이룬다. 둘을 따로 바꾸면 ck_participant_submitted 가 거절한다.
     * 답변 제출은 다음 이슈라 지금은 값을 넣는 곳이 없다.
     */
    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    protected Participant() {
    }

    private Participant(Room room, Nickname nickname, byte[] accessTokenHash) {
        this.room = room;
        this.nickname = nickname.display();
        this.nicknameKey = nickname.key();
        this.accessTokenHash = accessTokenHash;
        this.answerStatus = AnswerStatus.ANSWERING;
    }

    public static Participant join(Room room, Nickname nickname, byte[] accessTokenHash) {
        return new Participant(room, nickname, accessTokenHash);
    }

    public Long getId() {
        return id;
    }

    public Room getRoom() {
        return room;
    }

    public String getNickname() {
        return nickname;
    }

    public String getNicknameKey() {
        return nicknameKey;
    }

    public AnswerStatus getAnswerStatus() {
        return answerStatus;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getSubmittedAt() {
        return submittedAt;
    }
}
