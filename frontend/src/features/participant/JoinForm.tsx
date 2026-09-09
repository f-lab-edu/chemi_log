"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { joinRoom } from "@/features/room/api";
import {
  NicknameField,
  useNicknameInput,
} from "@/features/room/nicknameInput";
import type { RoomSummary } from "@/features/room/types";
import { ApiError } from "@/shared/api/client";
import { Avatar } from "@/shared/ui/Avatar";
import { Button } from "@/shared/ui/Button";

/**
 * 05 초대 링크 진입 (위키 `UI-MVP`).
 *
 * 닉네임 중복을 막는 규칙이 화면에 드러나는 곳이다.
 */
export function JoinForm({ room }: { room: RoomSummary }) {
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
      await joinRoom(room.shareCode, input.nickname);
      router.replace(`/rooms/${room.shareCode}/answer`);
    } catch (error) {
      // 중복 닉네임은 프론트가 미리 알 수 없다. 서버가 판정해 409 로 알려준다.
      input.setServerMessage(
        error instanceof ApiError ? error.message : "잠시 후 다시 시도해 주세요.",
      );
      setSubmitting(false);
    }
  }

  return (
    <form onSubmit={handleSubmit} className="flex flex-1 flex-col px-6">
      <div className="flex-1 text-center">
        <header className="-mx-6 bg-linear-158 from-[#6a48ff] via-[#9b5cff] to-[#c46af0] px-6 pt-7.5 pb-8 text-white">
          <div className="flex justify-center">
            <Avatar nickname={room.hostNickname} size="lg" onBand />
          </div>
          <h1 className="mt-4.5 text-[22px] leading-[1.4] font-bold">
            {room.hostNickname}님의 케미방에
            <br />
            초대받았어요
          </h1>
          <p className="mt-2.5 text-[13px] leading-[1.55] text-white/85">
            질문 12개에 답하면 케미 점수가 나와요
          </p>
        </header>

        <div className="mt-6.5">
          <NicknameField input={input} />
        </div>

        {/*
          목업 05 와 같은 문구다. 중복 판정은 공백을 전부 지우고 대소문자를 통합한 값으로
          하므로 `지 은` 과 `지은` 이 같은 이름이 된다 (PRD 7장, 12장).
          지우는 것은 판정용 키뿐이고 화면에 보이는 이름에는 공백이 그대로 남는다.
        */}
        <div className="mt-4 rounded-2xl bg-surface px-4.5 py-4 text-left text-[13.5px] leading-[1.65] text-[#5a5470]">
          대소문자와 띄어쓰기만 다른 이름도 같은 이름으로 봐요.
          <br />
          <b className="font-bold text-brand-deep">지은 · 지 은 · JIEUN · jieun</b>{" "}
          은 모두 같아요.
        </div>
      </div>

      <div className="pb-safe pt-6">
        <Button type="submit" disabled={submitting}>
          {submitting ? "참여하는 중..." : "참여하기"}
        </Button>
      </div>
    </form>
  );
}
