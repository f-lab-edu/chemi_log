"use client";

import { Button } from "@/shared/ui/Button";
import { StateScreen } from "@/shared/ui/StateScreen";

/**
 * 렌더링 중 터진 오류 (PRD 4장 "오류 상태를 명확한 화면으로 제공한다").
 *
 * API 실패는 각 화면이 `RoomErrorScreen` 으로 코드별로 안내한다. 여기 오는 것은
 * 거기서 잡지 못한 것들이라 원인을 특정할 수 없다. `error.message` 를 화면에 쓰지 않는
 * 이유가 그것이다. 사용자가 읽을 수 있는 문구라는 보장이 없다.
 */
export default function Error({ reset }: { error: Error; reset: () => void }) {
  return (
    <StateScreen
      emoji="😵"
      title={<>문제가 생겼어요</>}
      description={
        <>
          잠시 후 다시 시도해 주세요.
          <br />
          계속 이러면 링크를 다시 열어 보세요.
        </>
      }
      action={
        <Button variant="primary" onClick={reset}>
          다시 시도
        </Button>
      }
    />
  );
}
