import { apiFetch } from "@/shared/api/client";
import type {
  CreateRoomRequest,
  CreateRoomResponse,
  JoinRoomRequest,
  JoinRoomResponse,
  RoomSummary,
} from "./types";

/**
 * 방을 만들고 방장을 첫 참여자로 등록한다.
 *
 * 서버는 room, participant, room.host_participant_id, room_question 12개를
 * 한 트랜잭션으로 처리한다 (`docs/domain.md`). 응답과 함께 참여자 토큰 쿠키가 내려온다.
 */
export function createRoom(nickname: string): Promise<CreateRoomResponse> {
  const body: CreateRoomRequest = { nickname };
  return apiFetch<CreateRoomResponse>("/api/rooms", {
    method: "POST",
    body: JSON.stringify(body),
  });
}

/**
 * 방 요약을 읽는다. 초대 링크로 들어온 화면이 다음에 어디로 갈지 이 응답으로 정한다.
 *
 * 공유 코드가 없으면 `404 ROOM_NOT_FOUND` 다.
 */
export function getRoom(shareCode: string): Promise<RoomSummary> {
  return apiFetch<RoomSummary>(`/api/rooms/${encodeURIComponent(shareCode)}`);
}

/**
 * 방에 참여한다. 응답과 함께 참여자 토큰 쿠키가 내려온다.
 *
 * `409 NICKNAME_DUPLICATED` 는 같은 방에 이미 있는 이름일 때,
 * `409 ROOM_NOT_OPEN` 은 방장이 아직 답변 중일 때다 (위키 `API-규약`).
 */
export function joinRoom(
  shareCode: string,
  nickname: string,
): Promise<JoinRoomResponse> {
  const body: JoinRoomRequest = { nickname };
  return apiFetch<JoinRoomResponse>(
    `/api/rooms/${encodeURIComponent(shareCode)}/participants`,
    { method: "POST", body: JSON.stringify(body) },
  );
}
