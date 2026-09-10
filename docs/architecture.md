# Architecture

**백엔드 코드는 새 스키마 기준으로 다시 작성했습니다.**

ERD 재설계에서 컬럼 여덟 개가 제거되면서 기존 구현이 `ddl-auto: none` 환경에서 더 이상 뜨지 않았고,
기존 소스 28개를 정리한 뒤 새 스키마 기준으로 다시 작성했습니다.

```text
[x] 엔티티와 리포지토리   Room, Participant, RoomQuestion, Question
[x] 질문 세트 생성 로직   RoomQuestionFactory. 카테고리별 3개씩 뽑아 12개를 섞어서 INSERT
[x] POST /api/rooms      응답의 방 상태를 RoomStatus.of(방장)로 계산
[x] 통합 테스트           Testcontainers로 실제 MySQL 8.4 사용. 56개 전부 통과
```

**`Answer` 엔티티는 아직 없습니다.**
답변 제출은 다음 이슈에서 구현하며, 현재는 테이블만 있습니다.

**이 이슈의 구현은 끝났습니다.**
`RoomService.create()`에 있던 `TODO(human)`은 `RoomStatus.of(Participant)`로 채웠습니다.
남은 작업은 커밋과 PR입니다.

프론트엔드는 목업 8개와 로딩·오류 화면까지 구현되어 있고,
백엔드 연동은 `POST /api/rooms`만 실제로 확인했습니다.
나머지 화면은 아직 없는 API를 가정하고 구현한 상태입니다.

**프론트엔드 코드는 이 브랜치에 없습니다. `feat/1-create-room-ui` 브랜치에 있습니다.**
프론트엔드는 PR을 올리지 않기로 했기 때문에 백엔드 브랜치에 섞지 않습니다.
아래에서 `frontend/` 파일을 가리키는 절은 모두 해당 브랜치를 기준으로 합니다.

```bash
git show feat/1-create-room-ui:frontend/next.config.ts
```

아래에서 "현재"로 표시한 내용만 실제 코드에서 확인한 사실입니다.
그 외 내용은 TBD이거나, 결정은 끝났지만 아직 코드로 구현되지 않은 상태입니다.
백엔드 재작성으로 이전의 "재작성 대기" 표시는 모두 없어졌습니다.

## 기술 스택 (현재)

| 영역 | 스택 | 근거 |
| --- | --- | --- |
| Backend | Java 21, Spring Boot 4.1.1, Spring MVC, Spring Data JPA, Bean Validation | `backend/build.gradle` |
| 빌드 | Gradle (Wrapper 9.4.1) | `backend/gradle/wrapper/` |
| Frontend | Next.js 16.3.2, React 19.2.8, TypeScript 5, Tailwind CSS v4, App Router | `frontend/package.json` |
| Database | MySQL 8.4 (Docker Compose) | `database/docker-compose.yml` |
| 정적 분석 | SonarCloud (PR 단계, GitHub Actions) | `.github/workflows/sonarcloud-analyze.yml` (origin/main) |

### Spring Boot 4 특성이 반영된 부분

`build.gradle`에는 Boot 3과 다른 선택이 세 군데 있습니다.
참고 자료를 볼 때 이 차이를 확인해야 합니다.

```text
spring-boot-starter-web         →  spring-boot-starter-webmvc
io.spring.dependency-management →  Gradle 네이티브 platform(BOM_COORDINATES)
spring-boot-starter-test (통합) →  모듈별 test starter
                                    (webmvc-test, data-jpa-test, validation-test)
```

## 역할 분담 (현재)

```text
frontend/   Next.js. 모바일 웹 우선 UI
backend/    Spring Boot. REST API
database/   MySQL 컨테이너 + 스키마·시드 SQL
```

프론트엔드와 백엔드는 별도 프로세스로 실행하며 각각 3000, 8080 포트를 사용합니다 (`README.md`).

## 프론트엔드 ↔ 백엔드 연결 (현재)

브라우저는 백엔드를 직접 호출하지 않습니다.
항상 프론트엔드와 같은 오리진의 `/api`를 호출하고,
Next의 rewrite가 요청을 백엔드로 전달합니다 (`frontend/next.config.ts`).

