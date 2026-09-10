package edu.flab.chemilog.room;

import edu.flab.chemilog.participant.Participant;

/**
 * 방 상태. 정상 전이는 HOST_ANSWERING → OPEN 하나뿐입니다 (docs/domain.md).
 *
 * HOST_ANSWERING 인 방에는 방장 말고 아무도 들어올 수 없습니다. 방장이 12개 답변을 제출해야
 * OPEN 이 되고 그때부터 초대 링크가 동작합니다 (PRD 5장).
 */
public enum RoomStatus {
    HOST_ANSWERING,
    OPEN;

    /**
     * status 컬럼을 두지 않은 것은 방장의 submitted_at 과 같은 사실을 두 번 적게 되기
     * 때문입니다. 옛 설계의 answer_status 가 그래서 없어졌습니다 (docs/database.md).
     *
     * 방장 여부는 검사하지 않습니다. isHost = true 를 만드는 곳이 방 생성 하나뿐이라
     * 진입점을 좁혀 지키는 방식입니다 (Participant 참고). 게스트를 넘기면 그 사람의 제출
     * 여부가 방 상태로 나가고 오류는 나지 않습니다.
     */
    public static RoomStatus of(Participant host) {
        return host.hasSubmitted() ? OPEN : HOST_ANSWERING;
    }
}
