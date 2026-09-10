package edu.flab.chemilog.participant;

import edu.flab.chemilog.room.Room;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * 방에 들어온 사람. 방장도 참여자입니다 (PRD 5장, docs/domain.md).
 *
 * 어느 참여자가 방장인지는 이 행의 isHost 가 표시합니다. 옛 설계는 room.host_participant_id 가
 * 가리켰고, 그 때문에 방과 참여자가 서로를 참조하는 순환과 복합 FK 가 생겼습니다.
 * 2026-08-27 재설계로 둘 다 사라졌습니다 (docs/database.md).
 *
 * **"방마다 방장은 한 명" 을 DB 가 강제하지 않습니다.** 빠뜨린 것이 아니라 결정입니다.
 * isHost = true 를 만드는 곳이 방 생성 한 군데뿐이고, 링크로 들어오는 참여자는 항상
 * false 로 만들어지므로 두 번째 방장이 생길 경로가 없습니다.
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

    // 중복 판정용. 정규화는 Nickname 이 끝내고 여기에는 결과만 들어옵니다.
    @Column(name = "nickname_key", nullable = false, length = 48)
    private String nicknameKey;

    // 원본 토큰은 저장하지 않습니다. 발급 시 쿠키로 한 번만 내려갑니다.
    @Column(name = "access_token_hash", nullable = false, length = 32)
    private byte[] accessTokenHash;

    @Column(name = "is_host", nullable = false)
    private boolean host;

    // 값은 스키마의 DEFAULT CURRENT_TIMESTAMP(6) 가 만듭니다.
    // @Generated 는 INSERT 에서만 컬럼을 뺍니다. 답변 제출로 참여자를 갱신할 때
    // created_at 이 SET 절에 실리지 않게 하려면 updatable = false 가 필요합니다.
    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * 제출 여부를 이 컬럼 하나로 판단합니다. NULL 이면 아직 답변 중입니다.
     * 방이 OPEN 인지도 방장 행의 이 값으로 정해집니다 (docs/domain.md).
     *
     * 옛 설계의 answer_status 는 같은 사실을 두 번 적어 둔 컬럼이라 없앴습니다.
     * 되살리면 두 컬럼을 항상 함께 갱신해야 하는 제약이 다시 생깁니다.
     *
     * 채울 때는 NOW(6) 를 씁니다. 앱이 LocalDateTime.now() 로 채우면 DB 가 DEFAULT 로 만드는
     * created_at 과 기준이 달라집니다. DATETIME 은 시간대를 담지 않으므로 JVM 이 UTC 인
     * 컨테이너에서는 9시간 이르게 저장되고 DB 는 아무 오류도 내지 않습니다.
     * 답변 제출은 다음 이슈라 지금은 값을 넣는 곳이 없습니다.
     */
    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    protected Participant() {
    }

    private Participant(Room room, Nickname nickname, byte[] accessTokenHash, boolean host) {
        this.room = room;
        this.nickname = nickname.display();
        this.nicknameKey = nickname.key();
        this.accessTokenHash = accessTokenHash;
        this.host = host;
    }

    /** 방을 만든 사람. 이 저장소에서 isHost = true 를 만드는 곳은 여기 하나뿐입니다. */
    public static Participant host(Room room, Nickname nickname, byte[] accessTokenHash) {
        return new Participant(room, nickname, accessTokenHash, true);
    }

    /** 초대 링크로 들어온 사람. 참여는 다음 이슈라 아직 부르는 곳이 없습니다. */
    public static Participant guest(Room room, Nickname nickname, byte[] accessTokenHash) {
        return new Participant(room, nickname, accessTokenHash, false);
    }

    public boolean hasSubmitted() {
        return submittedAt != null;
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

    public boolean isHost() {
        return host;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getSubmittedAt() {
        return submittedAt;
    }
}
