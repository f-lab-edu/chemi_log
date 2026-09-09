import type { Choice } from "./types";

interface ChoiceButtonProps {
  choice: Choice;
  label: string;
  selected: boolean;
  onSelect: () => void;
}

/**
 * 2지선다 하나 (목업 03, 06).
 *
 * PRD 4장이 "엄지 조작이 쉬운 큰 버튼" 을 요구한다. 글자 수에 따라 높이가 달라지지만
 * 위아래 여백만으로도 최소 44px 을 넘는다.
 *
 * 선택 여부를 색으로만 알리지 않는다. PRD 4장이 "색상만으로 상태를 구분하지 않는다" 고
 * 요구해서 체크 표시를 함께 둔다. `aria-pressed` 로 스크린리더에도 상태가 전달된다.
 */
export function ChoiceButton({
  choice,
  label,
  selected,
  onSelect,
}: ChoiceButtonProps) {
  return (
    <button
      type="button"
      onClick={onSelect}
      aria-pressed={selected}
      className={`relative w-full rounded-[18px] border-[1.5px] px-5 py-5 text-left text-base leading-[1.5] text-[#2e2a3d] transition-colors ${
        selected ? "border-brand bg-brand-soft" : "border-line bg-white"
      }`}
    >
      <span
        className={`mb-1.5 block text-xs font-extrabold tracking-wider ${
          selected ? "text-brand" : "text-ink-mute"
        }`}
      >
        {choice}
      </span>
      {label}
      {selected && (
        <span
          aria-hidden
          className="absolute top-5 right-4.5 flex size-6 items-center justify-center rounded-full bg-brand text-[13px] text-white"
        >
          ✓
        </span>
      )}
    </button>
  );
}
