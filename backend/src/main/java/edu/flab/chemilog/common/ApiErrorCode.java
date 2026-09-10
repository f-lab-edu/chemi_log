package edu.flab.chemilog.common;

import org.springframework.http.HttpStatus;

/** 위키 API 규약의 에러 코드 표입니다. */
public enum ApiErrorCode {

    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다."),
    NICKNAME_INVALID(HttpStatus.BAD_REQUEST, "닉네임은 1~12자로 입력해 주세요."),
    ROOM_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 방입니다."),
    NICKNAME_DUPLICATED(HttpStatus.CONFLICT, "이미 사용 중인 닉네임입니다."),
    ALREADY_SUBMITTED(HttpStatus.CONFLICT, "이미 답변을 제출했습니다."),
    RESULT_NOT_ALLOWED(HttpStatus.FORBIDDEN, "답변을 제출해야 결과를 볼 수 있습니다."),
    ROOM_NOT_OPEN(HttpStatus.CONFLICT, "아직 방을 준비하고 있어요."),
    // ROOM_NOT_FOUND 와 상태는 같고 뜻은 다릅니다. 이쪽은 경로 자체가 없습니다.
    // 한 코드로 합치면 프론트가 없는 경로와 없는 방을 구분하지 못합니다.
    NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 경로가 없습니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 요청 방식입니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "잠시 후 다시 시도해 주세요.");

    private final HttpStatus status;
    private final String message;

    ApiErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus status() {
        return status;
    }

    public String message() {
        return message;
    }
}
