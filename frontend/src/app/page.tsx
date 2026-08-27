import Link from "next/link";
import { buttonClass } from "@/shared/ui/Button";

/**
 * 01 홈 (위키 `UI-MVP`).
 *
 * 진입점은 케미방 만들기 하나다. 참여는 초대 링크로만 들어온다.
 * 공유 코드를 직접 입력하는 진입점은 PRD 5장과 11장 어디에도 없어서 두지 않았다.
 */
export default function HomePage() {
  return (
    <div className="flex flex-1 flex-col bg-linear-168 from-[#5f3dff] via-[#8b4fff] to-[#c765e8] text-white">
      <main className="flex flex-1 flex-col items-center justify-center px-6 text-center">
        <div
          className="flex size-19 items-center justify-center rounded-3xl border-[1.5px] border-white/30 bg-white/20 text-4xl"
          aria-hidden
        >
          🧪
        </div>
        <h1 className="mt-7 text-[29px] leading-[1.4] font-bold tracking-tight">
          같은 질문,
          <br />
          다른 대답.
        </h1>
        <p className="mt-2.5 text-[15px] leading-[1.6] text-white/85">
          친구들과 12개 질문에 답하고
          <br />
          서로의 케미 점수를 확인해요.
        </p>
      </main>

      <div className="px-6 pb-safe">
        <Link href="/rooms/new" className={buttonClass("white")}>
          케미방 만들기
        </Link>
        <p className="mt-3.5 text-center text-[12.5px] text-white/70">
          질문 12개 · 카테고리 4종 · 2지선다
        </p>
      </div>
    </div>
  );
}
