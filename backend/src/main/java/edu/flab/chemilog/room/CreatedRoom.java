package edu.flab.chemilog.room;

/**
 * 방 생성 결과. 서비스가 컨트롤러에 돌려주는 값입니다.
 *
 * accessToken 이 응답 DTO 에 없는 것은 이 값이 Set-Cookie 헤더로만 나가기 때문입니다
 * (위키 API 규약의 인증 절). 바디에 넣으면 자바스크립트가 읽어 HttpOnly 가 무의미해집니다.
 */
public record CreatedRoom(String shareCode, RoomStatus status, String accessToken) {
}
