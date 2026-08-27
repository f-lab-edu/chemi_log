package edu.flab.chemilog.participant;

import edu.flab.chemilog.common.ApiErrorCode;
import edu.flab.chemilog.common.ApiException;
import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 닉네임의 표시값과 중복 판정 키.
 *
 * 두 값을 나누는 이유는 participant 테이블이 nickname 과 nickname_key 를 따로 갖기 때문이다.
 * 화면에는 사용자가 입력한 모양을 보여 주고, 같은 방 안의 중복은 정규화한 키로 판정한다.
 *
 * 정규화를 DB collation 에 맡기지 않는다. nickname_key 의 collation 은
 * utf8mb4_0900_as_cs 라 accent 와 대소문자를 모두 구분한다. 서버 기본 collation 에 기대면
 * cafe 와 cafe' 가 같은 닉네임이 되는데, PRD 7장은 이 둘을 다른 닉네임으로 요구한다.
 *
 * 소문자화는 반드시 Locale.ROOT 로 한다. 인자를 빼면 터키어 로케일에서 I 가 소문자 i 가
 * 아니라 U+0131 로 바뀌어, 서버의 기본 로케일에 따라 중복 판정 결과가 달라진다.
 */
public record Nickname(String display, String key) {

    public static final int MIN_LENGTH = 1;
    public static final int MAX_LENGTH = 12;

    /**
     * 연속 공백을 1칸으로 줄일 때 쓴다 (PRD 7장).
     *
     * `\s` 를 쓰지 않는다. Java 의 `\s` 는 `[ \t\n\x0B\f\r]` 라서 두 방향으로 틀린다.
     * 보이는 모양이 같은 U+00A0(NBSP)나 U+3000 을 접지 못해 화면에서 구분되지 않는
     * 닉네임 두 개가 서로 다른 `nickname_key` 를 갖게 되고, 반대로 줄바꿈과 탭은
     * 공백으로 바꿔 버려 아래 FORBIDDEN 검사가 잡아야 할 제어 문자를 조용히 지운다.
     *
     * 프론트의 `normalizeNickname` 과 같은 규칙이다 (frontend/src/features/room/nickname.ts).
     */
    private static final Pattern SPACE_SEPARATOR_RUN = Pattern.compile("\\p{Zs}+");

    /** 위 규칙으로 접은 뒤 앞뒤에 남은 공백. `strip()` 은 `\p{Zs}` 를 다 지우지 못한다. */
    private static final Pattern SURROUNDING_SPACE = Pattern.compile("^ +| +$");

    /**
     * 쓸 수 없는 문자.
     *
     * `\p{Cntrl}` 을 쓰지 않는다. Java 에서 그것은 `[\x00-\x1F\x7F]` 라 ASCII 범위만 본다.
     * U+0085(NEL)나 U+009F 같은 제어 문자가 그대로 통과해 DB 에 저장된다.
     *
     * `Cc` 는 제어 문자, `Cf` 는 포맷 문자다. `Cf` 를 함께 막는 이유는 U+202E 같은 양방향
     * 재정의 문자가 닉네임을 다른 이름처럼 보이게 만들 수 있기 때문이다. 닉네임이 방 안에서
     * 사람을 구분하는 유일한 수단이라 그대로 두면 안 된다.
     * `Zl`(U+2028)과 `Zp`(U+2029)는 `Zs` 가 아니라 위에서 접히지 않으면서 줄바꿈으로 렌더링된다.
     * `<` 와 `>` 는 PRD 7장이 마크업을 허용하지 않는다고 정해서 막는다.
     *
     * 프론트의 `FORBIDDEN_PATTERN` 과 같은 규칙이다. 서버가 더 느슨하면 브라우저를 거치지 않는
     * 요청이 그대로 통과한다 (docs/domain.md 의 "서버는 프론트를 믿지 않는다").
     */
    private static final Pattern FORBIDDEN_CHARACTER =
            Pattern.compile("[\\p{Cc}\\p{Cf}\\p{Zl}\\p{Zp}<>]");

    /**
     * 사용자 입력을 표시값과 키로 바꾼다.
     *
     * 프론트의 `normalizeNickname` + `validateNickname` 이 하는 일을 서버에서 다시 한 뒤,
     * 중복 판정용 키까지 만든다. 키 생성은 서버만 한다.
     *
     * 순서가 결과를 바꾼다. 공백을 접기 전에 금지 문자를 보면 `"지은\n"` 이 거절되고,
     * 접은 뒤에 보면 `\n` 이 이미 공백으로 바뀌어 통과한다. 프론트는 접기를 `\p{Zs}` 로만
     * 하므로 `\n` 이 살아남아 거절되는 쪽이다. 서버도 같은 결과를 내야 한다.
     *
     * 길이는 표시값을 코드포인트로 센다. `String.length()` 는 이모지를 2로 세는데
     * `nickname VARCHAR(12)` 는 문자 수로 세므로 둘이 어긋난다.
     *
     * @param raw 요청 바디의 닉네임. null 일 수 있다
     * @throws ApiException 규칙에 어긋나면 {@link ApiErrorCode#NICKNAME_INVALID}
     */
    public static Nickname of(String raw) {
        if (raw == null) {
            throw invalid("값이 없다");
        }

        // 접는 것은 Zs 뿐이다. 줄바꿈과 탭은 여기서 살아남아 아래 금지 문자 검사에 걸린다.
        String folded = SPACE_SEPARATOR_RUN.matcher(raw).replaceAll(" ");
        String display = SURROUNDING_SPACE.matcher(folded).replaceAll("");

        if (display.isEmpty()) {
            throw invalid("공백만 입력됐다");
        }
        // 길이보다 먼저 본다. 쓸 수 없는 문자를 지우면 길이도 줄지만 그 반대는 아니다.
        if (FORBIDDEN_CHARACTER.matcher(display).find()) {
            throw invalid("허용하지 않는 문자가 있다");
        }

        int length = display.codePointCount(0, display.length());
        if (length < MIN_LENGTH || length > MAX_LENGTH) {
            throw invalid("길이가 " + length + "자다");
        }

        // NFC 를 먼저 하고 소문자화한다. PRD 7장이 정한 순서다.
        // 소문자화가 문자 수를 늘릴 수 있어서 nickname_key 를 VARCHAR(48) 로 넓혀 뒀다.
        String key = Normalizer.normalize(display, Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
        return new Nickname(display, key);
    }

    private static ApiException invalid(String reason) {
        return new ApiException(ApiErrorCode.NICKNAME_INVALID, "닉네임 규칙 위반: " + reason);
    }
}
