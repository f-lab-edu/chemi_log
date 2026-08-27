package edu.flab.chemilog.participant;

/**
 * 참여자의 답변 상태.
 *
 * SUBMITTED 는 participant.submitted_at 이 NULL 이 아닌 것과 짝을 이룬다.
 * 이 짝은 ck_participant_submitted CHECK 제약이 DB 에서 강제한다.
 * 상태만 바꾸고 시각을 빼먹으면 INSERT 나 UPDATE 가 3819 로 거절된다.
 */
public enum AnswerStatus {
    ANSWERING,
    SUBMITTED
}
