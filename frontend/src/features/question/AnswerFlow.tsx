"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { getRoom } from "@/features/room/api";
import { RoomErrorScreen } from "@/features/room/RoomErrorScreen";
import type { RoomSummary } from "@/features/room/types";
import { ApiError } from "@/shared/api/client";
import { useApiResource } from "@/shared/hooks/useApiResource";
import { Button } from "@/shared/ui/Button";
import { LoadingScreen } from "@/shared/ui/StateScreen";
import { getRoomQuestions, submitAnswers } from "./api";
import { CATEGORY } from "./category";
import { ChoiceButton } from "./ChoiceButton";
import { clearDraft, loadDraft, saveDraft, type AnswerDraft } from "./draft";
import type { Choice, RoomQuestion } from "./types";

const TOTAL = 12;

interface LoadedRoom {
  room: RoomSummary;
  questions: RoomQuestion[];
}

/**
 * 03·06 질문 답변 (위키 `UI-MVP`).
 *
 * 방장과 참여자가 같은 화면을 쓴다. 방 생성 시점에 고정된 같은 질문 세트를 같은 순서로
 * 받기 때문이다 (PRD 8장). 갈리는 것은 제출한 뒤 어디로 가느냐뿐이다.
 */
export function AnswerFlow({ shareCode }: { shareCode: string }) {
  const router = useRouter();
  const resource = useApiResource<LoadedRoom>(
    () =>
      Promise.all([getRoom(shareCode), getRoomQuestions(shareCode)]).then(
        ([room, { questions }]) => ({ room, questions }),
      ),
    shareCode,
  );

  // 아직 읽는 중이면 판정하지 않는다. `me` 를 성급히 `null` 로 보면 로딩 중에 참여 화면으로 튄다.
  const redirect =
    resource.status === "ready"
      ? destinationFor(resource.data.room.me, shareCode)
      : null;

  useEffect(() => {
    if (redirect) router.replace(redirect);
  }, [redirect, router]);

  if (resource.status === "loading") {
    return <LoadingScreen message="질문을 준비하고 있어요" />;
  }
  if (resource.status === "error") {
    return <RoomErrorScreen error={resource.error} onRetry={resource.reload} />;
  }
  // 이동이 끝날 때까지 문항이 잠깐 보이지 않게 막는다.
  if (redirect) {
    return <LoadingScreen message="이어서 진행할게요" />;
  }
  return (
    <AnswerSheet
      shareCode={shareCode}
      room={resource.data.room}
      questions={resource.data.questions}
    />
  );
}

/**
 * 이 화면에 있으면 안 되는 사람을 어디로 보낼지 정한다. 머물러야 하면 `null` 이다.
 *
 * `RoomEntry` 와 같은 판정을 여기서도 한다. 답변 화면은 링크로 직접 열 수 있고
 * 새로고침으로도 들어온다. 걸러 내지 않으면 이미 제출한 사람이 12문항을 다시 답한 뒤
 * 마지막 버튼에서야 `409 ALREADY_SUBMITTED` 를 보게 된다 (PRD 12장 재접속).
 */
function destinationFor(
  me: RoomSummary["me"],
  shareCode: string,
): string | null {
  // 참여자 토큰이 없으면 제출할 수 없다 (PRD 7장). 참여부터 하게 돌려보낸다.
  if (!me) return `/r/${shareCode}`;
  if (me.answerStatus !== "SUBMITTED") return null;
  // 방장은 제출을 마치면 초대 링크를, 참여자는 참여 현황을 본다.
  return me.host
    ? `/rooms/${shareCode}/invite`
    : `/rooms/${shareCode}/status`;
}

