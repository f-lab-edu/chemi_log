import type { Choice, RoomQuestion } from "./types";

/**
 * 제출 전 진행 상태. 방 코드마다 따로 보관해 새로고침과 재접속에서 되살린다 (PRD 7장).
 *
 * **참여자 식별에 쓰지 마라.** 그것은 참여자 토큰의 몫이다. 토큰 없이 임시 답변만 남아
 * 있으면 제출할 수 없고, 그 판정은 `AnswerFlow` 의 `destinationFor` 가 한다.
 */
export interface AnswerDraft {
  /** `displayOrder` → 선택. 아직 답하지 않은 문항은 키가 없다. */
  answers: Record<number, Choice>;
  /** 보고 있던 문항의 배열 위치. 항상 `questions` 범위 안이다. */
  index: number;
}

const EMPTY: AnswerDraft = { answers: {}, index: 0 };

/**
 * 방마다 키를 나눈다. 한 브라우저로 여러 방에 참여하면 답변이 섞이기 때문이다.
 * 공유 코드는 base64url 22자라 `:` 가 들어갈 수 없어 구분자로 안전하다.
 */
function keyFor(shareCode: string): string {
  return `chemilog:answers:${shareCode}`;
}

/**
 * 저장소를 못 쓰는 환경이면 `null` 이다.
 *
 * `window.localStorage` 는 **읽는 것만으로 던진다.** Safari 프라이빗 모드와 쿠키 차단
 * 설정이 그렇다. 저장이 안 되는 것은 답변 화면이 죽을 이유가 아니다 (PRD 17장 호환성).
 */
function storage(): Storage | null {
  try {
    return window.localStorage;
  } catch {
    return null;
  }
}

/** 저장된 진행 상태를 이 방의 질문 세트에 맞춰 읽는다. 없거나 못 읽으면 처음부터다. */
export function loadDraft(
  shareCode: string,
  questions: RoomQuestion[],
): AnswerDraft {
  const store = storage();
  if (!store) return EMPTY;
  try {
    const raw = store.getItem(keyFor(shareCode));
    if (raw === null) return EMPTY;
    return reconcile(JSON.parse(raw), questions);
  } catch {
    return EMPTY;
  }
}

/**
 * 저장소에서 읽은 값을 검증해 되살린다.
 *
 * **이 값은 신뢰할 수 없다.** 사용자가 개발자 도구로 고칠 수 있고, 옛 형식이 남아 있을 수도
 * 있고, 그사이 다른 방의 값이 같은 키에 들어갔을 수도 있다. 아는 문항과 아는 선택만 남긴다.
 *
 * 하나라도 이상하면 전부 버리는 대신 유효한 것만 추린다. 한 항목이 깨졌다고 나머지 답변까지
 * 날리면 복구 기능이 있으나 마나 해지기 때문이다.
 */
function reconcile(parsed: unknown, questions: RoomQuestion[]): AnswerDraft {
  if (typeof parsed !== "object" || parsed === null) return EMPTY;
  const { answers, index } = parsed as Partial<AnswerDraft>;
  if (typeof answers !== "object" || answers === null) return EMPTY;

  const known = new Set(questions.map((question) => question.displayOrder));
  const restored: Record<number, Choice> = {};
  for (const [key, choice] of Object.entries(answers)) {
    const displayOrder = Number(key);
    if (known.has(displayOrder) && (choice === "A" || choice === "B")) {
      restored[displayOrder] = choice;
    }
  }
  return { answers: restored, index: clamp(index, restored, questions) };
}

/**
 * 되살릴 위치를 정한다. 배열 밖과 **아직 답하지 않은 문항 너머**를 모두 막는다.
 *
 * 답변과 위치는 따로 저장되므로 서로 맞지 않는 값이 들어올 수 있다.
 * `{"answers":{"1":"A"},"index":11}` 을 그대로 믿으면 12번 문항이 그려지고, 답이 하나뿐인데
 * 제출 버튼이 열린다. 서버가 `400 VALIDATION_FAILED` 로 막지만 화면은 어느 문항이 비었는지
 * 알려주지 않아 사용자가 빠져나갈 길이 없다.
 *
 * 그래서 첫 미답변 문항을 상한으로 둔다. 어떤 값에서 시작하든 거기서부터 순서대로 답하게 되어
 * 12개가 반드시 채워진다. 정상적으로 저장된 값은 이 상한에 걸리지 않는다.
 * 답을 n개 한 사람의 위치는 언제나 n 이하이기 때문이다.
 */
function clamp(
  index: unknown,
  answers: Record<number, Choice>,
  questions: RoomQuestion[],
): number {
  const unanswered = questions.findIndex(
    (question) => !(question.displayOrder in answers),
  );
  // 전부 답했으면 마지막 문항까지 오갈 수 있다.
  const limit = unanswered === -1 ? questions.length - 1 : unanswered;
  if (!Number.isInteger(index)) return 0;
  return Math.min(Math.max(index as number, 0), Math.max(limit, 0));
}

/**
 * 진행 상태를 저장한다. 실패하면 `false` 다.
 *
 * 호출부는 이 값으로 "새로고침하면 선택이 사라진다" 를 안내한다 (PRD 17장).
 * 저장소를 못 쓰는 경우와 한도를 넘긴 경우(`QuotaExceededError`)가 모두 여기로 모인다.
 */
export function saveDraft(shareCode: string, draft: AnswerDraft): boolean {
  const store = storage();
  if (!store) return false;
  try {
    store.setItem(keyFor(shareCode), JSON.stringify(draft));
    return true;
  } catch {
    return false;
  }
}

/** 제출에 성공하면 지운다. 복구는 제출 전 상태에만 쓴다 (PRD 7장). */
export function clearDraft(shareCode: string): void {
  const store = storage();
  if (!store) return;
  try {
    store.removeItem(keyFor(shareCode));
  } catch {
    // 지우지 못해도 할 수 있는 일이 없다. 제출은 이미 끝났고 서버 상태가 진실이다.
    // 남은 값으로 다시 들어와도 `destinationFor` 가 제출 완료자를 걸러 낸다.
  }
}
