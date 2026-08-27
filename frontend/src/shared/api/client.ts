/**
 * 백엔드 호출 한 곳. 규약은 위키 `API-규약` 이다.
 *
 * 경로는 항상 `/api` 로 시작하는 상대 경로다. 절대 URL 을 쓰지 마라.
 * `next.config.ts` 의 rewrite 가 백엔드로 넘기므로 브라우저에게는 같은 오리진이고,
 * 그래야 `SameSite=Lax` 인 참여자 토큰 쿠키가 요청에 붙는다.
 */

/** 위키 `API-규약` 의 에러 코드 표. 새 코드가 생기면 여기에 추가한다. */
export type ApiErrorCode =
  | "VALIDATION_FAILED"
  | "NICKNAME_INVALID"
  | "ROOM_NOT_FOUND"
  | "NICKNAME_DUPLICATED"
  | "ALREADY_SUBMITTED"
  | "RESULT_NOT_ALLOWED"
  | "ROOM_NOT_OPEN"
  | "INTERNAL_ERROR";

/**
 * 규약이 정한 에러 바디. 화면은 `code` 로 분기하고 `message` 는 표시용으로만 쓴다.
 * 문구는 언제든 바뀌지만 `code` 는 계약이다.
 */
export interface ApiErrorBody {
  code: ApiErrorCode;
  message: string;
}

export class ApiError extends Error {
  readonly code: ApiErrorCode;
  readonly status: number;

  constructor(code: ApiErrorCode, message: string, status: number) {
    super(message);
    this.name = "ApiError";
    this.code = code;
    this.status = status;
  }
}

const NETWORK_ERROR_MESSAGE = "네트워크 연결을 확인해 주세요.";
const UNEXPECTED_ERROR_MESSAGE = "잠시 후 다시 시도해 주세요.";

/**
 * 규약을 따르지 않는 응답도 `ApiError` 로 바꾼다.
 *
 * 서버가 죽었거나 프록시가 HTML 오류 페이지를 돌려주면 바디가 규약 형태가 아니다.
 * 그대로 두면 화면마다 "code 로 분기" 와 "그 밖의 예외" 두 갈래를 각각 처리하게 된다.
 * 여기서 `INTERNAL_ERROR` 로 모아 화면은 한 갈래만 보게 한다.
 */
function toApiError(status: number, body: unknown): ApiError {
  if (
    typeof body === "object" &&
    body !== null &&
    typeof (body as ApiErrorBody).code === "string" &&
    typeof (body as ApiErrorBody).message === "string"
  ) {
    const { code, message } = body as ApiErrorBody;
    return new ApiError(code, message, status);
  }
  return new ApiError("INTERNAL_ERROR", UNEXPECTED_ERROR_MESSAGE, status);
}

export async function apiFetch<T>(
  path: string,
  init: RequestInit = {},
): Promise<T> {
  let response: Response;
  try {
    response = await fetch(path, {
      ...init,
      headers: {
        "Content-Type": "application/json",
        ...init.headers,
      },
    });
  } catch {
    // fetch 는 네트워크가 끊겼을 때만 reject 한다. 4xx, 5xx 는 여기로 오지 않는다.
    throw new ApiError("INTERNAL_ERROR", NETWORK_ERROR_MESSAGE, 0);
  }

  if (!response.ok) {
    const body = await response.json().catch(() => null);
    throw toApiError(response.status, body);
  }

  // 규약상 본문이 필요 없으면 204 다.
  if (response.status === 204) {
    return undefined as T;
  }
  return (await response.json()) as T;
}
