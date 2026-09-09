"use client";

import { useState } from "react";
import Link from "next/link";
import { CATEGORY, CATEGORY_ORDER } from "@/features/question/category";
import { RoomErrorScreen } from "@/features/room/RoomErrorScreen";
import { inviteUrlOf } from "@/features/room/ShareInvite";
import { ApiError } from "@/shared/api/client";
import { useApiResource } from "@/shared/hooks/useApiResource";
import { useBrowserValue } from "@/shared/hooks/useBrowserValue";
import { COPY_MESSAGE, useShareLink } from "@/shared/hooks/useShareLink";
import { Button, buttonClass } from "@/shared/ui/Button";
import { LoadingScreen, StateScreen } from "@/shared/ui/StateScreen";
import { getMyPairs, getRoomResult, MY_PAIR_PAGE_SIZE } from "./api";
import { categoryReading, scoreReading } from "./reading";
import { ScoreGauge } from "./ScoreGauge";
import type { PairDetail, PairScore, RoomResult } from "./types";

/** 08 그룹 결과 (위키 `UI-MVP`). */
export function ResultView({ shareCode }: { shareCode: string }) {
  const resource = useApiResource<RoomResult>(
    () => getRoomResult(shareCode),
    shareCode,
  );

  if (resource.status === "loading") {
    return <LoadingScreen message="케미를 계산하고 있어요" />;
  }
  if (resource.status === "error") {
    return (
      <RoomErrorScreen
        error={resource.error}
        onRetry={resource.reload}
        shareCode={shareCode}
      />
    );
  }

  const result = resource.data;

  // 제출을 마친 사람이 2명 미만이면 계산할 Pair 가 없다 (PRD 9장).
  if (!result.featuredPair) {
    return (
      <StateScreen
        emoji="⏳"
        title={<>아직 결과를 만들 수 없어요</>}
        description={
          <>
            친구 한 명이 더 답변을 마치면
            <br />
            케미 점수를 볼 수 있어요.
          </>
        }
        action={
          <Link
            href={`/rooms/${shareCode}/status`}
            className={buttonClass("primary")}
          >
            참여 현황 보기
          </Link>
        }
      />
    );
  }

  return (
    <ResultSheet
      shareCode={shareCode}
      result={result}
      featured={result.featuredPair}
    />
  );
}

function ResultSheet({
  shareCode,
  result,
  featured,
}: {
  shareCode: string;
  result: RoomResult;
  featured: PairDetail;
}) {
  const origin = useBrowserValue(() => window.location.origin, "");
  const inviteUrl = origin ? inviteUrlOf(origin, shareCode) : "";
  const { canShare, copyState, share, copy } = useShareLink();

  // 참여자가 2명이면 순위와 카테고리 1위가 내 Pair 하나와 같아 보여 줄 것이 없다 (PRD 6장).
  const isGroup = result.submittedCount >= 3;

  const shareText = `${featured.participantA} × ${featured.participantB} 케미 ${featured.score}점! 너도 답해볼래?`;

  return (
    <div className="flex flex-1 flex-col px-6">
      <header className="-mx-6 bg-linear-158 from-[#6a48ff] via-[#9b5cff] to-[#c46af0] px-6 pt-5.5 pb-5 text-center text-white">
        {/* 화면의 제목이 곧 누구와 누구의 결과인가다. 스크린리더가 이것을 먼저 읽어야 한다. */}
        <h1 className="text-[13.5px] font-semibold opacity-90">
          {featured.participantA} × {featured.participantB} · 12문제 중{" "}
          {featured.matchCount}개 일치
        </h1>
        <ScoreGauge score={featured.score} />
        <p className="mt-2.75 text-sm leading-[1.55] font-semibold">
          {scoreReading(featured.score)}
        </p>
        {categoryReading(featured).map((line) => (
          <p key={line} className="text-sm leading-[1.55] font-semibold opacity-90">
            {line}
          </p>
        ))}
      </header>

      <CategoryTiles featured={featured} />

      {isGroup && (
        <>
          <SectionTitle title="전체 케미 TOP 3" note={pairCountNote(result)} />
          <ol>
            {result.topPairs.map((pair) => (
              <RankRow key={pairKey(pair)} pair={pair} highlight={pair.mine} />
            ))}
          </ol>

          <SectionTitle title="카테고리별 1위" />
          <ul>
            {result.categoryLeaders.map((leader) => (
              <li
                key={leader.category}
                className="flex items-center gap-2.75 border-b border-[#f4f2fa] px-1 py-2.5"
              >
                <span className="w-14 flex-none text-[13px] font-bold text-ink-sub">
                  {CATEGORY[leader.category].emoji}{" "}
                  {CATEGORY[leader.category].label}
                </span>
                <span className="flex-1 text-[14.5px] font-semibold">
                  {leader.pair.participantA} × {leader.pair.participantB}
                </span>
                {/* 전체 점수가 아니라 그 카테고리 점수다. 위 타일과 숫자가 맞아야 한다. */}
                <span className="text-[15px] font-extrabold tabular-nums">
                  {leader.score}
                </span>
              </li>
            ))}
          </ul>

          {result.twistPair && (
            <div className="mt-4 rounded-2xl bg-surface px-4.5 py-4 text-[13.5px] leading-[1.65] text-[#5a5470]">
              <b className="font-bold text-brand-deep">
                {CATEGORY[result.twistPair.category].label}에서 발견한 반전 케미
              </b>
              <br />
              {result.twistPair.pair.participantA} ×{" "}
              {result.twistPair.pair.participantB}는 전체 {result.twistPair.pair.score}
              점인데 {CATEGORY[result.twistPair.category].label}만{" "}
              {result.twistPair.categoryScore}점이에요.
            </div>
          )}

          <MyPairList shareCode={shareCode} first={result.myPairs} />
        </>
      )}

      <p className="mt-5 text-center text-[13px] leading-[1.6] text-ink-mute">
        친구가 더 참여하면 순위를 다시 계산해요.
      </p>

      <div
        className="mt-3 min-h-5 text-center text-[13px]"
        role="status"
        aria-live="polite"
      >
        {copyState !== "idle" && (
          <span className={copyState === "copied" ? "text-brand-deep" : "text-danger"}>
            {COPY_MESSAGE[copyState]}
          </span>
        )}
      </div>

      <div className="pb-safe pt-3">
        <Button
          variant="primary"
          disabled={!inviteUrl}
          onClick={() =>
            canShare ? share(inviteUrl, shareText) : copy(inviteUrl)
          }
        >
          결과 공유하기
        </Button>
        <Link href="/rooms/new" className={`${buttonClass("soft")} mt-3`}>
          새 케미방 만들기
        </Link>
      </div>
    </div>
  );
}

