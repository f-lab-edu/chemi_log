# Architecture

**백엔드는 방 생성 API 까지 구현됐다.** 프론트엔드는 목업 8개와 로딩·오류 화면까지 구현됐고,
백엔드 연동은 `POST /api/rooms` 만 실제로 통과했다. 나머지 화면은 아직 없는 API 를 가정하고 만들었다.
아래에서 "현재"로 표시된 것만 실제 코드로 확인된 사실이고,
나머지는 TBD 이거나 결정만 되고 코드로는 아직 없는 상태다.

## 기술 스택 (현재)

| 영역 | 스택 | 근거 |
| --- | --- | --- |
| Backend | Java 21, Spring Boot 4.1.1, Spring MVC, Spring Data JPA, Bean Validation | `backend/build.gradle` |
| 빌드 | Gradle (Wrapper 9.4.1) | `backend/gradle/wrapper/` |
| Frontend | Next.js 16.3.2, React 19.2.8, TypeScript 5, Tailwind CSS v4, App Router | `frontend/package.json` |
| Database | MySQL 8.4 (Docker Compose) | `database/docker-compose.yml` |
| 정적 분석 | SonarCloud (PR 단계, GitHub Actions) | `.github/workflows/sonarcloud-analyze.yml` (origin/main) |

### Spring Boot 4 특성이 반영된 부분

`build.gradle` 에 Boot 3 과 다른 선택이 세 군데 있다. 참고 자료를 볼 때 주의해야 한다.

```text
spring-boot-starter-web        →  spring-boot-starter-webmvc
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

프론트엔드와 백엔드는 별도 프로세스이며 각각 3000, 8080 포트를 쓴다 (`README.md`).

## 프론트엔드 ↔ 백엔드 연결 (현재)

**브라우저는 백엔드를 직접 부르지 않는다.** 항상 프론트엔드와 같은 오리진의 `/api` 를 부르고,
Next 의 rewrite 가 백엔드로 넘긴다 (`frontend/next.config.ts`).

```text
브라우저 ──► localhost:3000/api/rooms ──rewrite──► localhost:8080/api/rooms
```

대상 오리진은 `BACKEND_ORIGIN` 이며 기본값은 `http://localhost:8080` 이다.
브라우저 번들에는 들어가지 않는다 (`frontend/.env.example`).

**이 값은 런타임 변수가 아니라 빌드 시점에 굳는 값이다.** `rewrites()` 는 빌드 때 실행되어
`.next/routes-manifest.json` 에 문자열로 박히고, `next start` 는 그 매니페스트를 읽는다.
실행할 때 `BACKEND_ORIGIN` 을 바꿔도 요청은 빌드에 박힌 오리진으로 나간다.

```text
BACKEND_ORIGIN=http://localhost:8090 npx next start   실측 결과 8080 이 요청을 받았다
```

지금은 배포 방식이 정해지지 않아 이대로 둔다. **배포 대상마다 다시 빌드해야 한다.**
한 번 빌드한 이미지를 여러 환경에 올리는 방식으로 가면 rewrite 로는 안 되고,
요청마다 `process.env` 를 읽는 자리(Route Handler 나 `middleware.ts`)로 옮겨야 한다.
그렇게 옮겨도 브라우저가 보는 오리진은 그대로라 아래 `SameSite=Lax` 근거는 유지된다.

**이 방식을 고른 이유는 쿠키다.** 위키 `API-규약` 이 참여자 토큰을
`SameSite=Lax` 쿠키로 못박았다. 브라우저가 백엔드를 직접 부르면 배포 환경에서 도메인이
달라져 이 쿠키가 요청에 붙지 않는다. 프록시를 두면 브라우저 입장에서 항상 같은 오리진이라
`SameSite=Lax` 를 그대로 두고도 쿠키가 전달되고, 백엔드에 CORS 설정을 둘 필요도 없다.

**로컬에서는 이 문제가 드러나지 않는다.** `localhost:3000` 과 `localhost:8080` 은
포트만 다르고 호스트가 같아 same-site 로 취급된다. 직접 호출로 바꿔도 로컬에서는 잘 도는 것처럼
보이다가 배포 후에 인증만 실패한다. 되돌리지 마라.

## 프론트엔드 디렉터리 구조 (현재)

백엔드와 같은 기준으로 **도메인별로 먼저 나눈다.**

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

`app/` 아래에는 라우팅에 필요한 파일만 둔다. 화면의 알맹이는 `features/` 에 있다.
`shared/` 는 여러 도메인이 실제로 공유하게 됐을 때만 채운다.

**초대 링크만 `/r/` 로 짧게 둔다.** 목업이 `chemilog.app/r/<코드>` 로 그렸고,
공유 코드가 22자라 경로가 길면 메신저에서 줄바꿈된다.

**데이터를 읽는 화면은 전부 클라이언트 컴포넌트다.** `apiFetch` 가 쓰는 상대 경로는
브라우저에서만 rewrite 를 탄다. 서버 컴포넌트에서 부르려면 절대 URL 이 필요하고,
그러면 백엔드를 직접 부르게 되어 위의 `SameSite=Lax` 근거가 사라진다.
로딩과 오류 상태는 `shared/hooks/useApiResource` 가 함께 돌려준다.

