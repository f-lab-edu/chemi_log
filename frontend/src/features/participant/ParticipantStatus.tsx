"use client";

import Link from "next/link";
import { RoomErrorScreen } from "@/features/room/RoomErrorScreen";
import { useApiResource } from "@/shared/hooks/useApiResource";
import { Avatar } from "@/shared/ui/Avatar";
import { Button, buttonClass } from "@/shared/ui/Button";
import { LoadingScreen } from "@/shared/ui/StateScreen";
import { getParticipants } from "./api";
import type { ParticipantSummary, ParticipantsResponse } from "./types";

/** 결과를 열려면 제출을 마친 사람이 이만큼 있어야 한다 (PRD 9장). */
const RESULT_THRESHOLD = 2;

/**
 * 07 참여 현황 (위키 `UI-MVP`).
 *
 * 모두를 기다리지 않는다. 완료자가 2명이면 결과가 이미 열려 있고, 이후 완료자가 생길 때마다
 * 순위를 다시 계산한다 (PRD 9장).
 */
export function ParticipantStatus({ shareCode }: { shareCode: string }) {
  const resource = useApiResource<ParticipantsResponse>(
    () => getParticipants(shareCode),
    shareCode,
  );

  if (resource.status === "loading") {
    return <LoadingScreen message="참여 현황을 불러오고 있어요" />;
  }
  if (resource.status === "error") {
    return <RoomErrorScreen error={resource.error} onRetry={resource.reload} />;
  }

  const { participants, submittedCount } = resource.data;
  const waiting = participants.filter((p) => p.answerStatus === "ANSWERING");
  // 두 가지를 나눠서 본다. 결과가 만들어졌는가와, 내가 그것을 볼 수 있는가는 다르다.
  // 완료자가 2명이어도 내가 아직 제출하지 않았으면 결과 조회는 403 이다 (PRD 7장 결과 접근).
  const resultExists = submittedCount >= RESULT_THRESHOLD;
  const iSubmitted =
    participants.find((p) => p.me)?.answerStatus === "SUBMITTED";
  const canSeeResult = resultExists && iSubmitted;

  return (
    <div className="flex flex-1 flex-col px-6 text-center">
      <header className="-mx-6 bg-linear-158 from-[#6a48ff] via-[#9b5cff] to-[#c46af0] px-6 pt-7.5 pb-8 text-white">
        <div className="flex justify-center">
          {participants.slice(0, 5).map((p) => (
            <div key={p.nickname} className="-ml-3 first:ml-0 rounded-2xl ring-3 ring-white/90">
              <Avatar nickname={p.nickname} />
            </div>
          ))}
        </div>
        <h1 className="mt-4.5 text-[22px] leading-[1.4] font-bold">
          {/* 헤더는 방의 상태를 말한다. 내가 봤는지와 무관하게 케미는 이미 만들어져 있다. */}
          {resultExists ? (
            <>
              {submittedCount}명의 케미가
              <br />
              준비됐어요
            </>
          ) : (
            <>친구를 기다리고 있어요</>
          )}
        </h1>
        <p className="mt-2.5 text-[13px] leading-[1.55] text-white/85">
          {iSubmitted
            ? waitingMessage(waiting, resultExists, participants.length)
            : "내 답변을 제출하면 케미 결과를 볼 수 있어요"}
        </p>
      </header>

      <ul className="mt-5.5 flex-1 text-left">
        {participants.map((p) => (
          <li
            key={p.nickname}
            className="flex items-center gap-3 border-b border-[#f4f2fa] px-1 py-3.5"
          >
            <Avatar nickname={p.nickname} />
            <div className="flex-1 text-[15.5px] font-semibold">
              {p.nickname}
              <small className="mt-0.75 block text-xs font-medium text-ink-mute">
                {[p.host && "방장", p.me && "나"].filter(Boolean).join(" · ") ||
                  " "}
              </small>
            </div>
            <span
              className={`rounded-full px-2.75 py-1.5 text-xs font-bold ${
                p.answerStatus === "SUBMITTED"
                  ? "bg-[#eaf7f0] text-[#2e9c69]"
                  : "bg-[#f3f1f9] text-[#8b85a3]"
              }`}
            >
              {p.answerStatus === "SUBMITTED" ? "완료" : "답변 중"}
            </span>
          </li>
        ))}
      </ul>

      <div className="pb-safe pt-6">
        {/* 눌러 봐야 막히는 버튼을 띄우지 않는다. 할 수 있는 일을 그대로 보여준다. */}
        {!iSubmitted ? (
          <Link
            href={`/rooms/${shareCode}/answer`}
            className={buttonClass("primary")}
          >
            답변하러 가기
          </Link>
        ) : canSeeResult ? (
          <Link
            href={`/rooms/${shareCode}/result`}
            className={buttonClass("primary")}
          >
            케미 결과 보기
          </Link>
        ) : (
          <Button variant="primary" onClick={resource.reload}>
            새로고침
          </Button>
        )}
        <Link
          href={`/rooms/${shareCode}/invite`}
          className={`${buttonClass("soft")} mt-3`}
        >
          초대 링크 다시 보내기
        </Link>
      </div>
    </div>
  );
}

/**
 * 아직 답하지 않은 사람을 알려 준다.
 *
 * 이름을 다 나열하지 않는 이유는 인원 상한이 없기 때문이다 (PRD 7장).
 * 20명이 답변 중이면 문구가 화면을 덮는다.
 */
function waitingMessage(
  waiting: ParticipantSummary[],
  resultReady: boolean,
  total: number,
): string {
  if (waiting.length === 0) {
    // 방장 혼자면 기다릴 사람이 없는 것이 아니라 아직 아무도 오지 않은 것이다.
    // 이 경우를 나누지 않으면 "친구를 기다리고 있어요" 아래에 "모두 답변을 마쳤어요" 가
    // 붙어 두 문장이 서로 다른 말을 한다. 헤더는 완료자 2명 이상으로 따로 판정한다.
    return total <= 1
      ? "초대 링크를 보내면 친구가 참여할 수 있어요"
      : "모두 답변을 마쳤어요";
  }
  const suffix = resultReady
    ? "답하면 순위를 다시 계산해요"
    : "답하면 결과를 볼 수 있어요";
  return waiting.length === 1
    ? `${waiting[0].nickname}님이 ${suffix}`
    : `${waiting[0].nickname}님 외 ${waiting.length - 1}명이 ${suffix}`;
}
