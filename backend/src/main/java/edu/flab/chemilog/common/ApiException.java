package edu.flab.chemilog.common;

/**
 * 규약의 에러 코드로 응답할 실패를 나타낸다.
 *
 * 응답 바디의 message 는 언제나 코드의 기본 문구다. 생성자로 받는 reason 은
 * 로그에만 남는다. 원인을 사용자에게 보여 주면 서버 내부 사정이 화면에 새어 나가고,
 * 문구가 코드마다 달라져 프론트가 message 로 분기하고 싶어진다.
 */
public class ApiException extends RuntimeException {

    private final transient ApiErrorCode errorCode;

    public ApiException(ApiErrorCode errorCode) {
        super(errorCode.message());
        this.errorCode = errorCode;
    }

    public ApiException(ApiErrorCode errorCode, String reason) {
        super(reason);
        this.errorCode = errorCode;
    }

    public ApiErrorCode errorCode() {
        return errorCode;
    }
}