## Backend 패키지 구조 (현재)

베이스 패키지는 `edu.flab.chemilog` (`ChemiLogApplication.java` 로 확인).

**도메인별로 먼저 나누고 그 안에 계층을 둔다.**

```text
edu.flab.chemilog
├── room/
│   ├── Room.java                 Entity
│   ├── RoomRepository.java
│   ├── RoomService.java
│   ├── RoomController.java
│   └── dto/
├── question/
├── participant/
└── common/                       여러 도메인이 공유하는 것만
```

계층별(`controller/`, `service/`, …)이 아니라 도메인별을 택한 이유는, 기능 하나를 고칠 때
읽어야 할 파일이 한 디렉터리에 모이기 때문이다. AI 가 탐색할 context 범위가 좁아진다.

`common/` 은 **여러 도메인이 실제로 공유하게 됐을 때만** 만든다.
"나중에 쓸 것 같아서" 미리 만들지 않는다.

`room_question` 은 `Room` 의 일부이므로 `room/` 아래에 둔다. 별도 최상위 패키지로 빼지 않는다.

## 계층 책임 (권장안, 미검증)

아직 구현이 없어 실제로 이 경계가 지켜질지 확인되지 않았다. 첫 구현에서 조정될 수 있다.

| 계층 | 책임 | 하지 않는 것 |
| --- | --- | --- |
| Controller | HTTP 요청/응답 매핑, 요청 DTO 검증 | 비즈니스 규칙 판단, Entity 직접 노출 |
| Service | 비즈니스 규칙, **트랜잭션 경계** | HTTP 개념(상태코드, 헤더) 취급 |
| Repository | 영속성 접근 | 비즈니스 규칙 |
| Entity | 도메인 상태와 불변식 | HTTP·영속성 외 관심사 |

## DB 접근 방식 (현재)

```yaml
spring.jpa.hibernate.ddl-auto: none      # 스키마는 SQL 파일이 소유. JPA 가 만들지 않는다
spring.jpa.open-in-view: false           # 뷰 렌더링 중 지연 로딩 금지
logging.level.org.hibernate.SQL: debug
```

**`ddl-auto: none` 이 중요하다.** Entity 를 고쳐도 스키마는 바뀌지 않는다.
스키마 변경은 `database/init/01-schema.sql` 을 고치고 볼륨을 지운 뒤 재기동해야 반영된다.
Entity 와 스키마가 어긋나면 **런타임에 터진다.** 둘을 항상 함께 확인하라.

마이그레이션 도구(Flyway, Liquibase)는 현재 없다. 볼륨 삭제 후 재생성 방식이며 **TBD** 다.

## 요청 흐름 (현재)

| 메서드 | 경로 | 담당 |
| --- | --- | --- |
| POST | `/api/rooms` | `RoomController` → `RoomService` → `RoomQuestionFactory` |

```text
RoomController      요청 DTO 검증(@NotNull), 201 응답, 참여자 토큰 쿠키 설정
RoomService         @Transactional. 방 생성 4단계를 한 트랜잭션으로 묶는다
RoomQuestionFactory 카테고리별 3개 추출과 display_order 부여
Nickname            닉네임 정규화와 검증. 표시값과 중복 판정 키를 함께 만든다
```

실패는 전부 `GlobalExceptionHandler` 를 지난다. `ResponseEntityExceptionHandler` 를
상속해서 Spring MVC 가 이미 상태를 정한 예외(없는 경로, 지원하지 않는 메서드)는
그 상태를 살리고 바디만 규약의 `{code, message}` 로 바꾼다.
`@ExceptionHandler(Exception.class)` 만 두면 404 와 405 까지 500 으로 나간다.

`docs/domain.md` 의 "주요 흐름"에 방 생성·방장 답변 제출의 트랜잭션 단위가 기록돼 있다.

## 프론트엔드가 기대하는 API (아직 백엔드에 없다)

**확정된 규약이 아니다.** 위키 `API-규약` 의 엔드포인트 표에는 `POST /api/rooms` 하나뿐이고,
나머지는 "엔드포인트별 상세 스펙은 각 이슈에서 정의한다" 로 남아 있다.
아래는 화면 8개를 만들면서 프론트가 **가정한 형태**다. 백엔드를 구현할 때 이대로 맞추거나,
다르게 정하고 프론트를 고치면 된다. 어느 쪽이든 위키에 먼저 확정해야 한다.

