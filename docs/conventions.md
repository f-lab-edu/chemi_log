# Conventions

**백엔드 구현을 기준으로 상당수 규칙이 확정됐습니다.**
그 이전에는 도메인 코드가 없어 실제 코드에서 확인할 수 있는 근거가 거의 없었습니다.

각 항목은 다음 세 가지 중 하나로 표시합니다.

- **현재**: 실제 코드나 설정에서 확인된 내용입니다. 그대로 따라야 합니다.
- **TBD**: 아직 정해지지 않은 내용입니다. 결정되기 전까지 구현으로 먼저 확정하지 않습니다.
- **권장안**: 근거는 있지만 아직 합의되지 않은 내용입니다. 적용하기 전에 확인이 필요합니다.

## 언어

**현재**: 코드 주석과 문서는 한국어로 작성합니다.
`build.gradle`, `01-schema.sql`, `docker-compose.yml`, `docs/` 모두 같은 기준을 따릅니다.

**현재**: 주석은 *무엇을* 하는지가 아니라 *왜* 그렇게 했는지를 적습니다.

기존 예시:

```java
// Spring Boot 4에서 spring-boot-starter-web이 spring-boot-starter-webmvc로 바뀌었다.
```

```sql
-- 서버 기본 collation은 accent-insensitive라 cafe와 café를 같게 본다.
-- 정규화를 애플리케이션이 끝내므로 이 컬럼은 있는 그대로 비교한다.
```

코드만 봐서는 알 수 없는 판단 근거가 있을 때만 주석을 남깁니다.
코드를 그대로 읽어주는 설명은 주석으로 적지 않습니다.

## Java

**현재**

- 베이스 패키지: `edu.flab.chemilog`
- group: `edu.flab`
- 들여쓰기: 스페이스 4칸
- 패키지명: 소문자 단어 하나 (`room`, `question`, `participant`)

**TBD**: 클래스·메서드·필드의 세부 네이밍 규칙, 접근 제어자 정책, `final` 사용 기준,
Lombok 도입 여부(현재 의존성 없음), 정적 팩터리와 생성자 사용 기준, record 사용 범위.

## 패키지 배치

**현재(결정됨)**: 도메인별로 먼저 나누고 그 안에 계층을 둡니다.
자세한 내용은 [docs/architecture.md](architecture.md)를 참고합니다.

```text
edu.flab.chemilog.room.RoomService        O
edu.flab.chemilog.service.RoomService     X
```

## Controller / Service / Repository / DTO / Entity

**현재(구현으로 확정)**.
계층별 책임은 [docs/architecture.md](architecture.md)의 "계층 책임" 표를 따릅니다.

| 항목 | 정한 것 | 근거 |
| --- | --- | --- |
| Entity 응답 노출 | **하지 않습니다.** DTO로 감쌉니다 | `RoomController`가 `CreateRoomResponse`를 반환합니다 |
| DTO 형태 | **`record`** | `CreateRoomRequest`, `CreateRoomResponse`, `ErrorResponse` 전부 |
| DTO 위치 | 도메인 패키지 안 **`dto/`** | `room/dto/` |
| Entity 생성 | **정적 팩터리.** 생성자는 private, 기본 생성자는 protected | `Room.withShareCode`, `Participant.host`, `RoomQuestion.of` |
| 트랜잭션 경계 | **Service** | `RoomService.create()`가 `@Transactional` |

정적 팩터리 이름에는 생성 의도가 드러나게 합니다.

`Participant.host`와 `Participant.guest`는 메서드 이름만 봐도 `is_host` 값이 어떻게 정해지는지 알 수 있습니다.
방장을 만드는 진입점이 한 군데뿐이라는 점이 "방마다 방장은 한 명"이라는 규칙을 애플리케이션에서 보장하는 근거입니다.
`new Participant(..., true)`처럼 일반 생성자로 열어두면 이 전제가 코드에서 보이지 않게 됩니다.