```text
브라우저 ──► localhost:3000/api/rooms ──rewrite──► localhost:8080/api/rooms
```

대상 오리진은 `BACKEND_ORIGIN`이며 기본값은 `http://localhost:8080`입니다.
이 값은 브라우저 번들에는 들어가지 않습니다 (`frontend/.env.example`).

**`BACKEND_ORIGIN`은 런타임 변수가 아니라 빌드 시점에 정해집니다.**
`rewrites()`는 빌드할 때 실행되고 결과가 `.next/routes-manifest.json`에 기록됩니다.
`next start`는 이 매니페스트를 읽기 때문에 실행 시점에 환경 변수를 바꿔도 이미 빌드된 값은 바뀌지 않습니다.

```text
BACKEND_ORIGIN=http://localhost:8090 npx next start
실측 결과 요청은 8080으로 전달됨
```

현재는 배포 방식이 정해지지 않아 이 구조를 유지합니다.
따라서 **배포 대상마다 다시 빌드해야 합니다.**

한 번 빌드한 이미지를 여러 환경에 배포해야 한다면 rewrite만으로는 어렵고,
요청마다 `process.env`를 읽을 수 있는 Route Handler나 `middleware.ts` 쪽으로 옮겨야 합니다.

그렇게 바꾸더라도 브라우저가 보는 오리진은 그대로 유지할 수 있기 때문에
아래 `SameSite=Lax`에 대한 근거는 달라지지 않습니다.

이 구조를 선택한 이유는 참여자 인증 쿠키입니다.
위키 `API-규약`에서는 참여자 토큰을 `SameSite=Lax` 쿠키로 사용하도록 정했습니다.

브라우저가 백엔드를 직접 호출하면 배포 환경에서 프론트와 백엔드의 도메인이 달라질 수 있고,
이 경우 쿠키가 요청에 붙지 않을 수 있습니다.
프록시를 두면 브라우저 입장에서는 항상 같은 오리진으로 보이므로
`SameSite=Lax`를 유지하면서 쿠키를 전달할 수 있고, 백엔드에 별도 CORS 설정도 필요하지 않습니다.

**로컬에서는 이 문제가 잘 드러나지 않습니다.**
`localhost:3000`과 `localhost:8080`은 포트만 다르고 같은 사이트로 취급되기 때문에
브라우저가 백엔드를 직접 호출해도 정상처럼 보일 수 있습니다.
배포 환경에서 처음 인증 문제가 생길 수 있으므로 이 차이를 주의해야 합니다.

## 프론트엔드 디렉터리 구조 (현재)

백엔드와 같은 기준으로 **도메인별로 먼저 나눕니다.**

```text
frontend/src/
├── app/                          라우팅과 화면 조립만
│   ├── page.tsx                  01 홈
│   ├── loading.tsx  error.tsx  not-found.tsx
│   ├── r/[shareCode]/            05 초대 링크 진입 (짧은 경로)
│   └── rooms/
│       ├── new/                  02 방 만들기
│       └── [shareCode]/
│           ├── answer/           03·06 질문 답변
│           ├── invite/           04 초대 링크
│           ├── status/           07 참여 현황
│           └── result/           08 그룹 결과
├── features/
│   ├── room/                     방, 닉네임, 초대 링크
│   ├── question/                 질문, 카테고리, 답변 흐름
│   ├── participant/              참여, 참여 현황
│   └── result/                   점수, 순위, 해석 문구
└── shared/                       도메인을 가리지 않는 것만
    ├── api/client.ts
    ├── hooks/                    useApiResource, useBrowserValue, useShareLink
    └── ui/                       Button, Avatar, StateScreen
```

`app/` 아래에는 라우팅에 필요한 파일만 둡니다.
실제 화면 로직과 UI는 `features/`에 둡니다.

`shared/`는 여러 도메인에서 실제로 함께 사용하게 된 코드만 둡니다.

