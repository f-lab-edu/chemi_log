package edu.flab.chemilog.participant;

import edu.flab.chemilog.common.ApiErrorCode;
import edu.flab.chemilog.common.ApiException;
import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 닉네임의 표시값과 중복 판정 키.
 *
 * participant 테이블이 nickname 과 nickname_key 를 따로 가지므로 여기서도 둘로 나눕니다.
 * 화면에는 사용자가 입력한 모양을 보여 주고, 같은 방 안의 중복은 정규화한 키로 판정합니다.
 *
 * 정규화를 DB collation 에 맡기지 않습니다. nickname_key 의 collation 은
 * utf8mb4_0900_as_cs 라 accent 와 대소문자를 모두 구분합니다. 서버 기본 collation 에 기대면
 * cafe 와 cafe' 가 같은 닉네임이 되는데, PRD 7장은 이 둘을 다른 닉네임으로 요구합니다.
 *
 * 소문자화는 반드시 Locale.ROOT 로 합니다. 인자를 빼면 터키어 로케일에서 I 가 소문자 i 가
 * 아니라 U+0131 로 바뀌어, 서버의 기본 로케일에 따라 중복 판정 결과가 달라집니다.
 */
public record Nickname(String display, String key) {

    public static final int MIN_LENGTH = 1;
    public static final int MAX_LENGTH = 12;

    /**
     * 연속 공백을 1칸으로 줄일 때 씁니다 (PRD 7장).
     *
     * `\s` 를 쓰지 않습니다. Java 의 `\s` 는 `[ \t\n\x0B\f\r]` 라서 두 방향으로 틀립니다.
     * 보이는 모양이 같은 U+00A0(NBSP)나 U+3000 을 접지 못해 화면에서 구분되지 않는
     * 닉네임 두 개가 서로 다른 `nickname_key` 를 갖게 되고, 반대로 줄바꿈과 탭은
     * 공백으로 바꿔 버려 아래 FORBIDDEN 검사가 잡아야 할 제어 문자를 조용히 지웁니다.
     *
     * 프론트의 `normalizeNickname` 과 같은 규칙입니다 (frontend/src/features/room/nickname.ts).
     */
    private static final Pattern SPACE_SEPARATOR_RUN = Pattern.compile("\\p{Zs}+");

    /** 위 규칙으로 접은 뒤 앞뒤에 남은 공백. `strip()` 은 `\p{Zs}` 를 다 지우지 못합니다. */
    private static final Pattern SURROUNDING_SPACE = Pattern.compile("(?:^ +)|(?: +$)");

    /**
     * 쓸 수 없는 문자.
     *
     * `\p{Cntrl}` 을 쓰지 않습니다. Java 에서 그것은 `[\x00-\x1F\x7F]` 라 ASCII 범위만 봅니다.
     * U+0085(NEL)나 U+009F 같은 제어 문자가 그대로 통과해 DB 에 저장됩니다.
     *
     * `Cc` 는 제어 문자, `Cf` 는 포맷 문자입니다. `Cf` 도 막습니다. U+202E 같은 양방향 재정의
     * 문자가 들어가면 닉네임이 다른 이름처럼 보이는데, 방 안에서 사람을 구분하는 수단이 닉네임뿐입니다.
     * `Zl`(U+2028)과 `Zp`(U+2029)는 `Zs` 가 아니라 위에서 접히지 않으면서 줄바꿈으로 렌더링됩니다.
     * `<` 와 `>` 는 PRD 7장이 마크업을 허용하지 않는다고 정해서 막습니다.
     *
     * `Cs`(대리 문자)를 빼면 JSON 의 `"\ud83d"` 같은 짝 없는 UTF-16 이스케이프가 통과합니다.
     * Jackson 이 그것을 낱개 대리 문자가 든 String 으로 만드는데, 그 값은 codePointCount 로 1자이고
     * Cc·Cf·Zs 어디에도 걸리지 않습니다. 그 뒤 JDBC 가 UTF-8 로 인코딩하면서 `?` 로 바꿉니다.
     * **치환이 드라이버 안에서 일어나 MySQL 은 1366 도 내지 않습니다.**
     * 오류 없이 입력한 적 없는 이름이 저장됩니다.
     *
     * 서로 다른 입력 넷(U+D800, U+DC00, 잘린 이모지, 문자 `?`)이 전부 같은 한 바이트가 되어
     * nickname_key 가 겹치므로, 참여 흐름에서 uk_participant_room_nickname 이
     * **사용자가 입력하지도 않은 이름으로 중복을 판정합니다.** 이모지가 든 닉네임을 클라이언트가
     * UTF-16 단위로 자르면 실제로 만들어지는 값입니다.
     *
     * 정상 이모지는 코드포인트 하나(U+1F600)로 매칭되므로 이 규칙에 걸리지 않습니다.
     *
     * 뒤의 여섯 자는 카테고리에 걸리지 않습니다. Lo(한글 채움 문자), So(점자 빈 칸),
     * Mn(결합 자소 이음)이라 `지\u3164은` 이 `지은` 과 화면에서 같아 보이는데 키는 다릅니다.
     * as_cs 가 U+3164 에 weight 를 주므로 uk_participant_room_nickname 도 막지 못합니다.
     *
     * 프론트의 `FORBIDDEN_PATTERN` 과 같은 규칙입니다. 서버가 더 느슨하면 브라우저를 거치지 않는
     * 요청이 그대로 통과합니다 (docs/domain.md 의 "서버는 프론트를 믿지 않는다").
     */
    private static final Pattern FORBIDDEN_CHARACTER = Pattern.compile(
            "[\\p{Cc}\\p{Cf}\\p{Cs}\\p{Zl}\\p{Zp}<>"
                    + "\\x{115F}\\x{1160}\\x{3164}\\x{FFA0}"
                    + "\\x{2800}"
                    + "\\x{034F}"
                    + "]");

