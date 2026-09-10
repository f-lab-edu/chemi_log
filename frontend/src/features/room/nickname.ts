/**
 * 닉네임 규칙. 근거는 PRD 7장 참여자 절과 `database/init/01-schema.sql` 이다.
 *
 * 여기서 하는 것은 **화면에 보여줄 값과 즉시 알려줄 오류**까지다.
 * 중복 판정에 쓰는 NFC 정규화, 공백 제거, 대소문자 통합은 서버가 하고 `nickname_key` 로 저장한다.
 * 같은 방에 이미 있는 이름인지는 프론트가 알 수 없으므로 `409 NICKNAME_DUPLICATED` 로 받는다.
 *
 * **표시값에서는 내부 공백을 지우지 않는다.** 공백 제거는 중복 판정에만 적용한다 (PRD 7장).
 * `지 은` 으로 입력한 사람은 `지 은` 으로 보이고, 중복 판정에서만 `지은` 과 같아진다.
 */

/** `participant.nickname VARCHAR(12)` 와 같다. 문자 수 기준이다. */
export const NICKNAME_MAX_LENGTH = 12;

export type NicknameProblem =
  | "EMPTY"
  | "TOO_LONG"
  | "INVALID_CHARACTER"
  | "NO_VISIBLE_CHARACTER";

export const NICKNAME_PROBLEM_MESSAGE: Record<NicknameProblem, string> = {
  EMPTY: "닉네임을 입력해 주세요.",
  TOO_LONG: `닉네임은 ${NICKNAME_MAX_LENGTH}자까지 쓸 수 있어요.`,
  INVALID_CHARACTER: "쓸 수 없는 문자가 들어 있어요.",
  NO_VISIBLE_CHARACTER: "화면에 보이는 글자를 한 자 이상 넣어 주세요.",
};

/**
 * 저장하고 표시할 값으로 다듬는다. 앞뒤 공백을 없애고 연속 공백을 1칸으로 줄인다.
 * PRD 7장은 이 처리를 저장할 때와 화면에 표시할 때 모두 적용하라고 한다.
 *
 * **다루는 공백은 `\p{Zs}`(공백 구분자) 전부다.** 보이는 모양이 같은데 코드포인트가 다른
 * 공백이 여럿 있다. U+2000 두 개를 넣은 이름과 보통 공백 하나를 넣은 이름은 화면에서
 * 구별되지 않는데 `nickname_key` 는 달라진다. 몇 종만 골라 처리하면 같은 방에 눈으로
 * 구분되지 않는 참여자 두 명이 생긴다.
 *
 * **`\s` 와 `.trim()` 은 쓰지 않는다.** 둘 다 줄바꿈과 탭을 공백으로 취급해
 * 제어 문자를 조용히 지운다. 제어 문자는 지울 것이 아니라 `validateNickname` 이 오류로
 * 걸러야 한다. `.trim()` 을 쓰면 `"지은\n"` 이 오류 없이 통과해 버린다.
 */
export function normalizeNickname(raw: string): string {
  return raw.replace(/\p{Zs}+/gu, " ").replace(/^ +| +$/g, "");
}

/** 화면에 보여줄 글자 수. `.length` 는 이모지를 2로 세므로 코드포인트로 센다. */
export function nicknameLength(value: string): number {
  return [...value].length;
}

