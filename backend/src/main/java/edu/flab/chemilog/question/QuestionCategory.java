package edu.flab.chemilog.question;

/**
 * 질문 카테고리.
 *
 * **선언 순서는 화면에 보이는 문항 순서와 무관합니다.** 방 생성 때 이 순서로 카테고리를 돌며
 * 3개씩 뽑지만, 뽑은 12개를 섞어서 INSERT 하므로 표시 순서는 완전 무작위입니다.
 * 대화 질문 다음에 소비 질문이 나올 수 있습니다 (docs/domain.md 의 문항 표시 순서).
 *
 * 옛 설계는 이 순서를 room_question.display_order 1~12 에 그대로 부여해 카테고리를
 * 묶어서 보여 줬습니다. 컬럼과 규칙 모두 2026-08-27 재설계로 없앴습니다.
 * 되살리면 문항 표시 순서가 다시 카테고리에 묶입니다.
 *
 * 상수 이름은 question.category 의 ENUM 값과 같아야 합니다
 * (database/init/01-schema.sql). 이름이 어긋나면 그 값을 처음 읽는 쿼리에서 터집니다.
 */
public enum QuestionCategory {
    CONVERSATION,
    TRAVEL,
    LIFESTYLE,
    SPENDING
}
