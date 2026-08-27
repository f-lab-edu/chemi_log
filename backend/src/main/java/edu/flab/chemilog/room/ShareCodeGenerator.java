package edu.flab.chemilog.room;

import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.stereotype.Component;

/**
 * 방 공유 코드를 만든다. 코드는 추측하기 어려워야 한다 (PRD 7장).
 *
 * 방 접근 수단이 이 코드 하나뿐이라 코드를 맞히면 방에 들어올 수 있다. 자격증명에 준하므로
 * java.util.Random 이 아니라 SecureRandom 을 쓰고 로그에 남기지 않는다
 * (docs/conventions.md 의 Logging 절).
 *
 * 충돌을 재시도로 다루지 않는다. 128비트 난수가 같은 값으로 나올 확률은 uk_room_share_code 가
 * 거절하는 상황을 실무에서 만들지 못하는 수준이고, 재시도를 넣으면 제약 위반으로
 * 롤백된 트랜잭션 안에서 다시 INSERT 하는 잘못된 코드가 된다. 충돌하면 500 으로 나간다.
 */
@Component
public class ShareCodeGenerator {

    // 16바이트를 base64url 로 인코딩하면 패딩 없이 22자다. share_code 는 VARCHAR(32) 라 넉넉하다.
    private static final int CODE_BYTES = 16;

    private final SecureRandom random = new SecureRandom();

    public String generate() {
        byte[] buffer = new byte[CODE_BYTES];
        random.nextBytes(buffer);
        // 코드가 URL 경로에 그대로 들어간다. + 와 / 가 없는 base64url 이어야 인코딩이 필요 없다.
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buffer);
    }
}
