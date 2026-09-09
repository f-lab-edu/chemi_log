"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { JoinForm } from "@/features/participant/JoinForm";
import { ApiError } from "@/shared/api/client";
import { useApiResource } from "@/shared/hooks/useApiResource";
import { LoadingScreen } from "@/shared/ui/StateScreen";
import { getRoom } from "./api";
import { RoomErrorScreen } from "./RoomErrorScreen";
import type { RoomSummary } from "./types";

/**
 * 초대 링크로 들어온 사람을 알맞은 화면으로 보낸다 (목업 05, PRD 12장 재접속).
 *
 * ```text
 * 이미 제출함        →  참여 현황
 * 참여했지만 미제출  →  질문 답변
 * 처음 온 사람       →  참여 화면 (이 화면)
 * 방장이 답변 중     →  "방을 준비하고 있어요"
 * ```
 *
 * 판정 근거는 `me` 다. 참여자 토큰은 `HttpOnly` 라 프론트가 읽지 못하므로 서버가 알려준다.
 */
export function RoomEntry({ shareCode }: { shareCode: string }) {
  const router = useRouter();
  const resource = useApiResource<RoomSummary>(
    () => getRoom(shareCode),
    shareCode,
  );

  const me = resource.status === "ready" ? resource.data.me : null;

  useEffect(() => {
    if (!me) return;
    router.replace(
      me.answerStatus === "SUBMITTED"
        ? `/rooms/${shareCode}/status`
        : `/rooms/${shareCode}/answer`,
    );
  }, [me, router, shareCode]);

  if (resource.status === "loading") {
    return <LoadingScreen message="방을 확인하고 있어요" />;
  }
  if (resource.status === "error") {
    return <RoomErrorScreen error={resource.error} onRetry={resource.reload} />;
  }

  const room = resource.data;

  // 이동이 끝날 때까지 참여 화면이 잠깐 보이지 않게 막는다.
  if (room.me) {
    return <LoadingScreen message="이어서 진행할게요" />;
  }

  // 방장이 아직 답변 중이면 다른 사람은 참여할 수 없다 (PRD 5장).
  // 서버가 409 를 주기 전에 프론트가 먼저 알 수 있는 상태라, 참여를 시도시키지 않고 여기서 막는다.
  if (room.status === "HOST_ANSWERING") {
    return (
      <RoomErrorScreen
        error={new ApiError("ROOM_NOT_OPEN", "", 409)}
        onRetry={resource.reload}
      />
    );
  }

  return <JoinForm room={room} />;
}
