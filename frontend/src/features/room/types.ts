/** `room.status` ENUM 과 같다 (`database/init/01-schema.sql`). */
export type RoomStatus = "HOST_ANSWERING" | "OPEN";

/** `participant.answer_status` ENUM 과 같다. */
export type AnswerStatus = "ANSWERING" | "SUBMITTED";

export interface CreateRoomRequest {
  nickname: string;
}

/**
 * 위키 `API-규약` 의 성공 응답 예시와 같은 형태다. 공통 래퍼로 감싸지 않는다.
 * 방 생성 직후 상태는 항상 `HOST_ANSWERING` 이다 (PRD 5장).
 */
export interface CreateRoomResponse {
  shareCode: string;
  status: RoomStatus;
}

/**
 * 방 요약. 초대 링크로 들어온 사람과 이미 참여한 사람이 모두 이 응답으로 다음 화면을 정한다.
 *
 * **`me` 가 화면 분기의 핵심이다.** 참여자 토큰 쿠키가 없거나 이 방의 참여자가 아니면 `null`
 * 이고, 그때만 참여 화면을 보여준다 (PRD 12장 재접속). 이미 제출한 사람이 다시 들어오면
 * 결과나 대기 화면으로 보낸다.
 */
export interface RoomSummary {
  shareCode: string;
  status: RoomStatus;
  /** 초대 화면의 "○○님의 케미방에 초대받았어요" 에 쓴다 (목업 05). */
  hostNickname: string;
  participantCount: number;
  /** 제출을 마친 사람 수. 2명 이상이면 결과가 열려 있다 (PRD 9장). */
  submittedCount: number;
  me: { nickname: string; host: boolean; answerStatus: AnswerStatus } | null;
}

export interface JoinRoomRequest {
  nickname: string;
}

export interface JoinRoomResponse {
  nickname: string;
  answerStatus: AnswerStatus;
}
