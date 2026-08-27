package edu.flab.chemilog.room;

import edu.flab.chemilog.participant.Participant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

/**
 * 케미방. 공유 코드로 식별한다 (PRD 7장).
 *
 * host 가 nullable 인 것은 방과 방장이 서로를 참조하기 때문이다. 방을 먼저 만들어야
 * 방장 participant 에 room_id 를 넣을 수 있고, 방장 id 가 나와야 방이 그것을 가리킬 수 있다.
 * 그래서 방 생성은 room INSERT → participant INSERT → room UPDATE 순서이며 한 트랜잭션이다
 * (docs/domain.md 의 주요 흐름).
 *
 * 스키마의 fk_room_host_participant 는 (id, host_participant_id) 복합 FK 라,
 * 방장으로 다른 방의 참여자를 넣으면 DB 가 거절한다.
 */
@Entity
@Table(name = "room")
public class Room {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "share_code", nullable = false, length = 32)
    private String shareCode;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "host_participant_id")
    private Participant host;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RoomStatus status;

    /**
     * 값은 스키마의 DEFAULT CURRENT_TIMESTAMP(6) 가 만든다.
     *
     * `@Generated` 는 INSERT 에서만 컬럼을 빼고 값을 다시 읽어 온다. UPDATE 에는 관여하지 않아서
     * `updatable = false` 가 없으면 방을 갱신할 때마다 created_at 이 SET 절에 실린다.
     * 방장 답변 제출로 status 를 OPEN 으로 바꾸는 경로가 이 UPDATE 를 탄다.
     */
    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    protected Room() {
    }

    private Room(String shareCode) {
        this.shareCode = shareCode;
        this.status = RoomStatus.HOST_ANSWERING;
    }

    /** 방장이 아직 없는 상태로 만든다. 방장은 이어서 assignHost 로 붙인다. */
    public static Room openFor(String shareCode) {
        return new Room(shareCode);
    }

    public void assignHost(Participant host) {
        if (this.host != null) {
            throw new IllegalStateException("방장은 한 번만 정할 수 있다. roomId=" + id);
        }
        this.host = host;
    }

    public Long getId() {
        return id;
    }

    public String getShareCode() {
        return shareCode;
    }

    public Participant getHost() {
        return host;
    }

    public RoomStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
