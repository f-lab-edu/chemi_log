package edu.flab.chemilog.question;

/**
 * 질문 카테고리.
 *
 * 선언 순서가 방 안의 질문 표시 순서다. room_question.display_order 1~3 은 CONVERSATION,
 * 4~6 은 TRAVEL 이 되도록 RoomQuestionFactory 가 이 순서를 그대로 쓴다.
 * 순서를 바꾸면 새로 만들어지는 방의 문항 순서가 바뀐다.
 *
 * 상수 이름은 question.category, room_question.category 의 ENUM 값과 같아야 한다
 * (database/init/01-schema.sql). 이름이 어긋나면 그 값을 처음 읽는 쿼리에서 터진다.
 */
public enum QuestionCategory {
    CONVERSATION,
    TRAVEL,
    LIFESTYLE,
    SPENDING
}
