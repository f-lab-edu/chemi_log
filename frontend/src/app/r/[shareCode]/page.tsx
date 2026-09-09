import { RoomEntry } from "@/features/room/RoomEntry";

/**
 * 05 초대 링크 진입 (위키 `UI-MVP`).
 *
 * 경로가 `/rooms/...` 가 아니라 `/r/...` 인 것은 목업이 `chemilog.app/r/<코드>` 로 그렸기
 * 때문이다. 카카오톡 같은 곳에 붙었을 때 줄바꿈되지 않을 만큼 짧아야 한다.
 */
export default async function InviteEntryPage({
  params,
}: PageProps<"/r/[shareCode]">) {
  const { shareCode } = await params;
  return <RoomEntry shareCode={shareCode} />;
}
