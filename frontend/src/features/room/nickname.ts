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

export type NicknameProblem = "EMPTY" | "TOO_LONG" | "INVALID_CHARACTER";

export const NICKNAME_PROBLEM_MESSAGE: Record<NicknameProblem, string> = {
  EMPTY: "닉네임을 입력해 주세요.",
  TOO_LONG: `닉네임은 ${NICKNAME_MAX_LENGTH}자까지 쓸 수 있어요.`,
  INVALID_CHARACTER: "쓸 수 없는 문자가 들어 있어요.",
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
 * **`\s` 와 `.trim()` 을 쓰지 않는 이유는 따로 있다.** 둘 다 줄바꿈과 탭을 공백으로 취급해
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
 * `Cc` 는 제어 문자, `Cf` 는 포맷 문자다. PRD 7장이 "제어 문자와 마크업은 허용하지 않는다" 고
 * 했고, `Cf` 를 함께 막는 이유는 U+202E 같은 양방향 재정의 문자가 닉네임을 다른 이름처럼
 * 보이게 만들 수 있기 때문이다. 닉네임이 사람을 구분하는 유일한 수단이라 그대로 두면 안 된다.
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
 */
const FORBIDDEN_PATTERN = /[\p{Cc}\p{Cf}\p{Zl}\p{Zp}<>]/u;

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
  // `.length` 가 아니라 코드포인트 수로 센다. 화면 카운터와 `VARCHAR(12)` 가 모두 문자 수 기준이라,
  // 여기서만 UTF-16 코드 유닛으로 세면 카운터가 `12 / 12` 인데 오류가 뜨는 화면이 된다.
  if (nicknameLength(nickname) > NICKNAME_MAX_LENGTH) {
    return "TOO_LONG";
  }
  return null;
}
