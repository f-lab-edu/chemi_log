import { apiFetch } from "@/shared/api/client";
import type {
  Choice,
  RoomQuestionsResponse,
  SubmitAnswersRequest,
  SubmitAnswersResponse,
} from "./types";

/**
 * 방에 고정된 질문 12개를 순서대로 읽는다.
 *
 * 방 생성 시점의 스냅샷이라 원본 질문이 바뀌어도 이 값은 바뀌지 않는다 (PRD 8장).
 * 같은 방의 모든 참여자가 같은 질문을 같은 순서로 받는다.
 */
export function getRoomQuestions(
  shareCode: string,
): Promise<RoomQuestionsResponse> {
  return apiFetch<RoomQuestionsResponse>(
    `/api/rooms/${encodeURIComponent(shareCode)}/questions`,
  );
}

/**
 * 답변 12개를 한 번에 제출한다.
 *
 * 문항별로 보내지 않는 이유는 멱등성이다. 규약은 "같은 요청이 반복돼도 한 번만 반영" 하고
 * 다른 내용으로 재제출하면 `409 ALREADY_SUBMITTED` 를 주도록 정했다. 제출을 한 번의
 * 요청으로 두면 서버가 그 판정을 한 트랜잭션 안에서 끝낼 수 있다.
 *
 * 12개를 모두 채워야 한다 (PRD 9장 "모든 질문은 필수 답변이다").
 */
export function submitAnswers(
  shareCode: string,
  answers: Record<number, Choice>,
): Promise<SubmitAnswersResponse> {
  const body: SubmitAnswersRequest = {
    answers: Object.entries(answers)
      .map(([displayOrder, choice]) => ({
        displayOrder: Number(displayOrder),
        choice,
      }))
      .sort((a, b) => a.displayOrder - b.displayOrder),
  };
  return apiFetch<SubmitAnswersResponse>(
    `/api/rooms/${encodeURIComponent(shareCode)}/answers`,
    { method: "POST", body: JSON.stringify(body) },
  );
}
