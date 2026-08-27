package edu.flab.chemilog.participant;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.stereotype.Component;

/**
 * 참여자 재접속 토큰을 만들고 해시한다.
 *
 * 원본은 발급 시 쿠키로 한 번만 내려가고 저장하지 않는다. DB 에는 SHA-256 해시만 둔다
 * (docs/domain.md). DB 가 유출돼도 남의 참여자로 재접속할 수 없게 하려는 것이다.
 *
 * 토큰은 자격증명이다. 원본을 로그에 남기지 마라 (docs/conventions.md 의 Logging 절).
 */
@Component
public class AccessTokenGenerator {

    /** participant.access_token_hash 가 BINARY(32) 인 것이 SHA-256 의 출력 길이다. */
    private static final String HASH_ALGORITHM = "SHA-256";

    // 32바이트 난수는 base64url 로 43자다. 추측 불가 요구(PRD 7장)를 넘고도 남는다.
    private static final int TOKEN_BYTES = 32;

    private final SecureRandom random = new SecureRandom();

    public String generate() {
        byte[] buffer = new byte[TOKEN_BYTES];
        random.nextBytes(buffer);
        // 쿠키 값으로 그대로 나가므로 URL-safe 알파벳을 쓴다. 패딩 = 는 뺀다.
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buffer);
    }

    public byte[] hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
            return digest.digest(token.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 은 모든 JVM 구현이 제공해야 하는 알고리즘이라 여기 오면 실행 환경이 깨진 것이다.
            throw new IllegalStateException(HASH_ALGORITHM + " 을 쓸 수 없는 JVM 이다.", e);
        }
    }
}
