package edu.flab.chemilog.room;

/**
 * 방 생성 결과. 서비스가 컨트롤러에 돌려주는 값이다.
 *
 * accessToken 이 응답 DTO 가 아니라 여기에 있는 이유는, 토큰이 바디가 아니라
 * Set-Cookie 헤더로 나가기 때문이다 (위키 API 규약의 인증 절).
 * 바디에 넣으면 자바스크립트가 읽을 수 있게 되어 HttpOnly 를 건 뜻이 없어진다.
 */
public record CreatedRoom(String shareCode, RoomStatus status, String accessToken) {
}
