package edu.flab.chemilog.room;

import edu.flab.chemilog.room.dto.CreateRoomRequest;
import edu.flab.chemilog.room.dto.CreateRoomResponse;
import jakarta.validation.Valid;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    /** 쿠키 이름은 위키 API 규약이 정한 값입니다. 프론트가 이 이름을 읽지는 않지만 규약이 계약입니다. */
    private static final String PARTICIPANT_TOKEN_COOKIE = "participantToken";

    /**
     * 자격 수명입니다. PRD 는 브라우저 사이트 데이터를 지울 때까지 유지하라고 정했는데
     * (PRD 210행, AC-TOKEN-RESTART), 무기한 쿠키가 없으므로 쓸 수 있는 최댓값을 씁니다.
     *
     * **400 이 상한입니다.** Chrome 계열이 그보다 큰 Max-Age 를 400일로 잘라냅니다.
     * 더 줄이면 그만큼 일찍 자격을 잃고 AC-TOKEN-RESTART 가 깨집니다.
     */
    private static final Duration PARTICIPANT_TOKEN_MAX_AGE = Duration.ofDays(400);

    private final RoomService roomService;

    public RoomController(RoomService roomService) {
        this.roomService = roomService;
    }

    /**
     * 방을 만들고 방장 자격 쿠키를 발급합니다.
     *
     * **produces 를 지우면 Accept 가 JSON 이 아닌 요청에서 방만 만들어지고 응답이 실패합니다.**
     * Spring 이 핸들러를 먼저 실행하고 반환값을 쓸 때 콘텐츠 협상에 실패하기 때문입니다. 그 시점에는 roomService.create() 의
     * 트랜잭션이 이미 커밋돼 방과 방장과 질문 12개가 남는데, 클라이언트는 406 과 빈 본문을 받습니다.
     * 재시도할 때마다 방이 쌓입니다. produces 가 있으면 요청 매핑 단계에서 걸러 서비스가 안 돕니다.
     */
    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CreateRoomResponse> create(@Valid @RequestBody CreateRoomRequest request) {
        CreatedRoom created = roomService.create(request.nickname());

        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.SET_COOKIE,
                        participantTokenCookie(created.accessToken(), created.shareCode()).toString())
                .body(new CreateRoomResponse(created.shareCode(), created.status()));
    }

    /**
     * 참여자 자격 쿠키를 만듭니다.
     *
     * HttpOnly, Secure, SameSite=Lax 와 Max-Age 는 정해져 있습니다. Secure 를 로컬에서도 붙이는 것은
     * 브라우저가 http://localhost 를 신뢰할 수 있는 오리진으로 취급해 Secure 쿠키를 저장하기 때문입니다.
     * 개발용으로만 빼면 배포에서 처음 드러나는 차이가 생깁니다.
     *
     * **Path 가 방마다 다릅니다.** 이름은 규약의 participantToken 하나이고 Path 를
     * /api/rooms/{shareCode} 로 둡니다 (위키 API 규약 인증 절). 쿠키는 이름과 Path 가 함께
     * 식별자라 Path 가 다르면 같은 이름이어도 브라우저가 덮어쓰지 않습니다. Path=/ 로 되돌리면
     * 방 A 를 만든 뒤 방 B 를 만들 때 A 의 자격을 잃어 AC-TOKEN-SCOPE 가 깨집니다.
     *
     * Path 는 브라우저가 쿠키를 고르는 규칙이지 서버의 권한 검사가 아닙니다. 방 소속 확인은
     * 토큰을 읽는 엔드포인트가 요청마다 합니다 (AC-SEC-ROOM-ISOLATION).
     */
    private ResponseCookie participantTokenCookie(String token, String shareCode) {
        return ResponseCookie.from(PARTICIPANT_TOKEN_COOKIE, token)
                .httpOnly(true)
                .secure(true)
                .sameSite("Lax")
                .path("/api/rooms/" + shareCode)
                .maxAge(PARTICIPANT_TOKEN_MAX_AGE)
                .build();
    }
}