/** 카테고리 4개를 2 × 2 로 놓는다. 세로 목록보다 한눈에 비교된다 (목업 08). */
function CategoryTiles({ featured }: { featured: PairDetail }) {
  const byCategory = new Map(
    featured.categoryScores.map((s) => [s.category, s]),
  );
  return (
    <div className="mt-3.5 grid grid-cols-2 gap-2.25">
      {CATEGORY_ORDER.map((category) => {
        const score = byCategory.get(category);
        if (!score) return null;
        const style = CATEGORY[category];
        return (
          <div
            key={category}
            className="rounded-[15px] border-[1.5px] border-line px-3 py-2.25"
          >
            <div className="flex items-center justify-between text-[12.5px] font-bold text-ink-sub">
              <span>
                {style.emoji} {style.label}
              </span>
              {featured.bestCategory === category && (
                <em className="rounded-[5px] bg-[#edf7ef] px-1.5 py-0.5 text-[10.5px] font-extrabold not-italic text-[#2e9c69]">
                  가장 잘 맞음
                </em>
              )}
              {featured.worstCategory === category && (
                <em className="rounded-[5px] bg-[#fdefef] px-1.5 py-0.5 text-[10.5px] font-extrabold not-italic text-[#d9564f]">
                  가장 다름
                </em>
              )}
            </div>
            <div className="mt-0.5 text-[21px] font-extrabold tracking-tight tabular-nums">
              {score.score}
            </div>
            <div className="mt-1.75 h-1.25 overflow-hidden rounded-full bg-[#efedf7]">
              <div
                className="h-full rounded-full"
                style={{ width: `${score.score}%`, background: style.bar }}
              />
            </div>
          </div>
        );
      })}
    </div>
  );
}

function SectionTitle({ title, note }: { title: string; note?: string }) {
  return (
    <h2 className="mt-3 flex items-baseline justify-between text-[15px] font-bold">
      {title}
      {note && <span className="text-xs font-medium text-ink-mute">{note}</span>}
    </h2>
  );
}

function RankRow({ pair, highlight }: { pair: PairScore; highlight: boolean }) {
  return (
    <li
      className={`flex items-center gap-2.75 px-1 py-2.5 ${
        highlight
          ? "mt-0.5 rounded-[13px] border-b-transparent bg-[#f6f3ff] px-3 py-2.75"
          : "border-b border-[#f4f2fa]"
      }`}
    >
      <span className="w-5 flex-none text-sm font-extrabold text-brand">
        {pair.rank}
      </span>
      <span className="flex-1 text-[14.5px] font-semibold">
        {pair.participantA} × {pair.participantB}
        {pair.mine && (
          <em className="ml-1.5 rounded-[5px] bg-[#f1edff] px-1.5 py-0.5 text-[11px] font-bold not-italic text-brand">
            나
          </em>
        )}
      </span>
      <span className="text-[15px] font-extrabold tabular-nums">{pair.score}</span>
    </li>
  );
}

