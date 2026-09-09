import { InviteScreen } from "@/features/room/InviteScreen";

/** 04 초대 링크 (위키 `UI-MVP`). */
export default async function InvitePage({
  params,
}: PageProps<"/rooms/[shareCode]/invite">) {
  const { shareCode } = await params;
  return <InviteScreen shareCode={shareCode} />;
}
