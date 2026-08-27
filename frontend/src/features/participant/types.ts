import type { AnswerStatus } from "@/features/room/types";

/**
 * 참여 현황 한 줄 (목업 07).
 *
 * 서버가 `host` 와 `me` 를 정해서 내려준다. 프론트가 닉네임을 비교해 판정하지 않는다.
 * 닉네임은 표시용 값이라 같은 문자열이 두 사람일 수는 없지만, 내가 누구인지는
 * 참여자 토큰으로만 알 수 있고 그 토큰은 `HttpOnly` 라 프론트가 읽지 못한다.
 */
export interface ParticipantSummary {
  nickname: string;
  host: boolean;
  me: boolean;
  answerStatus: AnswerStatus;
}

export interface ParticipantsResponse {
  participants: ParticipantSummary[];
  submittedCount: number;
  totalCount: number;
}