초대 링크만 `/r/`로 짧게 유지합니다.
목업도 `chemilog.app/r/<코드>` 형태이고,
공유 코드가 22자이기 때문에 경로까지 길어지면 메신저에서 링크가 쉽게 줄바꿈될 수 있습니다.

데이터를 읽는 화면은 모두 클라이언트 컴포넌트입니다.
`apiFetch`에서 사용하는 상대 경로는 브라우저에서 호출할 때 rewrite를 탑니다.

서버 컴포넌트에서 같은 API를 호출하려면 절대 URL이 필요하고,
이 경우 백엔드를 직접 호출하게 되어 현재 same-origin 구조의 전제가 달라집니다.

로딩과 오류 상태는 `shared/hooks/useApiResource`에서 함께 처리합니다.

## Backend 패키지 구조 (현재)

베이스 패키지는 `edu.flab.chemilog`입니다 (`ChemiLogApplication.java` 기준).

**도메인별로 먼저 나누고 그 안에 계층을 둡니다.**
아래 구조는 실제로 작성된 코드 기준입니다.

```text
edu.flab.chemilog
├── room/
│   ├── Room.java                    Entity. share_code와 created_at만 가진다
│   ├── RoomQuestion.java            Entity. question을 참조만 한다
│   ├── RoomStatus.java              HOST_ANSWERING / OPEN. of(Participant)가 방장 행으로 계산한다
│   ├── RoomRepository.java
│   ├── RoomQuestionRepository.java
│   ├── RoomQuestionFactory.java     카테고리별 3개 추출 후 12개를 섞는다
│   ├── RoomService.java             @Transactional. 방 생성 경계
│   ├── RoomController.java
│   ├── ShareCodeGenerator.java      SecureRandom 16바이트 → base64url 22자
│   ├── CreatedRoom.java             서비스가 컨트롤러에 주는 값. accessToken 포함
│   └── dto/                         CreateRoomRequest, CreateRoomResponse
├── question/
│   ├── Question.java                불변. 읽기만 한다
│   ├── QuestionCategory.java
│   └── QuestionRepository.java
├── participant/
│   ├── Participant.java             is_host로 방장을 표시한다
│   ├── Nickname.java                정규화와 검증. 표시값과 중복 판정 키를 만든다
│   ├── AccessTokenGenerator.java    원본은 쿠키로만, DB에는 SHA-256 해시
│   └── ParticipantRepository.java
└── common/                          ApiErrorCode, ApiException, ErrorResponse,
                                     GlobalExceptionHandler
```

`Answer` 엔티티가 없는 것은 누락이 아닙니다.
답변 제출은 다음 이슈에서 구현합니다.

`ShareCodeGenerator`는 `room/`,
`AccessTokenGenerator`는 `participant/`에 둡니다.
각각 방과 참여자의 식별 정보를 만드는 역할이기 때문입니다.

둘을 `common/`으로 모으지 않습니다.

계층별(`controller/`, `service/`, …)이 아니라 도메인별 구조를 사용한 이유는
하나의 기능을 수정할 때 관련 파일을 같은 디렉터리 안에서 찾을 수 있게 하기 위해서입니다.

`common/`은 여러 도메인에서 실제로 함께 쓰이게 된 코드만 넣습니다.
미리 공용화하지 않습니다.

`room_question`은 `Room`의 일부로 보고 `room/` 아래에 둡니다.
별도 최상위 패키지로 분리하지 않습니다.

## 계층 책임 (현재)

구현은 아래 경계를 따릅니다.
멘토 리뷰 결과에 따라 이후 조정될 수 있습니다.

이 구조가 실제로 도움이 된 사례도 있습니다.
ERD 재설계로 방 생성 과정이 4단계에서 3단계로 줄었지만
`RoomController`는 수정하지 않았습니다.

컨트롤러가 엔티티 구조를 직접 알지 않고,
`CreatedRoom`이라는 서비스 반환 타입만 알고 있기 때문입니다.

반대로 제거된 컬럼을 직접 사용하던 엔티티와 서비스는 새 스키마에 맞춰 수정해야 했습니다.

