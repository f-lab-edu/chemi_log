"use client";

import { useState } from "react";
import {
  NICKNAME_MAX_LENGTH,
  NICKNAME_PROBLEM_MESSAGE,
  nicknameLength,
  normalizeNickname,
  validateNickname,
  type NicknameProblem,
} from "./nickname";

/**
 * 닉네임 입력 상태.
 *
 * 방 만들기(목업 02)와 초대 링크 참여(목업 05)가 같은 규칙을 쓴다. 두 화면의 차이는
 * 제출했을 때 무엇을 부르고 어디로 가느냐뿐이라 입력 쪽만 여기로 모았다.
 */
export interface NicknameInput {
  value: string;
  /** 다듬은 값. 서버로 보내고 화면에도 이 값을 기준으로 센다. */
  nickname: string;
  problem: NicknameProblem | null;
  /** 화면에 띄울 문구. 없으면 `null`. */
  message: string | null;
  setValue: (value: string) => void;
  markTouched: () => void;
  setServerMessage: (message: string | null) => void;
}

export function useNicknameInput(): NicknameInput {
  const [value, setValueState] = useState("");
  const [touched, setTouched] = useState(false);
  const [serverMessage, setServerMessage] = useState<string | null>(null);

  const nickname = normalizeNickname(value);
  const problem = validateNickname(nickname);

  return {
    value,
    nickname,
    problem,
    // 입력하는 동안 빨간 글씨를 띄우지 않는다. 한 글자 쳤을 때 "너무 짧다" 고 말하는 화면이 된다.
    message: touched && problem ? NICKNAME_PROBLEM_MESSAGE[problem] : serverMessage,
    setValue: (next) => {
      setValueState(next);
      // 값을 고치는 순간 서버가 준 오류는 더 이상 그 값에 대한 것이 아니다.
      setServerMessage(null);
    },
    markTouched: () => setTouched(true),
    setServerMessage,
  };
}

/** 라벨, 글자 수, 입력창, 오류 문구를 한 덩어리로 그린다 (목업 02, 05). */
export function NicknameField({ input }: { input: NicknameInput }) {
  return (
    <div className="text-left">
      <div className="mb-2.5 flex justify-between text-[13.5px] font-semibold text-ink-sub">
        <label htmlFor="nickname">닉네임</label>
        <span className="font-medium text-ink-mute">
          {nicknameLength(input.nickname)} / {NICKNAME_MAX_LENGTH}
        </span>
      </div>
      <input
        id="nickname"
        value={input.value}
        onChange={(event) => input.setValue(event.target.value)}
        onBlur={input.markTouched}
        placeholder="닉네임을 입력하세요"
        autoComplete="off"
        enterKeyHint="done"
        aria-invalid={input.message !== null}
        aria-describedby={input.message ? "nickname-error" : undefined}
        className={`h-14 w-full rounded-2xl border-[1.5px] px-4 text-base outline-none placeholder:text-[#beb9ce] ${
          input.message ? "border-danger-line" : "border-line focus:border-brand"
        }`}
      />
      <p
        id="nickname-error"
        role="alert"
        className="mt-2.5 min-h-5 text-[13px] text-danger"
      >
        {input.message}
      </p>
    </div>
  );
}
