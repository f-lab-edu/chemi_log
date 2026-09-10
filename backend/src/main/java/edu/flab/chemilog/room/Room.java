package edu.flab.chemilog.room;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

/**
 * 케미방. 공유 코드로 식별합니다 (PRD 7장).
 *
 * 2026-08-27 ERD 재설계로 host_participant_id 와 status 가 사라졌습니다 (docs/database.md).
 * 방장은 participant.is_host 가 표시하고, 방 상태는 방장의 submitted_at 으로 계산합니다.
 * **status 필드를 다시 넣으면 방장 제출과 방 상태가 어긋나는 경로가 생깁니다.**
 */
@Entity
@Table(name = "room")
public class Room {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "share_code", nullable = false, length = 32)
    private String shareCode;

    /**
     * 값은 스키마의 DEFAULT CURRENT_TIMESTAMP(6) 가 만듭니다.
     *
     * `@Generated` 는 INSERT 에서만 컬럼을 빼고 값을 다시 읽어 옵니다. UPDATE 에는 관여하지 않아서
     * `updatable = false` 가 없으면 방을 갱신할 때마다 created_at 이 SET 절에 실립니다.
     */
    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    protected Room() {
    }

    private Room(String shareCode) {
        this.shareCode = shareCode;
    }

    public static Room withShareCode(String shareCode) {
        return new Room(shareCode);
    }

    public Long getId() {
        return id;
    }

    public String getShareCode() {
        return shareCode;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