| 계층 | 책임 | 하지 않는 것 |
| --- | --- | --- |
| Controller | HTTP 요청/응답 매핑, 요청 DTO 검증 | 비즈니스 규칙 판단, Entity 직접 노출 |
| Service | 비즈니스 규칙, **트랜잭션 경계** | HTTP 개념(상태코드, 헤더) 취급 |
| Repository | 영속성 접근 | 비즈니스 규칙 |
| Entity | 도메인 상태와 불변식 | HTTP·영속성 외 관심사 |

## DB 접근 방식 (현재)

```yaml
spring.jpa.hibernate.ddl-auto: none      # 스키마는 SQL 파일이 소유. JPA가 만들지 않는다
spring.jpa.open-in-view: false           # 뷰 렌더링 중 지연 로딩 금지
logging.level.org.hibernate.SQL: debug
```

`ddl-auto: none`을 사용하기 때문에 Entity를 수정해도 DB 스키마는 자동으로 바뀌지 않습니다.

스키마를 변경할 때는 `database/init/01-schema.sql`을 수정한 뒤
DB 볼륨을 삭제하고 다시 실행해야 반영됩니다.

Entity와 실제 스키마가 어긋나면 컴파일 단계가 아니라 런타임에서 문제가 발생할 수 있으므로
두 쪽을 항상 함께 확인해야 합니다.

현재 Flyway나 Liquibase 같은 마이그레이션 도구는 사용하지 않습니다.
지금은 볼륨 삭제 후 재생성하는 방식이며, 마이그레이션 도구 선택은 TBD입니다.

## 요청 흐름 (현재)

| 메서드 | 경로 | 담당 |
| --- | --- | --- |
| POST | `/api/rooms` | `RoomController` → `RoomService` → `RoomQuestionFactory` |

```text
RoomController      요청 DTO 검증(@NotNull), 201 응답, 참여자 토큰 쿠키 설정 (방별 Path, 400일)
RoomService         @Transactional. 방 생성 3단계를 한 트랜잭션으로 묶는다
RoomQuestionFactory 카테고리별 3개 추출. 12개를 섞어서 순서까지 정해 돌려준다
Nickname            닉네임 정규화와 검증. 표시값과 중복 판정 키를 함께 만든다
```

질문의 저장 순서가 곧 표시 순서입니다.
표시 순서 컬럼이 없기 때문에 `RoomQuestionFactory`가 섞어 반환한 목록을
`saveAll`이 같은 순서로 INSERT하고, `AUTO_INCREMENT` id가 그 순서대로 붙습니다.

중간에서 별도 정렬을 하거나 순서를 바꾸면 표시 순서도 함께 바뀝니다.

응답의 방 상태는 저장된 값이 아니라 계산한 값입니다.
`room`에는 `status` 컬럼이 없고,
방장의 `submitted_at`을 기준으로 `RoomStatus.of(Participant)`가 상태를 계산합니다.

같은 판정을 서비스마다 삼항 연산자 등으로 반복하지 않습니다.
이후 조회 API에서도 같은 계산을 사용합니다.

`RoomStatus.of()`는 전달된 참여자가 실제 방장인지 확인하지 않기 때문에
호출하는 쪽에서 반드시 방장 참여자를 넘겨야 합니다.

참여자 토큰 쿠키는 방마다 `Path`가 다릅니다.

```text
Path=/api/rooms/{shareCode}
Cookie name=participantToken
```

위키 `API-규약`의 인증 절에 따른 구조입니다.

쿠키는 이름과 `Path` 조합으로 구분되기 때문에
같은 이름을 사용해도 Path가 다르면 각 방의 토큰을 별도로 저장할 수 있습니다.
브라우저도 해당 방 경로로 가는 요청에만 그 방의 쿠키를 보냅니다.

방마다 쿠키 이름을 다르게 하고 `Path=/`를 쓰는 방식은 사용하지 않습니다.
그 경우 참여한 방이 많아질수록 모든 요청에 모든 방의 쿠키가 함께 실려
요청 헤더가 계속 커집니다.

현재 방식에서는 방 경로 밖에서 참여자 자격을 읽을 수 없습니다.
하지만 PRD에서 "내가 참여한 방 목록" 같은 기능을 제공하지 않기로 했기 때문에
현재 API 구조에서는 문제가 되지 않습니다.

