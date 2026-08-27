package edu.flab.chemilog.room.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 방 생성 요청. 방장의 닉네임만 받는다. 방 이름은 받지 않는다 (위키 UI-MVP 02번 화면).
 *
 * 여기서 검증하는 것은 필드가 왔는지까지다. 길이와 허용 문자는 Nickname 이 판정한다.
 * 규약이 닉네임 규칙 위반에 NICKNAME_INVALID 라는 전용 코드를 두었는데,
 * 애노테이션으로 잡으면 VALIDATION_FAILED 로 나가 프론트가 두 경우를 구분하지 못한다.
 */
public record CreateRoomRequest(@NotNull String nickname) {
}