    /**
     * 아무것도 그리지 않는 문자만으로 이뤄진 입력을 잡습니다. 위 목록은 문자를 하나씩 적는
     * 방식이라 새로 나오는 것을 놓치므로 조건을 하나 더 둡니다.
     *
     * 남는 것은 결합 문자(Mn, Me)와 사설 영역(Co)뿐입니다. Cc·Cf·Cs·Zl·Zp 는 위에서 걸렀고
     * Zs 는 접어서 지웠습니다. Mn 을 위 목록에 넣지 않은 것은 `민수❤\uFE0F` 때문입니다.
     *
     * **L·N·P·S 를 허용 목록으로 쓰지 마십시오.** JVM 이 아는 유니코드 판에 판정이 묶입니다.
     * Java 21 은 유니코드 15.0 이라 그 뒤에 배정된 이모지(U+1FAE9 등)를 UNASSIGNED 로 보고
     * 어느 카테고리에도 넣지 않습니다. 브라우저는 통과시키는 닉네임을 서버만 거절하게 됩니다.
     */
    private static final Pattern VISIBLE_CHARACTER =
            Pattern.compile("[^\\p{Mn}\\p{Me}\\p{Co}]");

    /**
     * 사용자 입력을 표시값과 키로 바꿉니다.
     *
     * 프론트의 `normalizeNickname` + `validateNickname` 이 하는 일을 서버에서 다시 한 뒤,
     * 중복 판정용 키까지 만듭니다. 키 생성은 서버만 합니다.
     *
     * 순서가 결과를 바꿉니다. 공백을 접기 전에 금지 문자를 보면 `"지은\n"` 이 거절되고,
     * 접은 뒤에 보면 `\n` 이 이미 공백으로 바뀌어 통과합니다. 프론트는 접기를 `\p{Zs}` 로만
     * 하므로 `\n` 이 살아남아 거절되는 쪽입니다. 서버도 같은 결과를 내야 합니다.
     *
     * 길이는 표시값을 코드포인트로 셉니다. `String.length()` 는 이모지를 2로 세는데
     * `nickname VARCHAR(12)` 는 문자 수로 세므로 둘이 어긋납니다.
     *
     * @param raw 요청 바디의 닉네임. null 일 수 있습니다
     * @throws ApiException 규칙에 어긋나면 {@link ApiErrorCode#NICKNAME_INVALID}
     */
    public static Nickname of(String raw) {
        if (raw == null) {
            throw invalid("값이 없다");
        }

        // 접는 것은 Zs 뿐입니다. 줄바꿈과 탭은 여기서 살아남아 아래 금지 문자 검사에 걸립니다.
        String folded = SPACE_SEPARATOR_RUN.matcher(raw).replaceAll(" ");
        String display = SURROUNDING_SPACE.matcher(folded).replaceAll("");

        if (display.isEmpty()) {
            throw invalid("공백만 입력됐다");
        }
        // 길이보다 먼저 봅니다. 쓸 수 없는 문자를 지우면 길이도 줄지만 그 반대는 아닙니다.
        if (FORBIDDEN_CHARACTER.matcher(display).find()) {
            throw invalid("허용하지 않는 문자가 있다");
        }
        // 순서를 바꾸면 U+3164 가 Lo 라 여기를 통과합니다.
        if (!VISIBLE_CHARACTER.matcher(display).find()) {
            throw invalid("화면에 보이는 글자가 없다");
        }

        int length = display.codePointCount(0, display.length());
        if (length < MIN_LENGTH || length > MAX_LENGTH) {
            throw invalid("길이가 " + length + "자다");
        }

        // NFC 를 먼저 하고 공백을 지운 뒤 소문자화합니다. PRD 7장이 정한 순서입니다.
        // 소문자화가 문자 수를 늘릴 수 있어서 nickname_key 를 VARCHAR(48) 로 넓혀 뒀습니다.
        //
        // 공백을 지우는 것은 키뿐입니다. display 에는 내부 공백이 남습니다. PRD 7장이 "공백 제거는
        // 중복 판정에만 적용한다" 고 나눠 뒀습니다. 표시값까지 지우면 `지 은` 으로 입력한 사람이
        // `지은` 으로 보입니다.
        //
        // 여기서 남아 있는 공백은 U+0020 하나뿐입니다. 위에서 \p{Zs} 를 전부 그것으로 접었고
        // 앞뒤도 지웠으므로, 이 시점의 display 에는 낱개의 보통 공백만 있습니다.
        // display 는 앞뒤 공백이 이미 지워져 있어 공백만으로 이루어질 수 없습니다. 비어 있는
        // 경우는 위에서 걸리므로 공백을 지운 키도 비지 않습니다.
        String key = Normalizer.normalize(display, Normalizer.Form.NFC)
                .replace(" ", "")
                .toLowerCase(Locale.ROOT);
        return new Nickname(display, key);
    }

    private static ApiException invalid(String reason) {
        return new ApiException(ApiErrorCode.NICKNAME_INVALID, "닉네임 규칙 위반: " + reason);
    }
}