/**
 * 쓸 수 없는 문자.
 *
 * `Cc` 는 제어 문자, `Cf` 는 포맷 문자다. PRD 7장이 "제어 문자와 마크업은 허용하지 않는다" 고 했다.
 * `Cf` 도 함께 막는다. U+202E 같은 양방향 재정의 문자가 들어가면 닉네임이 다른 이름처럼 보이는데,
 * 방 안에서 사람을 구분하는 수단이 닉네임뿐이다.
 *
 * `Zl`(U+2028)과 `Zp`(U+2029)도 막는다. 이름 안에서 줄바꿈으로 렌더링되므로
 * PRD 7장의 "화면에 표시 가능한 문자" 에 해당하지 않는다.
 * `Zs` 가 아니라서 `normalizeNickname` 이 공백으로 접지 않는다.
 *
 * 이 규칙은 ZWJ(U+200D)로 이어 붙인 조합 이모지(👨‍👩‍👧)도 함께 막는다.
 * 단일 이모지(😀)는 `So` 카테고리라 통과한다.
 *
 * `<` 와 `>` 는 화면에 넣는 것이 React 가 이스케이프해 안전하지만,
 * PRD 가 마크업을 허용하지 않는다고 못박았으므로 입력 단계에서 막는다.
 *
 * 낱개로 적은 여섯 자는 카테고리에 걸리지 않는다. `Lo`(한글 채움 문자), `So`(점자 빈 칸),
 * `Mn`(결합 자소 이음)이라 `지\u3164은` 이 `지은` 과 화면에서 같아 보이는데
 * 서버가 만드는 `nickname_key` 는 다르다.
 *
 * 그중 `U+034F` 만 문자 클래스 밖에 둔다. 결합 문자라 클래스 안에 넣으면 바로 앞
 * `U+2800` 에 붙어 한 글자처럼 보인다. 목록을 읽는 사람이 빠뜨리게 되고
 * SonarCloud 도 그 형태를 지적한다.
 */
const FORBIDDEN_PATTERN =
  /\u{034F}|[\p{Cc}\p{Cf}\p{Zl}\p{Zp}<>\u{115F}\u{1160}\u{3164}\u{FFA0}\u{2800}]/u;

/**
 * 아무것도 그리지 않는 문자만으로 이뤄진 입력을 잡는다. 위 목록은 문자를 하나씩 적는
 * 방식이라 새로 나오는 것을 놓치므로 조건을 하나 더 둔다.
 *
 * 남는 것은 결합 문자(`Mn`, `Me`)와 사설 영역(`Co`)뿐이다. `Mn` 을 위 목록에 넣지 않은
 * 것은 `민수❤\uFE0F` 때문이다.
 *
 * `L`·`N`·`P`·`S` 허용 목록으로 쓰면 서버와 어긋난다. 서버의 Java 21 은 유니코드 15.0 이라
 * 그 뒤에 배정된 이모지를 어느 카테고리에도 넣지 않는다. 서버 `Nickname.VISIBLE_CHARACTER`
 * 와 같은 규칙을 쓴다.
 */
const VISIBLE_PATTERN = /[^\p{Mn}\p{Me}\p{Co}]/u;

/**
 * 다듬은 닉네임이 규칙에 맞는지 본다. 문제가 없으면 `null` 을 돌려준다.
 *
 * 인자는 `normalizeNickname` 을 이미 거친 값이다.
 */
export function validateNickname(nickname: string): NicknameProblem | null {
  if (nickname.length === 0) {
    return "EMPTY";
  }
  // 길이보다 먼저 본다. 쓸 수 없는 문자를 지우면 길이도 함께 줄지만,
  // 길이를 줄인다고 쓸 수 없는 문자가 없어지지는 않는다. 두 번 고치게 하지 않는 쪽을 먼저 알린다.
  if (FORBIDDEN_PATTERN.test(nickname)) {
    return "INVALID_CHARACTER";
  }
  // 순서를 바꾸면 U+3164 가 `Lo` 라 여기를 통과한다.
  if (!VISIBLE_PATTERN.test(nickname)) {
    return "NO_VISIBLE_CHARACTER";
  }
  // `.length` 가 아니라 코드포인트 수로 센다. 화면 카운터와 `VARCHAR(12)` 가 모두 문자 수 기준이라,
  // 여기서만 UTF-16 코드 유닛으로 세면 카운터가 `12 / 12` 인데 오류가 뜨는 화면이 된다.
  if (nicknameLength(nickname) > NICKNAME_MAX_LENGTH) {
    return "TOO_LONG";
  }
  return null;
}