서비스가 컨트롤러에 반환하는 값과 API 응답 DTO도 분리합니다.

`CreatedRoom`에는 `accessToken`이 있지만 `CreateRoomResponse`에는 없습니다.
토큰이 응답 바디가 아니라 `Set-Cookie` 헤더로 나가야 하기 때문입니다.
두 타입을 하나로 합치면 응답 DTO에 토큰을 넣게 될 가능성이 생기고 `HttpOnly`를 사용한 의미도 흐려집니다.

**TBD**: Lombok 도입 여부(현재 의존성 없음), 접근 제어자 정책, `final` 사용 기준.

## Exception / API 응답

**현재(구현으로 확정)**.
`common/` 아래 네 클래스로 구성합니다.

```text
ApiErrorCode            위키 API-규약의 에러 코드 표를 옮긴 enum. HTTP 상태를 함께 가진다
ApiException            해당 코드로 응답할 실패. reason은 로그에만 남기고 사용자에게는 보내지 않는다
ErrorResponse           {code, message} 고정. record
GlobalExceptionHandler  @RestControllerAdvice. ResponseEntityExceptionHandler를 상속한다
```

`ApiErrorCode`에는 HTTP 상태를 함께 둡니다.

같은 에러 코드가 상황에 따라 다른 HTTP 상태로 나가면
프론트가 `code` 하나만 보고 분기할 수 없습니다.
API 규약에서 `code`를 계약으로 정했기 때문에 상태도 같은 곳에서 함께 관리합니다.

`ApiException.reason`은 응답에 넣지 않습니다.

응답의 `message`는 항상 에러 코드에 정의된 기본 문구를 사용합니다.
내부 원인을 그대로 사용자에게 전달하면 서버 내부 사정이 화면에 노출될 수 있고,
같은 코드에서도 문구가 달라져 프론트가 `message`를 기준으로 분기하게 될 가능성이 생깁니다.

`ResponseEntityExceptionHandler` 상속도 유지합니다.

`@ExceptionHandler(Exception.class)`만 두면 `ExceptionHandlerExceptionResolver`가
`DefaultHandlerExceptionResolver`보다 먼저 동작해,
Spring MVC가 이미 HTTP 상태를 알고 있는 예외까지 일반 예외로 처리할 수 있습니다.

그 경우 404와 405가 500으로 바뀌고,
잘못된 경로 하나만 요청해도 서버 로그에 ERROR 스택 트레이스가 남을 수 있습니다.

`RoomCreationTest.없는_경로와_지원하지_않는_메서드는_전용_코드로_나간다`
테스트가 이 동작을 확인합니다.

Spring이 정한 4xx를 전부 `VALIDATION_FAILED`로 묶지는 않습니다.

- 없는 경로: `NOT_FOUND`
- 지원하지 않는 메서드: `METHOD_NOT_ALLOWED`

둘 다 위키 `API-규약`의 공통 에러 코드입니다.

없는 경로를 `ROOM_NOT_FOUND`로 내려주면 프론트가 잘못해서 "없는 방" 화면을 보여줄 수 있습니다.
`ROOM_NOT_FOUND`는 API 경로 자체는 맞지만 해당 공유 코드의 방이 없을 때만 사용합니다.

**권장안**: DB UNIQUE 제약 위반은 그대로 노출하지 말고 도메인 에러로 변환합니다.
근거는 `docs/database.md`의 "UNIQUE (room_id, nickname_key)" 절에 있습니다.

애플리케이션의 사전 조회는 빠르게 사용자에게 응답하기 위한 용도이고,
최종 정합성은 DB UNIQUE 제약이 보장합니다.
사전 조회만으로 동시 요청까지 막을 수 있다고 가정하지 않습니다.

## Validation

**현재**: `spring-boot-starter-validation` 의존성이 있으며 Bean Validation을 사용합니다.

