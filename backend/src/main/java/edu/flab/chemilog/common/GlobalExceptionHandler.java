package edu.flab.chemilog.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * 실패 응답을 한 곳에서 만듭니다.
 *
 * 컨트롤러마다 try-catch 를 두면 규약의 바디 형태를 지키는 곳과 안 지키는 곳이 섞입니다.
 * 여기를 통과하지 않는 실패가 없어야 프론트의 apiFetch 가 code 한 갈래만 다룰 수 있습니다.
 *
 * ResponseEntityExceptionHandler 를 상속해서 Spring MVC 가 이미 상태 코드를 정해 둔 예외들을
 * 그대로 살립니다. 없는 경로, 지원하지 않는 메서드, 지원하지 않는 Content-Type 이 거기 해당합니다.
 * @ExceptionHandler(Exception.class) 만 두면 ExceptionHandlerExceptionResolver 가
 * DefaultHandlerExceptionResolver 보다 먼저 돌아 그 예외들까지 가로챕니다.
 * 그러면 404 와 405 가 전부 500 INTERNAL_ERROR 로 나가고, 오타난 경로 하나가
 * 서버 로그에 ERROR 스택 트레이스를 남깁니다.
 *
 * 세 핸들러가 모두 Content-Type 을 JSON 으로 명시합니다. 이것을 지우면 오류 응답도 요청의
 * Accept 로 콘텐츠 협상을 합니다. Accept: text/html 로 들어온 요청은 406 과 빈 본문을 받고
 * {code, message} 계약이 깨져서 프론트의 apiFetch 가 code 를 읽지 못합니다.
 * Content-Type 이 정해져 있으면 Spring 이 협상을 건너뛰고 그 타입으로 씁니다.
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
        return ResponseEntity.status(code.status())
                .contentType(MediaType.APPLICATION_JSON)
                .body(ErrorResponse.of(code));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception exception) {
        log.error("처리하지 못한 예외다.", exception);
        ApiErrorCode code = ApiErrorCode.INTERNAL_ERROR;
        return ResponseEntity.status(code.status())
                .contentType(MediaType.APPLICATION_JSON)
                .body(ErrorResponse.of(code));
    }

    /**
     * 부모가 처리하는 예외들의 바디를 규약 형태로 바꿉니다.
     *
     * 부모는 기본적으로 ProblemDetail(RFC 9457)을 내보내는데, 위키 API 규약은
     * {code, message} 를 계약으로 정했습니다. 상태 코드는 부모가 정한 것을 그대로 쓰고
     * 바디만 갈아 끼웁니다. 상태까지 여기서 정하면 부모를 상속한 뜻이 없어집니다.
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
        return ResponseEntity.status(statusCode)
                .headers(headers)
                .contentType(MediaType.APPLICATION_JSON)
                .body(ErrorResponse.of(code));
    }

    /**
     * 부모가 정한 상태를 규약의 공통 코드로 바꿉니다.
     *
     * 규약의 공통 코드는 넷입니다. 없는 경로는 NOT_FOUND, 지원하지 않는 메서드는
     * METHOD_NOT_ALLOWED, 나머지 4xx(본문 파싱 실패, 검증 실패, 지원하지 않는 Content-Type)는
     * VALIDATION_FAILED, 5xx 는 INTERNAL_ERROR 입니다.
     * 도메인 코드(ROOM_NOT_FOUND, NICKNAME_DUPLICATED, ALREADY_SUBMITTED, ROOM_NOT_OPEN)는
     * 서비스가 ApiException 으로 직접 던지므로 여기를 지나지 않습니다.
     *
     * 없는 경로를 ROOM_NOT_FOUND 로 내보내지 않습니다. 그 코드는 "존재하지 않는 공유 코드"
     * 라는 뜻이라, 합치면 프론트가 없는 경로와 없는 방을 구분하지 못합니다.
     */
    private ApiErrorCode toErrorCode(HttpStatusCode statusCode) {
        if (statusCode.is5xxServerError()) {
            return ApiErrorCode.INTERNAL_ERROR;
        }
        if (HttpStatus.NOT_FOUND.isSameCodeAs(statusCode)) {
            return ApiErrorCode.NOT_FOUND;
        }
        if (HttpStatus.METHOD_NOT_ALLOWED.isSameCodeAs(statusCode)) {
            return ApiErrorCode.METHOD_NOT_ALLOWED;
        }
        return ApiErrorCode.VALIDATION_FAILED;
    }
}