따라서 참여자 자격이 필요한 API는 모두 `/api/rooms/{shareCode}` 아래에 둡니다.

`Path`는 브라우저가 어떤 쿠키를 보낼지 결정하는 규칙일 뿐
서버의 권한 검사를 대신하지는 않습니다.

브라우저를 거치지 않는 클라이언트는 임의의 쿠키를 어떤 경로에도 보낼 수 있기 때문에
이후 참여자 인증이 필요한 API에서는 토큰으로 찾은 참여자의 `room_id`와
요청 경로의 방이 같은지 항상 확인해야 합니다 (`AC-SEC-ROOM-ISOLATION`).

실패 응답은 모두 `GlobalExceptionHandler`를 거칩니다.

`GlobalExceptionHandler`는 `ResponseEntityExceptionHandler`를 상속합니다.
Spring MVC가 이미 HTTP 상태를 정한 예외는 해당 상태를 유지하고,
응답 바디만 `{code, message}` 형식으로 바꿉니다.

현재 매핑은 다음과 같습니다.

- 없는 경로: `NOT_FOUND`
- 지원하지 않는 메서드: `METHOD_NOT_ALLOWED`
- 그 밖의 4xx: `VALIDATION_FAILED`

`@ExceptionHandler(Exception.class)` 하나만 사용하면
404나 405까지 일반 예외로 잡혀 500으로 처리될 수 있습니다.

방 생성과 방장 답변 제출의 트랜잭션 단위는 `docs/domain.md`의 "주요 흐름"에도 정리되어 있습니다.

## 프론트엔드가 기대하는 API (아직 백엔드에 없습니다)

아래 내용은 아직 확정된 백엔드 API 규약이 아닙니다.

위키 `API-규약`의 엔드포인트 표에는 현재 `POST /api/rooms`만 확정되어 있고,
나머지는 "엔드포인트별 상세 스펙은 각 이슈에서 정의합니다"로 남아 있습니다.

아래 표는 화면 8개를 구현하면서 프론트엔드가 임시로 가정한 형태입니다.
백엔드를 구현할 때 이 형태를 그대로 사용할 수도 있고,
다르게 정한 뒤 프론트 타입을 수정할 수도 있습니다.

어느 경우든 위키에 먼저 규약을 확정합니다.

| 메서드 | 경로 | 쓰는 화면 | 응답 요지 |
| --- | --- | --- | --- |
| GET | `/api/rooms/{shareCode}` | 05 진입 | `status`, `hostNickname`, `participantCount`, `submittedCount`, `me` |
| POST | `/api/rooms/{shareCode}/participants` | 05 참여 | `nickname`, `answerStatus` + 참여자 토큰 쿠키 |
| GET | `/api/rooms/{shareCode}/questions` | 03·06 | `questions[]` (`displayOrder`, `category`, `content`, `optionA`, `optionB`) |
| POST | `/api/rooms/{shareCode}/answers` | 03·06 제출 | `answerStatus`, `submittedCount` |
| GET | `/api/rooms/{shareCode}/participants` | 07 | `participants[]`, `submittedCount`, `totalCount` |
| GET | `/api/rooms/{shareCode}/results` | 08 | `featuredPair`, `topPairs`, `categoryLeaders`, `twistPair`, `myPairs` |
| GET | `/api/rooms/{shareCode}/results/my-pairs?offset&limit` | 08 더 보기 | `items[]`, `total`, `hasMore` |

타입 정의는 `frontend/src/features/*/types.ts`에 있고
각 필드의 근거도 주석으로 남겨 두었습니다.

`questions[]`의 `displayOrder`는 현재 스키마에 대응하는 컬럼이 없습니다.
ERD 재설계에서 `room_question.display_order`가 제거됐고,
질문 순서는 `id` 오름차순으로 정합니다 (`docs/database.md`).

백엔드를 구현할 때는 두 가지 선택지가 있습니다.

- 응답에서 `displayOrder`를 제거하고 배열 순서 자체를 사용
- 조회 결과의 인덱스를 계산해 `displayOrder`로 내려줌

