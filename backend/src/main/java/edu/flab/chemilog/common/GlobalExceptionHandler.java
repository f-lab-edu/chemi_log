package edu.flab.chemilog.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * 실패 응답을 한 곳에서 만든다.
 *
 * 컨트롤러마다 try-catch 를 두면 규약의 바디 형태를 지키는 곳과 안 지키는 곳이 섞인다.
 * 여기를 통과하지 않는 실패가 없어야 프론트의 apiFetch 가 code 한 갈래만 다룰 수 있다.
 *
 * ResponseEntityExceptionHandler 를 상속하는 이유는 Spring MVC 가 이미 상태 코드를 정해 둔
 * 예외들(없는 경로, 지원하지 않는 메서드, 지원하지 않는 Content-Type)을 그대로 살리기 위해서다.
 * @ExceptionHandler(Exception.class) 만 두면 ExceptionHandlerExceptionResolver 가
 * DefaultHandlerExceptionResolver 보다 먼저 돌아 그 예외들까지 가로챈다.
 * 그러면 404 와 405 가 전부 500 INTERNAL_ERROR 로 나가고, 오타난 경로 하나가
 * 서버 로그에 ERROR 스택 트레이스를 남긴다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApiException(ApiException exception) {
        ApiErrorCode code = exception.errorCode();
        if (code.status().is5xxServerError()) {
            log.error("서버 오류로 요청을 처리하지 못했다. code={}", code, exception);
        } else {
            log.debug("요청을 거절했다. code={}, reason={}", code, exception.getMessage());
        }
        return ResponseEntity.status(code.status()).body(ErrorResponse.of(code));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception exception) {
        log.error("처리하지 못한 예외다.", exception);
        ApiErrorCode code = ApiErrorCode.INTERNAL_ERROR;
        return ResponseEntity.status(code.status()).body(ErrorResponse.of(code));
    }

    /**
     * 부모가 처리하는 예외들의 바디를 규약 형태로 바꾼다.
     *
     * 부모는 기본적으로 ProblemDetail(RFC 9457)을 내보내는데, 위키 API 규약은
     * {code, message} 를 계약으로 정했다. 상태 코드는 부모가 정한 것을 그대로 쓰고
     * 바디만 갈아 끼운다. 상태까지 여기서 정하면 부모를 상속한 뜻이 없어진다.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception exception,
                                                             Object body,
                                                             HttpHeaders headers,
                                                             HttpStatusCode statusCode,
                                                             WebRequest request) {
        ApiErrorCode code = toErrorCode(statusCode);
        if (statusCode.is5xxServerError()) {
            log.error("서버 오류로 요청을 처리하지 못했다. status={}", statusCode, exception);
        } else {
            log.debug("요청을 거절했다. status={}, reason={}", statusCode, exception.getMessage());
        }
        return ResponseEntity.status(statusCode).headers(headers).body(ErrorResponse.of(code));
    }

    /**
     * 규약의 에러 코드 표에서 요청 자체의 문제를 나타내는 4xx 코드는 VALIDATION_FAILED 하나다.
     * 나머지 넷(ROOM_NOT_FOUND, NICKNAME_DUPLICATED, ALREADY_SUBMITTED, ROOM_NOT_OPEN)은
     * 도메인 상황을 뜻하므로 서비스가 ApiException 으로 직접 던진다.
     * 그래서 여기 오는 4xx 는 전부 VALIDATION_FAILED 로 모은다.
     *
     * 없는 경로의 404 도 여기 걸린다. ROOM_NOT_FOUND 로 바꾸지 않는 이유는 그 코드가
     * "존재하지 않는 공유 코드" 라는 뜻이어서, 없는 경로와 없는 방을 프론트가 구분하지
     * 못하게 되기 때문이다. 상태 코드는 404 그대로 나가므로 구분할 수단은 남는다.
     */
    private ApiErrorCode toErrorCode(HttpStatusCode statusCode) {
        if (statusCode.is5xxServerError()) {
            return ApiErrorCode.INTERNAL_ERROR;
        }
        return ApiErrorCode.VALIDATION_FAILED;
    }
}
