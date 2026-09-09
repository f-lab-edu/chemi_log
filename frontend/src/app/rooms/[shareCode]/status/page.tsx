import { ParticipantStatus } from "@/features/participant/ParticipantStatus";

/** 07 참여 현황 (위키 `UI-MVP`). */
export default async function StatusPage({
  params,
}: PageProps<"/rooms/[shareCode]/status">) {
  const { shareCode } = await params;
  return <ParticipantStatus shareCode={shareCode} />;
}
