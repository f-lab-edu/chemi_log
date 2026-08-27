"use client";

import { useSyncExternalStore } from "react";

/** 구독할 것이 없다. 아래 값들은 한 번 정해지면 바뀌지 않는다. */
const noopSubscribe = () => () => {};

/**
 * 서버에서는 알 수 없는 브라우저 값을 hydration 불일치 없이 읽는다.
 *
 * `useEffect` 로 읽어 `setState` 하면 빈 값으로 한 번, 실제 값으로 또 한 번 그려진다.
 * `useSyncExternalStore` 는 서버 스냅샷과 클라이언트 스냅샷을 따로 받아 React 가
 * hydration 시점에 맞춰 준다.
 *
 * `read` 는 호출할 때마다 같은 값을 돌려줘야 한다. 매번 새 객체를 만들면 무한 렌더가 된다.
 */
export function useBrowserValue<T>(read: () => T, serverValue: T): T {
  return useSyncExternalStore(noopSubscribe, read, () => serverValue);
}
