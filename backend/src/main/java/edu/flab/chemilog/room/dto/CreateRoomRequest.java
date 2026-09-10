package edu.flab.chemilog.room.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 방 생성 요청. 방 이름은 받지 않습니다 (위키 UI-MVP 02번 화면).
 *
 * 길이와 허용 문자에 @Size 나 @Pattern 을 붙이지 않습니다. 그러면 VALIDATION_FAILED 로
 * 나가는데 규약은 닉네임 위반에 NICKNAME_INVALID 를 따로 두었습니다. 판정은 Nickname 이 합니다.
 */
public record CreateRoomRequest(@NotNull String nickname) {
}
