import { apiFetch } from "@/shared/api/client";
import type { ParticipantsResponse } from "./types";

/**
 * 참여 현황을 읽는다 (목업 07).
 *
 * 인원 상한이 없으므로(PRD 7장) 목록이 길어질 수 있지만, 참여자 목록 자체는
 * 인원에 비례할 뿐이라 Pair 목록처럼 제곱으로 늘지 않는다. 페이징을 두지 않는다.
 */
export function getParticipants(
  shareCode: string,
): Promise<ParticipantsResponse> {
  return apiFetch<ParticipantsResponse>(
    `/api/rooms/${encodeURIComponent(shareCode)}/participants`,
  );
}
