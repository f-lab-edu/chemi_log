# Architecture

**프로젝트 초기 상태다.** 백엔드에 도메인 코드가 없고 프론트엔드는 create-next-app
보일러플레이트 그대로다. 아래에서 "현재"로 표시된 것만 실제 코드로 확인된 사실이고,
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
현재 두 쪽을 연결하는 코드는 없다. API 호출 방식·CORS·인증 전달은 **TBD**.

## Backend 패키지 구조 (결정됨, 코드는 아직 없음)

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

## 요청 흐름

구현된 엔드포인트가 없다. **TBD.**

`docs/domain.md` 의 "주요 흐름"에 방 생성·방장 답변 제출의 트랜잭션 단위가 기록돼 있다.
첫 구현 시 그 단위를 따르고, 실제 흐름이 확정되면 여기에 기록한다.

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
└── ChemiLogApplicationTests.java     @SpringBootTest, contextLoads() 하나뿐
```

JUnit 5 (`useJUnitPlatform()`). 프론트엔드에는 테스트 설정이 없다.

테스트 전략(단위/슬라이스/통합 비중, DB 를 쓰는 테스트를 어떻게 격리할지,
Testcontainers 도입 여부)은 **TBD**. 첫 구현 때 결정한다.

## 외부 시스템

없다. 인증 공급자, 메시징, 캐시, 파일 저장소 모두 사용하지 않는다.
참여자 인증은 자체 발급 토큰(SHA-256 해시 저장)을 쓴다.

## TBD 정리

첫 구현 전에 결정이 필요한 것들. 정해지기 전에 구현으로 굳히지 않는다.

- API 응답 형식 (성공/실패 공통 래퍼 여부, 에러 바디 구조)
- 예외 처리 방식 (`@RestControllerAdvice`, 커스텀 예외 계층)
- 프론트엔드 ↔ 백엔드 연결 (호출 위치, CORS, 토큰 전달 방식)
- 스키마 마이그레이션 도구
- 테스트 전략과 DB 격리 방식
- 프론트엔드 디렉터리 구조 (현재 `src/app/` 뿐)