아직 어느 쪽으로 할지는 정하지 않았습니다.
결정하면 위키 `API-규약`과 프론트엔드 타입을 같이 수정해야 합니다.

설계할 때 지킨 기준은 세 가지입니다.

1. **`me`는 서버가 판정합니다.**  
   참여자 토큰이 `HttpOnly` 쿠키이기 때문에 프론트는 토큰을 직접 읽을 수 없습니다.
   현재 사용자가 누구인지, 이미 제출했는지 같은 정보는 서버가 방 요약 응답에 넣어줘야 합니다.

2. **결과에 모든 Pair를 담지 않습니다.**  
   PRD 11장에서는 Pair 수가 참여자 수의 제곱에 비례하기 때문에
   전체 목록을 응답에 담지 않도록 정했습니다.
   내 Pair 목록만 별도 API에서 10개씩 조회합니다.

3. **답변은 12개를 한 번에 보냅니다.**  
   문항별로 따로 저장하면 "같은 요청이 반복돼도 한 번만 반영"해야 하는 멱등성을
   하나의 트랜잭션 안에서 판단하기 어려워집니다.

`categoryLeader.score`는 전체 케미 점수가 아니라 해당 카테고리 점수입니다.
두 값을 혼동하면 카테고리 1위 옆 점수와 같은 화면의 카테고리 타일 점수가 서로 다르게 보일 수 있습니다.
구현 과정에서 실제로 한 번 발생했던 문제입니다.

## 트랜잭션 경계 (현재 원칙)

스키마 설계에서 다음 두 가지 트랜잭션 경계를 확정했습니다.

1. **방 생성**  
   `room` 생성 → `participant` 생성(방장, `is_host = TRUE`) → `room_question` 12개 생성  
   세 단계가 하나의 트랜잭션입니다.

   2번이 실패하면 방장 없는 방이 남고,
   3번이 실패하면 질문 없는 방이 남을 수 있습니다.

2. **답변 제출**  
   `answer` 12행 INSERT와 `participant.submitted_at` 갱신을 하나의 트랜잭션으로 처리합니다.

   제출자가 방장이면 이 작업이 끝난 뒤 방 상태가 `OPEN`으로 계산됩니다.
   별도 상태 컬럼을 갱신하는 것이 아니라 방장의 `submitted_at`을 기준으로 계산합니다.

재설계 후에는 함께 맞춰야 하는 저장값이 줄었습니다.

이전 설계에서는 방 생성 시 `room.host_participant_id`,
답변 제출 시 `room.status`와 `participant.answer_status`를 함께 갱신해야 했습니다.

서로 다른 테이블에 있는 값을 함께 맞춰야 해서 DB CHECK로 묶을 수 없었고,
트랜잭션으로 일관성을 유지해야 했습니다.

현재는 같은 사실을 여러 컬럼에 저장하지 않기 때문에
상태값끼리 서로 어긋나는 문제가 없어졌습니다.

트랜잭션 경계는 위 "계층 책임"에 따라 Service에 둡니다.
`RoomService.create()`에는 `@Transactional`이 붙어 있습니다.

## 테스트 구조 (현재)

```text
backend/src/test/java/edu/flab/chemilog/
├── ChemiLogApplicationTests.java      컨텍스트 로딩
├── support/
│   ├── MySqlContainerConfig.java      MySQL 8.4 컨테이너 + database/init 적재
│   └── IntegrationTest.java           @SpringBootTest 조합을 한 곳에 모은 애노테이션
├── participant/NicknameTest.java      DB 없는 단위 테스트
└── room/RoomCreationTest.java         POST /api/rooms 통합 테스트
```

JUnit 5를 사용합니다 (`useJUnitPlatform()`).
프론트엔드에는 아직 테스트 설정이 없습니다.

`ChemiLogApplicationTests`는 단순 `@SpringBootTest`로 두지 않습니다.

`application.yml`의 datasource가 기본값 없는 `${DB_PASSWORD}`를 참조하고 있기 때문에
DB 설정 없이 애플리케이션 컨텍스트를 띄우면 Hibernate가
`Unable to determine Dialect` 오류로 종료됩니다.

