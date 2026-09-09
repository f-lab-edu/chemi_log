"use client";

import { useEffect } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useApiResource } from "@/shared/hooks/useApiResource";
import { buttonClass } from "@/shared/ui/Button";
import { LoadingScreen } from "@/shared/ui/StateScreen";
import { getRoom } from "./api";
import { RoomErrorScreen } from "./RoomErrorScreen";
import { ShareInvite } from "./ShareInvite";
import type { RoomSummary } from "./types";

/**
 * 04 초대 링크 (위키 `UI-MVP`).
 *
 * 방장이 12문항을 제출해 `room.status` 가 `OPEN` 으로 바뀐 뒤 나온다.
 * 참여 현황 화면에서 링크를 다시 보려고 들어올 수도 있다.
 *
 * **방 상태를 읽는 이유가 있다.** 이 화면은 "답변 완료!" 라고 단언하는데, 링크로 직접 열 수
 * 있어서 그 말이 사실인지 확인하지 않으면 아직 제출하지 않은 사람에게도 완료 화면과
 * 아직 열리지 않은 방의 초대 링크를 보여주게 된다.
 */
export function InviteScreen({ shareCode }: { shareCode: string }) {
  const router = useRouter();
  const resource = useApiResource<RoomSummary>(
    () => getRoom(shareCode),
    shareCode,
  );

  // 아직 읽는 중이면 판정하지 않는다. `me` 를 성급히 `null` 로 보면 참여 화면으로 튄다.
  const redirect =
    resource.status === "ready"
      ? destinationFor(resource.data.me, shareCode)
      : null;

  useEffect(() => {
    if (redirect) router.replace(redirect);
  }, [redirect, router]);

  if (resource.status === "loading") {
    return <LoadingScreen message="방을 확인하고 있어요" />;
  }
  if (resource.status === "error") {
    return <RoomErrorScreen error={resource.error} onRetry={resource.reload} />;
  }
  // 이동이 끝날 때까지 완료 화면이 잠깐 보이지 않게 막는다.
  if (redirect) {
    return <LoadingScreen message="이어서 진행할게요" />;
  }

  return (
    <div className="flex flex-1 flex-col px-6 text-center">
      <header className="-mx-6 bg-linear-158 from-[#6a48ff] via-[#9b5cff] to-[#c46af0] px-6 pt-9 pb-9.5 text-white">
        <div
          className="mx-auto flex size-22 items-center justify-center rounded-full border-[1.5px] border-white/35 bg-white/20 text-4xl"
          aria-hidden
        >
          ✓
        </div>
        <h1 className="mt-5.5 text-2xl leading-[1.4] font-bold">
          답변 완료!
          <br />
          친구를 초대하세요
        </h1>
      </header>

      <p className="mt-6.5 text-[15px] leading-[1.6] text-ink-sub">
        친구가 같은 질문 12개에 답하면
        <br />
        케미 점수를 볼 수 있어요.
      </p>

      <ShareInvite
        shareCode={shareCode}
        footer={
          // 목업 04 에는 없는 버튼이다. 방장은 제출 직후 `router.replace` 로 여기 오므로
          // 히스토리에 되돌아갈 항목이 없고, 이것이 없으면 친구가 답을 마쳐도 결과로 갈 수
          // 없다. 07 화면의 "초대 링크 다시 보내기" 와 짝이 되어 두 화면이 서로 오간다.
          <Link
            href={`/rooms/${shareCode}/status`}
            className={`${buttonClass("ghost")} mt-3`}
          >
            참여 현황 보기
          </Link>
        }
      />
    </div>
  );
}

/**
 * 이 화면에 있으면 안 되는 사람을 어디로 보낼지 정한다. 머물러야 하면 `null` 이다.
 *
 * `AnswerFlow` 의 같은 이름 함수와 조건이 서로 반대라 두 화면이 서로를 부르지 않는다.
 * 여기는 제출을 마쳐야 머물고, 답변 화면은 마치지 않아야 머문다.
 */
function destinationFor(
  me: RoomSummary["me"],
  shareCode: string,
): string | null {
  // 참여자 토큰이 없으면 이 방 사람이 아니다. 참여부터 하게 돌려보낸다.
  if (!me) return `/r/${shareCode}`;
  if (me.answerStatus !== "SUBMITTED") return `/rooms/${shareCode}/answer`;
  return null;
}