/**
 * 내 Pair 목록. 기본으로 접혀 있고 10개씩 더 불러온다 (PRD 11장).
 *
 * 전체를 한 번에 받지 않는 이유는 Pair 수가 참여자 수의 제곱에 비례하기 때문이다.
 */
function MyPairList({
  shareCode,
  first,
}: {
  shareCode: string;
  first: RoomResult["myPairs"];
}) {
  const [open, setOpen] = useState(false);
  const [items, setItems] = useState(first.items);
  const [hasMore, setHasMore] = useState(first.hasMore);
  const [loading, setLoading] = useState(false);
  const [failure, setFailure] = useState<string | null>(null);

  if (first.total === 0) return null;

  async function loadMore() {
    setLoading(true);
    setFailure(null);
    try {
      const page = await getMyPairs(shareCode, items.length);
      setItems((prev) => [...prev, ...page.items]);
      setHasMore(page.hasMore);
    } catch (error) {
      setFailure(
        error instanceof ApiError ? error.message : "잠시 후 다시 시도해 주세요.",
      );
    }
    setLoading(false);
  }

  return (
    <section className="mt-3">
      <button
        type="button"
        onClick={() => setOpen(!open)}
        aria-expanded={open}
        // 내 Pair 목록은 기본으로 접혀 있어 이 버튼이 그 내용에 닿는 유일한 길이다.
        // 주요 터치 영역은 44px 이상이어야 한다 (PRD 4장). py-2 만으로는 39px 이었다.
        className="flex min-h-11 w-full items-center justify-between py-2 text-[15px] font-bold"
      >
        내 Pair 전체
        <span className="text-xs font-medium text-ink-mute">
          {first.total}개 {open ? "접기" : "펼치기"}
        </span>
      </button>

      {open && (
        <>
          <ol>
            {items.map((pair) => (
              <RankRow key={pairKey(pair)} pair={pair} highlight={false} />
            ))}
          </ol>
          {failure && (
            <p role="alert" className="mt-2 text-[13px] text-danger">
              {failure}
            </p>
          )}
          {hasMore && (
            <Button
              variant="ghost"
              className="mt-3"
              onClick={loadMore}
              disabled={loading}
            >
              {loading ? "불러오는 중..." : `${MY_PAIR_PAGE_SIZE}개 더 보기`}
            </Button>
          )}
        </>
      )}
    </section>
  );
}

/** `N(N-1)/2` (PRD 6.2). */
function pairCount(participants: number): number {
  return (participants * (participants - 1)) / 2;
}

/**
 * 순위 옆에 붙는 요약.
 *
 * **Pair 수는 제출을 마친 사람 기준이다.** 아직 답하지 않은 사람과는 비교할 답이 없어
 * Pair 가 생기지 않는다. 참여자 수로 세면 10명 들어와 3명 제출한 방에서
 * "10명 · Pair 45개" 라고 써 놓고 목록에는 3개만 보여주게 된다.
 *
 * 두 수가 다르면 둘 다 밝힌다. "3명" 만 쓰면 나머지 7명이 어디 갔는지 알 수 없다.
 */
function pairCountNote(result: RoomResult): string {
  const pairs = pairCount(result.submittedCount);
  return result.participantCount === result.submittedCount
    ? `${result.submittedCount}명 · Pair ${pairs}개`
    : `${result.participantCount}명 중 ${result.submittedCount}명 완료 · Pair ${pairs}개`;
}

/**
 * 서버가 A, B 를 정규화된 닉네임 오름차순으로 고정하므로 이 조합이 Pair 를 유일하게 가리킨다.
 *
 * **구분자를 공백으로 두면 안 된다.** 표시값에는 내부 공백이 남으므로 (PRD 7장) 한 방에
 * `지 은`, `민수`, `지`, `은 민수` 가 함께 있을 수 있다. 넷의 `nickname_key` 는
 * `지은`, `민수`, `지`, `은민수` 로 전부 달라 UNIQUE 가 막지 않는다. 그때 Pair
 * `(지 은, 민수)` 와 `(지, 은 민수)` 가 둘 다 `"지 은 민수"` 가 되어 React key 가 겹친다.
 *
 * U+001F 는 `FORBIDDEN_PATTERN` 의 `\p{Cc}` 에 걸려 닉네임에 들어갈 수 없다
 * (`features/room/nickname.ts`). 그래서 나뉘는 자리를 유일하게 표시한다.
 */
function pairKey(pair: PairScore): string {
  return `${pair.participantA}\u001f${pair.participantB}`;
}
