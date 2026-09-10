package edu.flab.chemilog.room.dto;

import edu.flab.chemilog.room.RoomStatus;

/**
 * 방 생성 응답. 규약대로 래퍼 없이 데이터만 내보냅니다.
 *
 * 필드 이름이 프론트의 CreateRoomResponse 와 같아야 합니다
 * (frontend/src/features/room/types.ts). status 는 언제나 HOST_ANSWERING 이지만
 * 프론트가 상수로 적지 않게 서버가 줍니다.
 */
public record CreateRoomResponse(String shareCode, RoomStatus status) {
}
