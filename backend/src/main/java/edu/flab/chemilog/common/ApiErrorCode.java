package edu.flab.chemilog.common;

import org.springframework.http.HttpStatus;

/**
 * 위키 API 규약의 에러 코드 표를 그대로 옮긴 것이다.
 *
 * HTTP 상태를 코드에 묶어 둔 이유는, 같은 코드가 곳에 따라 다른 상태로 나가면
 * 프론트가 code 하나로 분기할 수 없게 되기 때문이다. 규약은 code 를 계약으로 정했다.
 *
 * message 는 표시용이라 언제든 바뀔 수 있다. 프론트는 이 문구로 분기하지 않는다.
 */
public enum ApiErrorCode {

    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다."),
    NICKNAME_INVALID(HttpStatus.BAD_REQUEST, "닉네임은 1~12자로 입력해 주세요."),
    ROOM_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 방입니다."),
    NICKNAME_DUPLICATED(HttpStatus.CONFLICT, "이미 사용 중인 닉네임입니다."),
    ALREADY_SUBMITTED(HttpStatus.CONFLICT, "이미 답변을 제출했습니다."),
    RESULT_NOT_ALLOWED(HttpStatus.FORBIDDEN, "답변을 제출해야 결과를 볼 수 있습니다."),
    ROOM_NOT_OPEN(HttpStatus.CONFLICT, "아직 방을 준비하고 있어요."),
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
