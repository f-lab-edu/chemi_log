package edu.flab.chemilog.common;

/**
 * 위키 API 규약이 정한 에러 바디. 모든 실패 응답이 이 형태다.
 *
 * 성공 응답은 데이터를 그대로 내보내고 래퍼로 감싸지 않는다. 성공에만 없는 래퍼를
 * 실패에도 없애면 프론트가 상태 코드로 두 형태를 구분할 수 없다.
 */
public record ErrorResponse(String code, String message) {

    public static ErrorResponse of(ApiErrorCode code) {
        return new ErrorResponse(code.name(), code.message());
    }
}
