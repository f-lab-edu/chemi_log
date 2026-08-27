import { LoadingScreen } from "@/shared/ui/StateScreen";

/**
 * 라우트 전환 중 화면 (PRD 4장 "로딩 상태를 명확한 화면으로 제공한다").
 *
 * 데이터를 읽는 동안의 로딩은 각 화면이 `useApiResource` 로 따로 그린다.
 * 이 파일이 맡는 것은 화면과 화면 사이의 빈 시간이다.
 */
export default function Loading() {
  return <LoadingScreen />;
}