실제 원인은 DB 설정인데 에러 메시지만 보면 Hibernate 설정 문제처럼 보일 수 있습니다.
기존 소스를 정리하는 과정에서 이 파일이 초기 상태로 돌아가 실제로 같은 문제가 있었고,
그 뒤 수정했습니다.

통합 테스트의 애노테이션 조합도 클래스마다 다르게 두지 않습니다.
조합이 달라지면 Spring이 다른 테스트 컨텍스트로 판단할 수 있고,
그 경우 MySQL 컨테이너 기동도 반복될 수 있습니다.

그래서 통합 테스트 설정은 `@IntegrationTest` 하나로 모읍니다.

DB를 사용하는 테스트는 Testcontainers로 실제 MySQL 8.4에 붙습니다.

인메모리 DB를 사용하지 않는 이유는 스키마의 `ENUM`,
컬럼별 collation(`utf8mb4_0900_bin`, `utf8mb4_0900_as_cs`),
`ON DELETE` 동작을 H2에서 동일하게 재현하기 어렵기 때문입니다.

이 저장소에서 발견된 DB 관련 문제도 대부분
**제약을 선언했지만 특정 값에서 예상과 다르게 통과하는 경우**였습니다 (`docs/database.md`).

이런 문제를 확인하려면 실제 MySQL의 비교 규칙이 필요합니다.

`ddl-auto: none`이므로 Testcontainers에서도 `database/init`의 SQL을 그대로 적용합니다.
테스트 환경에서도 실제 스키마 파일을 사용해야
엔티티와 스키마가 어긋난 문제를 발견할 수 있습니다.

저장 결과를 확인할 때는 리포지토리만 사용하지 않고
`JdbcTemplate`으로 실제 컬럼 값을 직접 조회합니다.

통합 테스트에는 `@Transactional`을 붙이지 않습니다.
테스트 자체의 트랜잭션에 컨트롤러 요청이 참여하면 실제 커밋이 일어나지 않아
롤백이나 트랜잭션 경계가 제대로 검증되지 않을 수 있습니다.

Testcontainers 2.x에서는 artifactId에 `testcontainers-` 접두사가 붙고,
`MySQLContainer` 패키지는 `org.testcontainers.mysql`로 변경됐으며 제네릭 타입이 아닙니다.
Spring Boot 4.1.1의 BOM이 해당 버전을 사용합니다.

## 외부 시스템

현재 외부 시스템 연동은 없습니다.

인증 공급자, 메시징 시스템, 캐시, 파일 저장소를 사용하지 않습니다.
참여자 인증은 자체 발급 토큰을 사용하고, DB에는 SHA-256 해시만 저장합니다.

## TBD 정리

초기 구현 전에 결정이 필요한 항목입니다.
결정되기 전까지 구현으로 먼저 굳히지 않습니다.

- 스키마 마이그레이션 도구
- 프론트엔드 테스트 도구 선택

예외 처리 방식은 TBD가 아닙니다.
`common/`의 다음 네 클래스로 구성되어 있습니다.

- `ApiErrorCode`
- `ApiException`
- `ErrorResponse`
- `GlobalExceptionHandler`

자세한 내용은 위 "요청 흐름" 절을 참고합니다.
네 클래스는 모두 다시 작성했습니다.
스키마와 직접 연결되지 않는 코드라 이전 구현 구조를 그대로 복원했습니다.

백엔드 테스트 전략도 TBD가 아닙니다.
Testcontainers를 사용하는 방식으로 확정했고, 위 "테스트 구조" 절에 정리했습니다.

API 응답 형식도 TBD가 아닙니다.
위키 `API-규약`에 이미 정해져 있고,
프론트엔드도 해당 형식에 맞춰 구현되어 있습니다 (`frontend/src/shared/api/client.ts`).

성공 응답은 데이터를 그대로 반환하고,
실패 응답은 `{code, message}` 형식으로 반환합니다.

남은 것은 백엔드에서 이 형식을 어떤 코드 구조로 만들어낼지에 대한 구현입니다.