**TBD**: 검증을 DTO 애노테이션에서 할지, 도메인에서 할지, 둘의 경계를 어디에 둘지.

**현재(도메인 규칙)**:
닉네임 정규화는 **전부 애플리케이션에서 처리합니다.**
DB collation에 맡기지 않습니다.

정규화 규칙:

- trim
- 연속 공백 축소
- NFC
- 공백 전체 제거
- 소문자화

소문자화는 반드시 `toLowerCase(Locale.ROOT)`를 사용합니다.
`Locale` 인자를 생략하면 서버 로케일에 따라 결과가 달라질 수 있습니다.

공백 전체 제거는 `nickname_key`에만 적용합니다.
표시용 `nickname`에는 내부 공백을 남깁니다.

정규화 구현은 `Nickname.of` 한 곳에 있습니다.
중복 조회용 키를 따로 만들지 말고 여기서 만들어진 값을 그대로 사용합니다.

## Transaction

**현재(도메인 규칙)**:
방 생성과 방장 답변 제출은 각각 하나의 트랜잭션으로 처리합니다.
자세한 근거와 범위는 [docs/domain.md](domain.md)의 "주요 흐름" 절을 참고합니다.

**TBD**: `@Transactional`을 어느 계층에 붙일지, `readOnly` 사용 기준.

## Persistence

**현재**: `ddl-auto: none`.

스키마는 **`database/init/01-schema.sql`이 직접 관리합니다.**
Entity를 수정해도 실제 DB 스키마는 자동으로 바뀌지 않습니다.
둘이 어긋나면 런타임에서 문제가 발생합니다.

**현재**: `open-in-view: false`.

지연 로딩을 컨트롤러나 응답 직렬화 시점까지 미루지 않습니다.
Service 트랜잭션 안에서 필요한 데이터를 모두 조회해야 합니다.

## SQL

**현재**: `database/init/01-schema.sql`에서 확인할 수 있는 규칙입니다.

| 대상 | 규칙 | 예 |
| --- | --- | --- |
| 테이블·컬럼 | snake_case | `room_question`, `source_question_id` |
| UNIQUE | `uk_<테이블>_<의미>` | `uk_room_share_code` |
| FK | `fk_<테이블>_<대상>` | `fk_room_question_room` |
| CHECK | `ck_<테이블>_<의미>` | `ck_participant_submitted` |
| 인덱스 | `idx_<테이블>_<컬럼들>` | `idx_question_category_active` |

추가 규칙:

- 엔진은 `InnoDB`를 명시합니다.
- 시각 컬럼은 `DATETIME(6)`을 사용하고 기본값은 `CURRENT_TIMESTAMP(6)`입니다.
- 좌측 프리픽스가 이미 겹치는 FK 컬럼에는 별도 인덱스를 추가하지 않습니다.

## Test

**현재**: JUnit 5를 사용합니다 (`useJUnitPlatform()`).
테스트는 56개입니다.

| 항목 | 정한 것 |
| --- | --- |
| 메서드 이름 | **한국어 서술문.** `짝_없는_대리_문자를_거절한다` |
| 클래스 이름 | 검증 대상 + 역할. `NicknameTest`, `RoomCreationTest` |
| DB 격리 | `@AfterEach`로 직접 지웁니다. **`@Transactional`은 붙이지 않습니다** |
| 검증 수단 | 통합 테스트는 리포지토리가 아니라 `JdbcTemplate`으로 실제 컬럼 값을 읽습니다 |
| given-when-then | 형식을 강제하지 않습니다. 필요한 경우 주석으로 무엇을 왜 검증하는지 적습니다 |

통합 테스트에는 `@Transactional`을 붙이지 않습니다.

테스트 트랜잭션 안에서 컨트롤러 요청까지 실행되면 실제 커밋이 일어나지 않아
롤백이나 트랜잭션 경계를 제대로 검증하지 못할 수 있습니다.