function AnswerSheet({
  shareCode,
  room,
  questions,
}: {
  shareCode: string;
  room: RoomSummary;
  questions: RoomQuestion[];
}) {
  const router = useRouter();
  // `AnswerFlow` 가 데이터를 다 읽은 뒤에만 이 컴포넌트를 그린다. 서버 렌더와 hydration
  // 첫 렌더는 둘 다 `LoadingScreen` 이라, 초기값에서 저장소를 읽어도 불일치가 없다.
  const [draft, setDraft] = useState<AnswerDraft>(() =>
    loadDraft(shareCode, questions),
  );
  const [storable, setStorable] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [failure, setFailure] = useState<ApiError | null>(null);

  const { answers, index } = draft;
  const question = questions[index];
  const category = CATEGORY[question.category];
  const selected = answers[question.displayOrder];
  const isLast = index === questions.length - 1;

  // 이벤트 핸들러 안에서만 저장한다. `useEffect` 로 상태 변화를 뒤쫓으면 복구한 값을
  // 마운트 직후에 그대로 다시 쓰게 되고, 저장 실패를 `setState` 로 알리는 자리가
  // `react-hooks/set-state-in-effect` 에 걸린다 (`docs/conventions.md`).
  function update(next: AnswerDraft) {
    setDraft(next);
    setStorable(saveDraft(shareCode, next));
  }

  function choose(choice: Choice) {
    update({
      ...draft,
      answers: { ...answers, [question.displayOrder]: choice },
    });
    setFailure(null);
  }

  async function handleNext() {
    if (!selected || submitting) return;
    if (!isLast) {
      update({ ...draft, index: index + 1 });
      return;
    }

    setSubmitting(true);
    setFailure(null);
    try {
      await submitAnswers(shareCode, answers);
      // 제출이 끝나면 임시 답변은 쓸 일이 없다. 남겨 두면 다음 방문에서 이미 제출한
      // 사람에게 옛 선택이 되살아난 화면이 잠깐 보인다.
      clearDraft(shareCode);
      // 방장이 제출하면 방이 OPEN 이 되고 초대 링크가 열린다 (PRD 5장).
      // 참여자는 다른 사람들의 진행 상황을 보는 화면으로 간다.
      router.replace(
        room.me?.host
          ? `/rooms/${shareCode}/invite`
          : `/rooms/${shareCode}/status`,
      );
    } catch (error) {
      // 네트워크 오류에서는 선택을 유지하고 재시도 CTA 를 준다 (PRD 12장).
      setFailure(
        error instanceof ApiError
          ? error
          : new ApiError("INTERNAL_ERROR", "잠시 후 다시 시도해 주세요.", 0),
      );
      setSubmitting(false);
    }
  }

  return (
    <div className="flex flex-1 flex-col">
      <nav className="flex h-13 flex-none items-center px-5">
        <button
          type="button"
          onClick={() => update({ ...draft, index: index - 1 })}
          disabled={index === 0}
          aria-label="이전 질문"
          className="-ml-3 flex size-11 items-center justify-center text-xl leading-none disabled:opacity-30"
        >
          ←
        </button>
      </nav>

      <div className="flex flex-1 flex-col px-6">
        <div>
          <div className="mb-2.5 flex items-center justify-between text-[13px] text-ink-mute">
            <span>
              <b className="font-bold text-brand">{index + 1}</b> / {TOTAL}
            </span>
            <span>{category.label}</span>
          </div>
          <div
            className="h-1.5 overflow-hidden rounded-full bg-[#efedf7]"
            role="progressbar"
            aria-valuenow={index + 1}
            aria-valuemin={1}
            aria-valuemax={TOTAL}
            aria-label="답변 진행률"
          >
            <div
              className="h-full rounded-full bg-brand transition-[width]"
              style={{ width: `${((index + 1) / TOTAL) * 100}%` }}
            />
          </div>
        </div>

        <div className="mt-8">
          <span
            className={`inline-flex items-center gap-1.5 rounded-full px-3.5 py-1.75 text-[13px] font-bold ${category.badge}`}
          >
            {category.emoji} {category.label}
          </span>
        </div>

        <h1 className="mt-4 text-[22px] leading-[1.42] font-bold tracking-tight">
          {question.content}
        </h1>

        <div className="mt-7 flex flex-col gap-3">
          <ChoiceButton
            choice="A"
            label={question.optionA}
            selected={selected === "A"}
            onSelect={() => choose("A")}
          />
          <ChoiceButton
            choice="B"
            label={question.optionB}
            selected={selected === "B"}
            onSelect={() => choose("B")}
          />
        </div>

        <p role="alert" className="mt-4 min-h-5 text-[13px] text-danger">
          {failure?.message}
        </p>

        {/* 저장소를 못 쓰는 환경에 제한 사항을 알린다 (PRD 17장 호환성). */}
        {!storable && (
          <p className="text-[13px] leading-relaxed text-ink-mute">
            이 브라우저에서는 선택을 임시로 저장할 수 없어요. 새로고침하면
            처음부터 다시 답해야 해요.
          </p>
        )}
      </div>

      <div className="px-6 pb-safe pt-2">
        <Button onClick={handleNext} disabled={!selected || submitting}>
          {submitting ? "제출하는 중..." : isLast ? "제출하기" : "다음 질문"}
        </Button>
      </div>
    </div>
  );
}