| 메서드 | 경로 | 쓰는 화면 | 응답 요지 |
| --- | --- | --- | --- |
| GET | `/api/rooms/{shareCode}` | 05 진입 | `status`, `hostNickname`, `participantCount`, `submittedCount`, `me` |
| POST | `/api/rooms/{shareCode}/participants` | 05 참여 | `nickname`, `answerStatus` + 참여자 토큰 쿠키 |
| GET | `/api/rooms/{shareCode}/questions` | 03·06 | `questions[]` (`displayOrder`, `category`, `content`, `optionA`, `optionB`) |
| POST | `/api/rooms/{shareCode}/answers` | 03·06 제출 | `answerStatus`, `submittedCount` |
| GET | `/api/rooms/{shareCode}/participants` | 07 | `participants[]`, `submittedCount`, `totalCount` |
| GET | `/api/rooms/{shareCode}/results` | 08 | `featuredPair`, `topPairs`, `categoryLeaders`, `twistPair`, `myPairs` |
| GET | `/api/rooms/{shareCode}/results/my-pairs?offset&limit` | 08 더 보기 | `items[]`, `total`, `hasMore` |

타입 정의는 `frontend/src/features/*/types.ts` 에 있고 각 필드에 근거를 주석으로 달아 뒀다.

**설계할 때 지킨 것 세 가지**

1. **`me` 를 서버가 판정한다.** 참여자 토큰이 `HttpOnly` 쿠키라 프론트가 읽지 못한다.
   내가 누구인지, 이미 제출했는지를 프론트가 알 방법이 없으므로 방 요약에 담는다.
2. **결과에 모든 Pair 를 담지 않는다.** PRD 11장이 "Pair 수는 참여자 수의 제곱에 비례하므로
   전체 목록을 응답에 담으면 인원이 늘 때 응답 크기가 감당할 수 없게 커진다" 고 못박았다.
   내 Pair 목록만 10개씩 따로 받는다.
3. **답변은 12개를 한 번에 보낸다.** 문항별로 보내면 "같은 요청이 반복돼도 한 번만 반영"
   (위키 `API-규약` 멱등성)을 서버가 한 트랜잭션 안에서 판정하기 어렵다.

**`categoryLeader.score` 는 전체 점수가 아니라 그 카테고리 점수다.** 둘을 헷갈리면
카테고리 1위 옆 숫자가 같은 화면의 카테고리 타일과 어긋난다. 구현 중에 실제로 겪었다.

## 트랜잭션 경계 (현재 원칙)

스키마 설계에서 도출된 것으로 두 가지가 확정돼 있다.

1. **방 생성**: room 생성 → participant 생성 → room.host_participant_id 갱신 →
   room_question 12개 생성이 한 트랜잭션. `host_participant_id` 가 nullable 인 이유가 이 순서다.
2. **방장 답변 제출**: participant 상태·시각 갱신과 room.status 갱신이 한 트랜잭션.
   두 테이블에 걸친 조건이라 DB CHECK 로 표현할 수 없다.

경계를 어느 계층에 둘지는 위 "계층 책임"의 권장안(Service)을 따르되, 첫 구현에서 검증한다.

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

JUnit 5 (`useJUnitPlatform()`). 프론트엔드에는 테스트 설정이 없다.

**DB 를 쓰는 테스트는 Testcontainers 로 실제 MySQL 8.4 에 붙는다.** 인메모리 DB 를 쓰지 않는
이유는 스키마의 `ENUM`, `CHECK`, 복합 FK, `utf8mb4_0900_bin` 을 H2 가 재현하지 못하기 때문이다.
`ddl-auto: none` 이라 컨테이너도 `database/init` 의 SQL 을 그대로 먹는다. 그래야 엔티티와
스키마가 어긋난 것이 테스트에서 드러난다.

검증은 리포지토리가 아니라 `JdbcTemplate` 으로 실제 컬럼 값을 읽어서 한다.
`@Transactional` 을 테스트에 붙이지 않는다. 붙이면 컨트롤러가 테스트의 트랜잭션에 참여해
커밋이 일어나지 않고, 롤백 검증이 검증한 척만 하게 된다.

Testcontainers 2.x 는 artifactId 에 `testcontainers-` 접두사가 붙고
`MySQLContainer` 가 `org.testcontainers.mysql` 로 옮겨졌으며 제네릭이 아니다.
Boot 4.1.1 의 BOM 이 이 버전을 가리킨다.

## 외부 시스템

없다. 인증 공급자, 메시징, 캐시, 파일 저장소 모두 사용하지 않는다.
참여자 인증은 자체 발급 토큰(SHA-256 해시 저장)을 쓴다.

## TBD 정리

첫 구현 전에 결정이 필요한 것들. 정해지기 전에 구현으로 굳히지 않는다.

- 스키마 마이그레이션 도구
- 프론트엔드 테스트 도구 선택

**예외 처리 방식은 TBD 가 아니다.** `common/` 의 `ApiErrorCode`, `ApiException`,
`ErrorResponse`, `GlobalExceptionHandler` 로 구현했다. 위 "요청 흐름" 절 참고.

**백엔드 테스트 전략도 TBD 가 아니다.** Testcontainers 로 확정했다. 위 "테스트 구조" 절 참고.

**API 응답 형식은 TBD 가 아니다.** 위키 `API-규약` 에 확정돼 있고
프론트엔드는 이미 그것을 따라 구현했다 (`frontend/src/shared/api/client.ts`).
성공은 데이터를 그대로 반환하고 실패는 `{code, message}` 로 반환한다.
남은 것은 백엔드가 그 형태를 어떤 방식으로 만들어 내는가다.
