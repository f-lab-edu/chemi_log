package edu.flab.chemilog.room;

import edu.flab.chemilog.room.dto.CreateRoomRequest;
import edu.flab.chemilog.room.dto.CreateRoomResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    /** 쿠키 이름은 위키 API 규약이 정한 값이다. 프론트가 이 이름을 읽지는 않지만 규약이 계약이다. */
    private static final String PARTICIPANT_TOKEN_COOKIE = "participantToken";

    private final RoomService roomService;

    public RoomController(RoomService roomService) {
        this.roomService = roomService;
    }

    @PostMapping
    public ResponseEntity<CreateRoomResponse> create(@Valid @RequestBody CreateRoomRequest request) {
        CreatedRoom created = roomService.create(request.nickname());

        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.SET_COOKIE, participantTokenCookie(created.accessToken()).toString())
                .body(new CreateRoomResponse(created.shareCode(), created.status()));
    }

    /**
     * 규약이 정한 속성 그대로다. HttpOnly, Secure, SameSite=Lax, Path=/.
     *
     * Secure 를 로컬에서도 붙이는 것은 브라우저가 http://localhost 를 신뢰할 수 있는 오리진으로
     * 취급해 Secure 쿠키를 저장하기 때문이다. 개발용으로만 빼면 배포에서 처음 드러나는 차이가 생긴다.
     *
     * Max-Age 를 붙이지 않아 브라우저를 닫으면 사라지는 세션 쿠키다. 규약의 Set-Cookie 예시에
     * Max-Age 가 없어 그대로 따랐다. 재접속 가능 기간은 제품 결정이라 규약이 정해야 한다.
     * 지금 상태로는 브라우저를 껐다 켠 참여자가 자기 방으로 돌아오지 못한다.
     */
    private ResponseCookie participantTokenCookie(String token) {
        return ResponseCookie.from(PARTICIPANT_TOKEN_COOKIE, token)
                .httpOnly(true)
                .secure(true)
                .sameSite("Lax")
                .path("/")
                .build();
    }
}
