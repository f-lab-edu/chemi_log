/**
 * 점수 링 (목업 08 의 `.gauge`).
 *
 * `conic-gradient` 로 그리고 가운데를 `mask` 로 뚫는다. 안쪽을 단색으로 덮으면
 * 뒤 그라데이션과 이음매가 보인다.
 */
export function ScoreGauge({ score }: { score: number }) {
  return (
    <div className="relative mx-auto mt-3 w-24">
      <div
        aria-hidden
        className="size-24 rounded-full"
        style={{
          background: `conic-gradient(#fff 0 ${score}%, rgba(255,255,255,.26) ${score}%)`,
          WebkitMask: "radial-gradient(closest-side, transparent 76%, #000 77%)",
          mask: "radial-gradient(closest-side, transparent 76%, #000 77%)",
        }}
      />
      <div className="absolute inset-0 flex flex-col items-center justify-center">
        <b className="text-[34px] leading-none font-extrabold tracking-tighter">
          {score}
        </b>
        <span className="mt-0.5 text-xs font-bold opacity-85">점</span>
      </div>
    </div>
  );
}
