import { AnswerFlow } from "@/features/question/AnswerFlow";

/**
 * 03·06 질문 답변 (위키 `UI-MVP`).
 *
 * 방장과 참여자가 같은 화면을 쓴다. 방 생성 시 고정된 같은 질문 세트를 같은 순서로 받는다.
 */
export default async function AnswerPage({
  params,
}: PageProps<"/rooms/[shareCode]/answer">) {
  const { shareCode } = await params;
  return <AnswerFlow shareCode={shareCode} />;
}
