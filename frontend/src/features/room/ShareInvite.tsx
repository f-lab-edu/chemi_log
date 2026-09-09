"use client";

import type { ReactNode } from "react";
import { useBrowserValue } from "@/shared/hooks/useBrowserValue";
import { COPY_MESSAGE, useShareLink } from "@/shared/hooks/useShareLink";
import { Button } from "@/shared/ui/Button";

/**
 * 초대 링크 경로. 참여 화면(`app/r/[shareCode]`)과 같아야 한다.
 *
 * `features/*\/api.ts` 와 같이 인코딩한다. `share_code` 는 서버가 만드는 base64url 22자라
 * 정상 경로에서는 바뀌는 문자가 없지만, 이 값은 URL 파라미터에서 그대로 흘러들어온다.
 * 인코딩하지 않으면 `/rooms/a%20b/invite` 로 들어온 사람이 공백이 든 초대 링크를 복사한다.
 */
export function inviteUrlOf(origin: string, shareCode: string): string {
  return `${origin}/r/${encodeURIComponent(shareCode)}`;
}

/**
 * 초대 링크를 보여주고 공유한다 (목업 04).
 *
 * `footer` 는 아래 버튼 더미에 이어 붙인다. 목업에는 공유와 복사 둘뿐이지만 이 화면은
 * 방장이 제출을 마친 뒤 도착하는 곳이라, 그것만으로는 앱 안으로 돌아갈 길이 없다.
 */
export function ShareInvite({
  shareCode,
  footer,
}: {
  shareCode: string;
  footer?: ReactNode;
}) {
  const origin = useBrowserValue(() => window.location.origin, "");
  const inviteUrl = origin ? inviteUrlOf(origin, shareCode) : "";
  const { canShare, copyState, share, copy } = useShareLink();

  return (
    <>
      <div className="mt-6 rounded-2xl border-[1.5px] border-dashed border-[#d8d2f5] bg-surface p-4.5 text-center">
        <p className="text-xs text-ink-mute">초대 링크</p>
        <p className="mt-2 font-mono text-[15px] leading-[1.5] font-semibold break-all text-brand-deep">
          {/* 링크는 클라이언트에서 만든다. 비어 있는 첫 렌더에서 높이가 무너지지 않게 자리를 잡아 둔다. */}
          {inviteUrl || " "}
        </p>
      </div>

      <div
        className="mt-3 min-h-5 text-center text-[13px]"
        role="status"
        aria-live="polite"
      >
        {copyState !== "idle" && (
          <span className={copyState === "copied" ? "text-brand-deep" : "text-danger"}>
            {COPY_MESSAGE[copyState]}
          </span>
        )}
      </div>

      <div className="mt-auto px-0 pb-safe pt-6">
        {canShare && (
          <Button
            variant="primary"
            className="mb-3"
            onClick={() =>
              share(inviteUrl, "질문 12개에 답하고 우리 케미 점수를 확인해요.")
            }
          >
            친구에게 공유하기
          </Button>
        )}
        <Button
          variant={canShare ? "soft" : "primary"}
          onClick={() => copy(inviteUrl)}
          disabled={!inviteUrl}
        >
          링크 복사하기
        </Button>
        {footer}
      </div>
    </>
  );
}
