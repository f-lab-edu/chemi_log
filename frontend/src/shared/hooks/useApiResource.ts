"use client";

import { useCallback, useEffect, useState } from "react";
import { ApiError } from "@/shared/api/client";

export type ApiResource<T> =
  | { status: "loading" }
  | { status: "ready"; data: T }
  | { status: "error"; error: ApiError };

/**
 * 화면이 필요한 데이터를 읽고 로딩과 오류 상태를 함께 돌려준다.
 *
 * 서버 컴포넌트에서 읽지 않는 이유는 rewrite 다. `apiFetch` 는 상대 경로를 쓰고 그 경로는
 * 브라우저에서만 rewrite 를 탄다. 서버에서 부르려면 절대 URL 이 필요하고, 그러면 백엔드를
 * 직접 부르게 되어 참여자 토큰 쿠키 전달 근거가 사라진다 (`docs/architecture.md`).
 *
 * `key` 가 바뀌면 다시 읽는다. `load` 를 의존성에 넣지 않는 것은 호출부에서 인라인 화살표
 * 함수를 쓰면 매 렌더마다 새 함수가 되어 무한히 다시 읽기 때문이다.
 */
export function useApiResource<T>(
  load: () => Promise<T>,
  key: string,
): ApiResource<T> & { reload: () => void } {
  const [state, setState] = useState<ApiResource<T>>({ status: "loading" });
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    // 화면을 떠난 뒤 도착한 응답으로 상태를 바꾸지 않는다.
    let alive = true;
    load().then(
      (data) => {
        if (alive) setState({ status: "ready", data });
      },
      (error: unknown) => {
        if (!alive) return;
        setState({
          status: "error",
          error:
            error instanceof ApiError
              ? error
              : new ApiError("INTERNAL_ERROR", "잠시 후 다시 시도해 주세요.", 0),
        });
      },
    );
    return () => {
      alive = false;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [key, attempt]);

  const reload = useCallback(() => {
    setState({ status: "loading" });
    setAttempt((n) => n + 1);
  }, []);

  return { ...state, reload };
}
