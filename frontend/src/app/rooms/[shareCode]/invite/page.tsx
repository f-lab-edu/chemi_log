import { ShareInvite } from "@/features/room/ShareInvite";

/**
 * 04 초대 링크 (위키 `UI-MVP`).
 *
 * 방장이 12문항을 제출해 `room.status` 가 `OPEN` 으로 바뀐 뒤 나온다.
 * `AnswerFlow` 가 제출에 성공하면 방장을 이 화면으로 보낸다.
 * 참여 현황 화면에서 링크를 다시 보려고 들어올 수도 있다.
 */
export default async function InvitePage({
  params,
}: PageProps<"/rooms/[shareCode]/invite">) {
  const { shareCode } = await params;

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

      <ShareInvite shareCode={shareCode} />
    </div>
  );
}