`활성_질문이_모자라면_방을_만들지_않는다` 테스트가 이 동작을 확인합니다.

`JdbcTemplate`으로 실제 컬럼을 읽습니다. `ddl-auto: none`이라 엔티티와 스키마가 어긋나도 애플리케이션이 뜹니다.

엔티티와 DB 스키마가 서로 달라도 애플리케이션이 뜨거나 컴파일이 통과할 수 있습니다.
리포지토리로 다시 조회하면 같은 엔티티 매핑을 한 번 더 사용하게 되므로
매핑 오류가 그대로 가려질 수 있습니다.

그래서 통합 테스트에서는 실제 컬럼 값을 직접 확인합니다.

테스트 데이터를 지울 때는 FK 삭제 순서를 지켜야 합니다.

`fk_participant_room`이 `ON DELETE RESTRICT`이기 때문에
`participant`를 먼저 삭제하지 않으면 `room` 삭제가 MySQL 오류 `1451`로 거부됩니다.

**TBD**: 단위 테스트와 통합 테스트의 비중.

## Logging

**현재**: `logging.level.org.hibernate.SQL: debug`를 사용합니다.
개발 중 SQL 확인을 위한 설정입니다.

**TBD**: 애플리케이션 로그 작성 규칙, 로그 레벨 기준, 민감정보 마스킹 정책.

**주의**: `access_token` 원본과 `share_code`는 자격증명에 준해서 다룹니다.
로그에 남기지 않습니다.

## Frontend

**현재**

- TypeScript `strict: true`
- 경로 별칭 `@/*` → `./src/*`
- App Router (`src/app/`)
- Tailwind CSS v4, 유틸리티 클래스를 JSX에 직접 작성
- ESLint: `eslint-config-next`의 core-web-vitals + typescript

**현재**: 디렉터리는 도메인별로 나눕니다.
구조와 이유는 `docs/architecture.md`의 "프론트엔드 디렉터리 구조" 절에 있습니다.

**현재**: 파일 이름은 파일이 담고 있는 대상에 맞춥니다.

- 컴포넌트를 내보내는 파일: PascalCase (`NicknameForm.tsx`)
- 그 외 파일: camelCase (`api.ts`, `nickname.ts`, `useBrowserValue.ts`)

### 백엔드 호출

백엔드 요청은 **`shared/api/client.ts`의 `apiFetch`로만 보냅니다.**
컴포넌트 안에서 `fetch`를 직접 사용하지 않습니다.

경로는 항상 `/api`로 시작하는 상대 경로를 사용합니다.

절대 URL을 사용하면 Next rewrite를 거치지 않게 되고,
현재 참여자 토큰 쿠키 구조와 맞지 않게 됩니다.
자세한 이유는 `docs/architecture.md`에 있습니다.

에러 처리는 `code`를 기준으로 분기합니다.
`message`는 화면에 보여주기 위한 용도로만 사용합니다.

문구는 바뀔 수 있지만 `code`는 API 계약이므로
`message` 문자열을 기준으로 분기하지 않습니다.

`apiFetch`는 API 규약을 따르지 않는 응답도 `ApiError` 형태로 변환합니다.

예를 들어 다음 경우가 모두 `INTERNAL_ERROR`로 처리됩니다.

- 네트워크 연결 실패
- 프록시가 HTML 오류 페이지를 반환
- JSON 형식이 아닌 예상 밖의 응답

덕분에 호출하는 쪽에서는 실패 처리를 하나의 `catch` 흐름으로 다룰 수 있습니다.

화면에서 데이터를 조회할 때는 `shared/hooks/useApiResource`를 사용합니다.

이 훅은 다음 상태와 재시도 함수를 함께 제공합니다.

- `loading`
- `ready`
- `error`

실패 상태는 `features/room/RoomErrorScreen`에 넘기면
에러 `code`에 맞는 안내 화면을 보여줍니다.

