import type { QuestionCategory } from "./category";

/** 2지선다. `answer` 테이블이 생기면 그 ENUM 과 맞춰야 한다. */
export type Choice = "A" | "B";

/**
 * 방에 고정된 질문 하나. `room_question` 스냅샷을 그대로 읽는다.
 * `question` 과 조인하지 않는다 (`docs/domain.md`).
 */
export interface RoomQuestion {
  /** 1~12. `UNIQUE (room_id, display_order)` 로 보장된다. */
  displayOrder: number;
  category: QuestionCategory;
  content: string;
  optionA: string;
  optionB: string;
}

export interface RoomQuestionsResponse {
  questions: RoomQuestion[];
}

/** 제출 요청. 12개를 모두 채워야 한다 (PRD 9장 "모든 질문은 필수 답변이다"). */
export interface SubmitAnswersRequest {
  answers: { displayOrder: number; choice: Choice }[];
}

export interface SubmitAnswersResponse {
  /** 제출 뒤 내 상태. 규약상 언제나 `SUBMITTED` 지만 서버가 준 값을 쓴다. */
  answerStatus: "ANSWERING" | "SUBMITTED";
  /** 제출을 마친 참여자 수. 2명 이상이면 결과를 볼 수 있다 (PRD 9장). */
  submittedCount: number;
}
