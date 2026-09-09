"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { ApiError } from "@/shared/api/client";
import { Avatar } from "@/shared/ui/Avatar";
import { Button } from "@/shared/ui/Button";
import { createRoom } from "./api";
import { NicknameField, useNicknameInput } from "./nicknameInput";

/**
 * 02 방 만들기 (위키 `UI-MVP`).
 *
 * 방장의 닉네임만 받는다. 방 이름은 받지 않는다.
 */
export function NicknameForm() {
  const router = useRouter();
  const input = useNicknameInput();
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault();
    input.markTouched();
    if (input.problem !== null || submitting) {
      return;
    }

    setSubmitting(true);
    try {
      const room = await createRoom(input.nickname);
      // 방장은 만들자마자 답변을 시작한다. 12개를 제출해야 방이 OPEN 이 된다 (PRD 5장).
      // 성공 뒤에는 되돌아올 화면이 아니라 replace 를 쓴다.
      router.replace(`/rooms/${room.shareCode}/answer`);
    } catch (error) {
      input.setServerMessage(
        error instanceof ApiError ? error.message : "잠시 후 다시 시도해 주세요.",
      );
      setSubmitting(false);
    }
  }

  return (
    <form onSubmit={handleSubmit} className="flex flex-1 flex-col px-6">
      <div className="flex-1 text-center">
        {input.nickname ? (
          <div className="mt-3.5 flex justify-center">
            <Avatar nickname={input.nickname} size="lg" />
          </div>
        ) : (
          <div
            className="mx-auto mt-3.5 flex size-19.5 items-center justify-center rounded-[26px] border-2 border-dashed border-[#dcd6f2] bg-[#f1eefb] text-3xl font-extrabold text-[#c4bedc]"
            aria-hidden
          >
            ?
          </div>
        )}

        <h1 className="mt-5.5 text-[25px] leading-[1.4] font-bold tracking-tight">
          어떤 이름으로
          <br />
          참여할까요?
        </h1>
        <p className="mt-2.5 text-[15px] leading-[1.6] text-ink-sub">
          방 안에서 이 이름으로 보여요.
        </p>

        <div className="mt-6.5">
          <NicknameField input={input} />
        </div>

        <div className="mt-4 rounded-2xl bg-surface px-4.5 py-4 text-left text-[13.5px] leading-[1.65] text-[#5a5470]">
          방을 만들면 <b className="font-bold text-brand-deep">질문 12개</b>가
          자동으로 준비돼요.
          <br />
          대화 · 여행 · 생활 · 소비에서 3개씩 무작위로 뽑아요.
        </div>
      </div>

      <div className="pb-safe pt-6">
        <Button type="submit" disabled={submitting}>
          {submitting ? "방을 만드는 중..." : "방 만들고 시작하기"}
        </Button>
      </div>
    </form>
  );
}