데이터를 조회하는 화면은 클라이언트 컴포넌트로 둡니다.

서버 컴포넌트에서 API를 호출할 경우 rewrite를 우회하게 되는 이유는
`docs/architecture.md`에 정리되어 있습니다.

`app/` 아래의 `page.tsx`는 `params`를 풀어
`features/` 컴포넌트에 전달하는 역할만 합니다.

### 스타일

색상은 `src/app/globals.css`의 `@theme` 토큰만 사용합니다.

색상 값의 기준은 위키 `mockup/ui-mvp.html`이며,
목업에 없는 색을 임의로 추가하지 않습니다.

다크 모드는 현재 목업에 없기 때문에 라이트 테마 하나만 사용합니다.

`body`에는 `word-break: keep-all`이 적용되어 있습니다.
한국어가 어절 단위로 줄바꿈되도록 하기 위한 설정입니다.

URL이나 공유 코드처럼 어절 경계가 없는 긴 문자열은 그대로 넘칠 수 있으므로
그런 영역에는 별도로 `break-all`을 적용합니다.

### 클라이언트 전용 값

`window`, `navigator`처럼 서버에서 알 수 없는 값은
`shared/hooks/useBrowserValue`를 통해 읽습니다.

`useEffect` 안에서 `setState`로 값을 읽지 않습니다.

그렇게 하면 첫 렌더 이후 상태를 다시 갱신하면서 화면이 한 번 더 그려지고,
`react-hooks/set-state-in-effect` 규칙에도 걸립니다.

`useBrowserValue`는 **한 번 읽은 뒤 바뀌지 않는 값에만** 사용합니다.
내부에서 구독하지 않기 때문입니다 (`noopSubscribe`).

계속 바뀌는 값은 `useState`로 관리하고 초기값에서 읽습니다.

브라우저 값을 `useState` 초기값에서 직접 읽어도 되는 조건은 하나입니다.

**해당 컴포넌트가 서버 렌더링되지 않아야 합니다.**

`useApiResource`는 첫 렌더에서 항상 `loading`을 반환하기 때문에
그 결과 이후에만 렌더되는 컴포넌트는 브라우저에서만 마운트됩니다.

`AnswerFlow`의 `AnswerSheet`가 이 조건을 이용해
`loadDraft`를 초기값에서 호출합니다.

이 전제가 없는 컴포넌트에서 같은 방식을 사용하면 hydration 결과가 달라질 수 있습니다.

### 브라우저 저장소

`localStorage`는 단순히 읽는 것만으로도 예외가 발생할 수 있습니다.
Safari 프라이빗 모드나 브라우저 저장소 차단 설정이 대표적인 경우입니다.

따라서 `window.localStorage` 접근 자체를 `try`로 감쌉니다.

저장이 실패하는 것은 화면 전체가 중단될 이유가 아닙니다.
PRD 17장에서도 이런 환경에서는 제한 사항을 안내하도록 요구합니다.

저장 함수는 성공 여부를 반환하고,
화면에서는 그 값을 이용해 사용자에게 안내합니다 (`features/question/draft.ts`).

브라우저 저장소에서 읽은 값은 그대로 신뢰하지 않습니다.

사용자가 직접 수정할 수 있고,
이전 버전의 형식이 남아 있을 수도 있으며,
현재 서버 응답과 맞지 않을 수도 있습니다.

파싱한 뒤 현재 서버 응답과 비교해 유효한 값만 남깁니다.
일부 값이 잘못됐다고 전체 데이터를 모두 버리지는 않습니다.

키 형식은 다음과 같습니다.

```text
chemilog:<용도>:<식별자>
```

공유 코드는 Base64URL을 사용하기 때문에 `:`가 들어가지 않습니다.

탭 사이의 저장 상태 동기화는 하지 않습니다.

