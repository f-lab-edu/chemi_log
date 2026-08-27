package edu.flab.chemilog.room.dto;

import edu.flab.chemilog.room.RoomStatus;

/**
 * 방 생성 응답. 규약대로 데이터를 그대로 내보내고 래퍼로 감싸지 않는다.
 *
 * 방 생성 직후 상태는 언제나 HOST_ANSWERING 이지만 필드로 내보낸다.
 * 프론트가 상태를 상수로 굳히지 않고 서버가 준 값으로 화면을 정하게 하려는 것이다.
 * 프론트의 CreateRoomResponse 와 필드 이름이 같아야 한다 (frontend/src/features/room/types.ts).
 */
public record CreateRoomResponse(String shareCode, RoomStatus status) {
}
