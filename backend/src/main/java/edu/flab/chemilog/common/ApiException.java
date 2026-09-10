package edu.flab.chemilog.common;

/**
 * 규약의 에러 코드로 응답할 실패를 나타냅니다.
 *
 * 생성자의 reason 은 로그에만 남습니다. 응답 바디의 message 는 언제나 코드의 기본 문구입니다
 * (ErrorResponse.of). super(reason) 을 보고 reason 이 응답에 나간다고 읽기 쉽습니다.
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
