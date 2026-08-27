package edu.flab.chemilog.room;

/**
 * 방 상태. 정상 전이는 HOST_ANSWERING → OPEN 하나뿐이다 (docs/domain.md).
 *
 * HOST_ANSWERING 인 방에는 방장 말고 아무도 들어올 수 없다. 방장이 12개 답변을 제출해야
 * OPEN 이 되고 그때부터 초대 링크가 동작한다 (PRD 5장).
 * 역방향 전이와 그 밖의 상태는 설계에 없다. 필요해 보여도 임의로 추가하지 마라.
 */
public enum RoomStatus {
    HOST_ANSWERING,
    OPEN
}
