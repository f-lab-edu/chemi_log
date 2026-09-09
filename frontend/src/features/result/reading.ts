import { CATEGORY } from "@/features/question/category";
import type { PairDetail } from "./types";

/**
 * 결과 해석 문구 (PRD 11장 "결과 해석 문구").
 *
 * 서버에서 받지 않고 화면에서 만든다. 문구는 UI 의 몫이고, 같은 점수라도 화면마다
 * 다르게 쓸 수 있어야 한다. 서버가 문자열을 만들어 내려주면 문구를 고칠 때마다 배포가 얽힌다.
 */

/**
 * 점수를 말로 바꾼다.
 *
 * **낮은 점수를 관계가 나쁘다는 뜻으로 쓰지 않는다** (PRD 6.1 "관계 비하 방지").
 * 12문항 2지선다라 무작위로 골라도 평균 50점이 나온다. 점수가 낮은 것은 취향이 다르다는
 * 뜻이지 사이가 나쁘다는 뜻이 아니다.
 */
export function scoreReading(score: number): string {
  if (score >= 83) return "취향이 거의 똑같아요";
  if (score >= 67) return "잘 통하는 편이에요";
  if (score >= 50) return "비슷한 부분과 다른 부분이 반반이에요";
  if (score >= 33) return "서로 다른 점이 많아 더 물어볼 게 많겠어요";
  return "취향이 정반대라 오히려 재미있는 사이예요";
}

/**
 * 카테고리 요약 두 줄 (목업 08 의 `.read`).
 *
 * 카테고리 점수는 0, 33, 67, 100 네 값뿐이라 동점이 잦다. 서버가 하나로 좁히지 못하면
 * `null` 을 주고, 그때는 그 줄을 쓰지 않는다.
 */
export function categoryReading(pair: PairDetail): string[] {
  const lines: string[] = [];
  if (pair.bestCategory) {
    lines.push(`${CATEGORY[pair.bestCategory].label}는 거의 똑같아요.`);
  }
  if (pair.worstCategory) {
    lines.push(`${CATEGORY[pair.worstCategory].label}는 서로 많이 달라요.`);
  }
  return lines;
}
