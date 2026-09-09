import Link from "next/link";
import type { ReactNode } from "react";
import { buttonClass } from "./Button";

interface StateScreenProps {
  emoji: string;
  title: ReactNode;
  description?: ReactNode;
  /** 아래에 붙일 버튼. 없으면 홈으로 가는 버튼도 그리지 않는다. */
  action?: ReactNode;
}

/**
 * 화면 전체를 채우는 상태 안내 (로딩, 오류, 빈 상태).
 *
 * PRD 4장이 "로딩, 빈 상태, 오류 상태를 각각 명확한 화면으로 제공하라" 고 요구한다.
 * 목업에는 없어서 기존 화면의 여백과 글자 크기를 따랐다.
 */
export function StateScreen({
  emoji,
  title,
  description,
  action,
}: StateScreenProps) {
  return (
    <div className="flex flex-1 flex-col px-6">
      <div className="flex flex-1 flex-col items-center justify-center text-center">
        <div className="text-5xl" aria-hidden>
          {emoji}
        </div>
        <h1 className="mt-6 text-[22px] leading-[1.42] font-bold tracking-tight">
          {title}
        </h1>
        {description && (
          <p className="mt-2.5 text-[15px] leading-[1.6] text-ink-sub">
            {description}
          </p>
        )}
      </div>
      {action && <div className="pb-safe pt-6">{action}</div>}
    </div>
  );
}

/** 어느 화면에서든 마지막으로 갈 곳은 홈이다 (PRD 12장 "새 케미방 만들기로 이동"). */
export function NewRoomLink({ label = "새 케미방 만들기" }: { label?: string }) {
  return (
    <Link href="/rooms/new" className={buttonClass("primary")}>
      {label}
    </Link>
  );
}

/**
 * 로딩 화면.
 *
 * `animate-pulse` 만 쓰고 회전 스피너를 두지 않는다. PRD 17장이 접근성을 요구하고,
 * 움직임을 줄이는 설정을 켠 사용자에게 회전은 피로를 준다.
 */
export function LoadingScreen({ message = "불러오는 중이에요" }: { message?: string }) {
  return (
    <div className="flex flex-1 flex-col items-center justify-center px-6 text-center">
      <div className="size-14 animate-pulse rounded-2xl bg-surface" aria-hidden />
      <p className="mt-5 text-[15px] text-ink-sub" role="status" aria-live="polite">
        {message}
      </p>
    </div>
  );
}
