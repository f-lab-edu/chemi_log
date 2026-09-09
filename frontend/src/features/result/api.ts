import { apiFetch } from "@/shared/api/client";
import type { MyPairPage, RoomResult } from "./types";

/** PRD 11장의 "점수순으로 10개씩 더 보기" 를 그대로 따른다. */
export const MY_PAIR_PAGE_SIZE = 10;

/**
 * 결과를 읽는다.
 *
 * 답변을 제출하지 않은 사람은 `403 RESULT_NOT_ALLOWED` 다 (PRD 7장 결과 접근).
 * 제출을 마친 사람이 2명 미만이면 `featuredPair` 가 `null` 이고 화면은 대기 상태로 간다.
 */
export function getRoomResult(shareCode: string): Promise<RoomResult> {
  return apiFetch<RoomResult>(
    `/api/rooms/${encodeURIComponent(shareCode)}/results`,
  );
}

/**
 * 내 Pair 목록의 다음 쪽을 읽는다.
 *
 * 전체를 한 번에 내려받지 않는 이유는 Pair 수가 참여자 수의 제곱에 비례하기 때문이다
 * (PRD 11장). 20명이면 190개가 된다.
 */
export function getMyPairs(
  shareCode: string,
  offset: number,
): Promise<MyPairPage> {
  const query = new URLSearchParams({
    offset: String(offset),
    limit: String(MY_PAIR_PAGE_SIZE),
  });
  return apiFetch<MyPairPage>(
    `/api/rooms/${encodeURIComponent(shareCode)}/results/my-pairs?${query}`,
  );
}
