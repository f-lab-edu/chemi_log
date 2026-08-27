import type { QuestionCategory } from "@/features/question/category";

/**
 * Pair 하나의 전체 점수.
 *
 * `참여자 A 와 B 는 정규화된 닉네임 오름차순으로 고정한다` (PRD 10장). 프론트는 그 순서를
 * 그대로 표시한다. 다시 정렬하면 서버가 정한 공동 순위의 안정 정렬이 깨진다.
 */
export interface PairScore {
  participantA: string;
  participantB: string;
  /** 12문항 중 일치 수. 목업 08 의 "12문제 중 8개 일치" 에 쓴다. */
  matchCount: number;
  /** `(일치 수 / 12) * 100` 을 반올림한 정수 (PRD 10장). */
  score: number;
  /** 공동 순위가 있으므로 배열 인덱스로 순위를 매기지 않는다. */
  rank: number;
  /** 내가 낀 Pair 인지. 서버가 참여자 토큰으로 판정한다. */
  mine: boolean;
}

export interface CategoryScore {
  category: QuestionCategory;
  /** 카테고리당 3문항이므로 0~3 이다. */
  matchCount: number;
  /** `(카테고리 일치 수 / 3) * 100` 을 반올림한 정수. 0, 33, 67, 100 네 값뿐이다. */
  score: number;
}

/**
 * 크게 보여주는 Pair 하나. 그룹 모드에서는 내 Pair 중 점수가 가장 높은 것,
 * 2인 모드에서는 유일한 Pair 다 (PRD 11장 "내 Pair 결과 우선 노출").
 */
export interface PairDetail extends PairScore {
  categoryScores: CategoryScore[];
  /**
   * 가장 잘 맞는 카테고리와 가장 다른 카테고리.
   *
   * 카테고리 점수는 0, 33, 67, 100 네 값뿐이라 동점이 자주 난다. PRD 11장은 단수를
   * 요구하는데 동점 규칙이 없어서, 서버가 하나로 좁히지 못하면 `null` 을 준다.
   * 화면은 `null` 이면 그 배지를 그리지 않는다.
   */
  bestCategory: QuestionCategory | null;
  worstCategory: QuestionCategory | null;
}

/**
 * 카테고리 1위 (PRD 11장).
 *
 * `score` 는 **그 카테고리의 점수**이고 `pair.score` 는 전체 케미 점수다. 둘은 다르다.
 * 대화 100점으로 1위인 Pair 의 전체 점수가 67점일 수 있다.
 * 화면에 `pair.score` 를 쓰면 같은 화면의 카테고리 타일과 숫자가 어긋난다.
 */
export interface CategoryLeader {
  category: QuestionCategory;
  pair: PairScore;
  score: number;
}

/**
 * 반전 케미 조합 (PRD 10장).
 *
 * TOP 3 밖에서 특정 카테고리가 100점이고 전체 점수보다 20점 이상 높은 Pair 다.
 * 후보가 없으면 `null` 이고 화면에서 그 영역을 통째로 뺀다.
 * **참여자가 3명이면 Pair 3개가 모두 TOP 3 에 들어가 후보가 생기지 않는다** (위키 `UI-MVP`).
 */
export interface TwistPair {
  pair: PairScore;
  category: QuestionCategory;
  categoryScore: number;
}

/** 내 Pair 목록. 한 번에 다 내려주지 않는다 (PRD 11장). */
export interface MyPairPage {
  items: PairScore[];
  total: number;
  hasMore: boolean;
}

export interface RoomResult {
  participantCount: number;
  submittedCount: number;
  /** 제출을 마친 사람이 2명 미만이면 결과가 없다. 화면은 대기 상태로 간다 (PRD 9장). */
  featuredPair: PairDetail | null;
  /** 전체 케미 TOP 3. 공동 순위 때문에 3개를 넘을 수 있다. */
  topPairs: PairScore[];
  categoryLeaders: CategoryLeader[];
  twistPair: TwistPair | null;
  myPairs: MyPairPage;
}
