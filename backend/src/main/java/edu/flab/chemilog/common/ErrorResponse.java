package edu.flab.chemilog.common;

/** 위키 API 규약이 정한 에러 바디. 모든 실패 응답이 이 형태입니다. */
public record ErrorResponse(String code, String message) {

    public static ErrorResponse of(ApiErrorCode code) {
        return new ErrorResponse(code.name(), code.message());
    }
}
