/**
 * 질문 카테고리. 값은 `question.category` ENUM 과 같다 (`database/init/01-schema.sql`).
 *
 * 라벨, 이모지, 색은 위키 `mockup/ui-mvp.html` 의 `.cat-*` 규칙에서 가져왔다.
 * 목업에 없는 색을 새로 만들지 않는다.
 */
export type QuestionCategory =
  | "CONVERSATION"
  | "TRAVEL"
  | "LIFESTYLE"
  | "SPENDING";

interface CategoryStyle {
  label: string;
  emoji: string;
  /** 배지 배경과 글자색. Tailwind 가 클래스 이름을 정적으로 훑으므로 문자열을 쪼개 쓰지 않는다. */
  badge: string;
  /** 결과 화면 막대 색. 인라인 style 로 넣는다. */
  bar: string;
}

/**
 * 선언 순서가 곧 문항 순서다. `RoomQuestionFactory` 가 `QuestionCategory` 선언 순서로
 * `display_order` 를 부여하기 때문에 1~3 대화, 4~6 여행, 7~9 생활, 10~12 소비가 된다
 * (`docs/domain.md`). 이 순서를 바꾸면 화면과 서버의 인식이 어긋난다.
 */
export const CATEGORY: Record<QuestionCategory, CategoryStyle> = {
  CONVERSATION: {
    label: "대화",
    emoji: "💬",
    badge: "bg-[#fff0ea] text-[#e4622f]",
    bar: "#e4622f",
  },
  TRAVEL: {
    label: "여행",
    emoji: "✈️",
    badge: "bg-[#e8f6fe] text-[#1b85c4]",
    bar: "#1b85c4",
  },
  LIFESTYLE: {
    label: "생활",
    emoji: "🏠",
    badge: "bg-[#eff8e6] text-[#56922c]",
    bar: "#56922c",
  },
  SPENDING: {
    label: "소비",
    emoji: "💸",
    badge: "bg-[#fff5e4] text-[#c4831b]",
    bar: "#c4831b",
  },
};

export const CATEGORY_ORDER: QuestionCategory[] = [
  "CONVERSATION",
  "TRAVEL",
  "LIFESTYLE",
  "SPENDING",
];