`saveDraft`는 키 하나를 통째로 덮어쓰고,
각 탭은 `storage` 이벤트를 구독하지 않습니다.

그래서 같은 방을 두 탭에서 열면
나중에 저장한 탭의 내용이 기존 저장값을 덮어쓸 수 있습니다.

예를 들어 탭 A에서 6문항을 답한 뒤
탭 B에서 1문항만 답하면 저장값이 1문항 기준으로 바뀔 수 있습니다.
그 상태에서 탭 A를 새로고침하면 기존 6문항 진행 상태가 사라질 수 있습니다.

현재는 이 동작을 허용합니다.

주요 사용 환경이 모바일 웹이라 같은 방을 여러 탭에서 동시에 사용하는 경우가 많지 않고,
`storage` 이벤트로 양쪽 화면 상태를 자동 동기화하면
사용자가 답변 중인 화면이 다른 탭의 변경으로 갑자기 바뀌는 문제가 생길 수 있습니다.

이 부분을 다시 검토할 기준은 실제 사용자 제보나 데스크톱 사용 비중입니다.
코드 구조만을 이유로 동기화를 추가하지 않습니다.

**TBD**: 상태 관리 라이브러리.
현재는 `useState`만 사용합니다.

**TBD**: 프론트엔드 테스트 도구.

## Git

**현재**: 커밋 메시지는 `type: 한국어 설명` 형식을 사용합니다.

```text
docs: 케미방 생성 ERD 추가
chore: 프로젝트 초기 셋팅 (backend, frontend, database)
```

커밋 본문은 필요하면 작성할 수 있지만,
diff만 봐도 알 수 있는 내용은 반복해서 적지 않습니다.

"무엇을 바꿨는지"는 diff에서 확인할 수 있으므로
본문에는 코드만으로 알기 어려운 배경이나 이유를 적습니다.

| 본문에 쓸 것 | 본문에 쓰지 않을 것 |
| --- | --- |
| 왜 이 시점에 하는지 | 바뀐 파일·테이블·함수 나열 |
| 왜 다른 방식 대신 이 방식을 선택했는지 | 코드나 주석에 이미 있는 설명 |
| 다시 바꾸면 안 되는 이유 | `docs/`에 이미 정리된 내용 전체 반복 |
| 이 커밋만으로 끝나지 않는 후속 작업 | 코드를 그대로 읽어주는 요약 |

설계 근거는 대부분 `docs/`에 따로 남기기 때문에
커밋 본문에 추가로 적을 내용이 없는 경우도 많습니다.

그럴 때는 제목 한 줄만 사용합니다.
본문을 억지로 채우지 않습니다.

```text
docs: 케미방 생성 ERD 추가
chore: 프로젝트 초기 셋팅 (backend, frontend, database)
```

**현재**: AI 관련 트레일러나 자동 생성 문구를 커밋, PR, 이슈에 넣지 않습니다.

다음 형식은 모두 사용하지 않습니다.

```text
Co-Authored-By: Claude ...
Claude-Session: https://claude.ai/code/...
🤖 Generated with [Claude Code](...)
```

도구의 기본 설정에서 이런 문구를 추가하도록 되어 있더라도
이 저장소에서는 사용하지 않습니다.

PR은 멘토가 직접 확인하며,
한 번 push된 커밋은 이후 메시지를 수정하더라도
PR 타임라인에 force-push 이력과 기존 커밋 링크가 남을 수 있습니다.

그래서 커밋하기 전에 메시지와 트레일러를 확인합니다.

`91045fb` 커밋이 트레일러가 포함된 상태로 push되어 PR #3에 노출된 적이 있습니다.
당시에는 이 규칙이 세션 인계용 메모에만 있었고 이 문서에는 정리되어 있지 않았습니다.

**TBD**: 허용할 commit type 목록, 브랜치 전략.
현재는 `feat/<번호>-<설명>` 형태가 사용되고 있습니다.
