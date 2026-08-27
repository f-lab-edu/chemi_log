"use client";

import { useState } from "react";
import { useBrowserValue } from "./useBrowserValue";

export type CopyState = "idle" | "copied" | "failed";

/**
 * 링크 공유. Web Share API 를 우선 쓰고 안 되면 복사로 대신한다 (PRD 4장).
 *
 * `navigator.share` 유무는 서버에서 알 수 없어 `useBrowserValue` 로 읽는다.
 */
export function useShareLink() {
  const canShare = useBrowserValue(
    () => typeof navigator.share === "function",
    false,
  );
  const [copyState, setCopyState] = useState<CopyState>("idle");

  async function copy(url: string) {
    // 이전 결과를 먼저 지운다. 같은 버튼을 두 번 눌렀을 때 문구가 그대로면
    // 두 번째 시도가 반영됐는지 화면으로 알 수 없다.
    setCopyState("idle");
    try {
      await navigator.clipboard.writeText(url);
      setCopyState("copied");
    } catch {
      // clipboard 는 보안 컨텍스트가 아니거나 문서에 포커스가 없으면 거부된다.
      setCopyState("failed");
    }
  }

  async function share(url: string, text: string) {
    // 여기서도 지운다. 공유에 성공하거나 사용자가 공유 시트를 닫는 경로는 `copyState` 를
    // 건드리지 않으므로, 지우지 않으면 앞선 복사 실패 문구가 그대로 남는다.
    // 복사가 거부된 뒤 공유에 성공한 사람이 "복사하지 못했어요" 를 보게 된다.
    // 이 안내 영역은 `aria-live="polite"` 라 스크린리더에도 읽힌다.
    setCopyState("idle");
    try {
      await navigator.share({ title: "케미로그", text, url });
    } catch (error) {
      // 사용자가 공유 시트를 닫으면 AbortError 로 reject 된다. 오류가 아니다.
      if (error instanceof DOMException && error.name === "AbortError") {
        return;
      }
      // `navigator.share` 가 있어도 호출이 실패하는 환경이 있다. 카카오톡 같은 인앱 브라우저,
      // `allow="web-share"` 가 없는 iframe 이 그렇다. 여기서 조용히 넘기면 링크를 전달할
      // 방법이 화면에서 사라지므로 복사로 대신한다.
      await copy(url);
    }
  }

  return { canShare, copyState, share, copy };
}

export const COPY_MESSAGE: Record<Exclude<CopyState, "idle">, string> = {
  copied: "링크를 복사했어요.",
  failed: "복사하지 못했어요. 링크를 길게 눌러 직접 복사해 주세요.",
};
