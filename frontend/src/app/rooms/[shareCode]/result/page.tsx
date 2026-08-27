import { ResultView } from "@/features/result/ResultView";

/** 08 그룹 결과 (위키 `UI-MVP`). */
export default async function ResultPage({
  params,
}: PageProps<"/rooms/[shareCode]/result">) {
  const { shareCode } = await params;
  return <ResultView shareCode={shareCode} />;
}
