# Domain

케미로그의 비즈니스 개념과 불변 규칙.

**현재 구현된 도메인 코드는 없다.** 이 문서는 `database/init/01-schema.sql` 과
요구사항(PRD)에서 확인되는 내용만 기록한다. 코드로 확인되지 않은 규칙은 TBD 로 표시한다.

서비스 개요: 여러 명이 같은 질문에 각자 답하고, 답변을 비교해 서로의 케미 점수와
모임 안 관계 순위를 확인한다 (`README.md`).

## 개념

### Question: 원본 질문 (Master)

서비스가 보유한 질문 Pool. 방과 무관하게 존재하며 독립적으로 추가·수정·비활성화된다.

| 속성 | 값 |
| --- | --- |
| `category` | `CONVERSATION` / `TRAVEL` / `LIFESTYLE` / `SPENDING` |
| `content` | 질문 본문 |
| `option_a`, `option_b` | 2지선다 보기 |
| `active` | 방 생성 시 후보로 쓸지 여부 |

현재 시드는 카테고리별 정확히 3문항, 총 12문항이다 (`database/init/02-seed.sql`).

### Room: 케미방

한 번의 케미 측정 단위. 공유 코드로 접근한다.

| 상태 | 의미 |
| --- | --- |
| `HOST_ANSWERING` | 방이 만들어졌고 방장이 아직 답변 중. 다른 사람은 참여할 수 없다 |
| `OPEN` | 방장이 답변을 제출해 참여자를 받는 상태 |

**정상 상태 전이는 하나뿐이다.**

```text
HOST_ANSWERING  →  OPEN
```

역방향 전이, 그 외 상태는 현재 설계에 없다. 필요해 보여도 임의로 추가하지 마라.

`OPEN` 으로의 전이는 **방장이 답변을 제출한 뒤에만** 가능하다 (PRD 5장).
이 전이는 방장의 `participant.answer_status`, `submitted_at` 변경과 **한 트랜잭션**으로 묶인다.
두 테이블에 걸친 조건이라 DB CHECK 로 표현할 수 없다. 애플리케이션이 지켜야 한다.

### RoomQuestion: 방에 고정된 질문 스냅샷

**관계 테이블이 아니다.** 방 생성 시점의 `Question` 값 복사본이다.

원본 `Question` 이 이후 변경·삭제되어도 이미 만들어진 방의 질문은 바뀌지 않아야 한다 (PRD 8장).
`source_question_id` 는 원본 추적용 참고값이며 **`question.id` 에 대한 FK 가 아니다.**
이것은 FK 누락이 아니라 의도된 스냅샷 설계다. 근거와 임의 수정 금지 사항은
[docs/database.md](database.md) 의 "room_question 은 관계 테이블이 아니라 스냅샷이다" 참고.

불변 규칙:

- 한 방 안에서 `display_order` 는 중복될 수 없다: `UNIQUE (room_id, display_order)`
- `display_order` 는 1~12: `CHECK (display_order BETWEEN 1 AND 12)`
- 방 하나는 질문 12개를 가진다. **개수 12는 DB로 보장되지 않는다.**
  방 생성 트랜잭션과 테스트에서 확인해야 한다
- 방의 질문을 조회할 때 `question` 과 조인하지 않는다

### Participant: 참여자

방에 들어온 사람. 방장도 참여자다 (`room.host_participant_id` 가 가리킨다).

| 상태 | 의미 |
| --- | --- |
| `ANSWERING` | 답변 작성 중 |
| `SUBMITTED` | 답변 제출 완료 |

불변 규칙:

- `answer_status = SUBMITTED` ↔ `submitted_at IS NOT NULL` (DB CHECK 로 강제)
- 한 방 안에서 닉네임은 중복될 수 없다: `UNIQUE (room_id, nickname_key)`
- 방장은 반드시 **그 방의** participant 여야 한다: 복합 FK 로 강제

닉네임은 표시용 `nickname` 과 중복 검사용 `nickname_key` 로 나뉜다.
정규화(trim, 연속 공백 축소, NFC, `toLowerCase(Locale.ROOT)`)는 **전부 애플리케이션에서** 수행한다.
`Locale` 인자를 빼면 터키어 로케일에서 `I` 가 `ı` 로 바뀌어 서버마다 결과가 달라진다.

`access_token` 은 재접속 인증 자격증명이다. 원본은 발급 시 쿠키로 한 번만 내려주고
DB 에는 SHA-256 해시만 저장한다.

## 관계

```text
Room  1 ──── N  RoomQuestion    방 생성 시 12개 복사, room 삭제 시 CASCADE
Room  1 ──── N  Participant     room 삭제 시 RESTRICT (참여자가 있으면 방을 못 지움)
Room  1 ──── 1  Participant     생성자(host). nullable, 복합 FK 로 같은 방임을 강제
Question ┄┄┄ RoomQuestion       값 복사만. FK 없음
```

## 주요 흐름

### 방 생성

```text
1. room 생성            status = HOST_ANSWERING, host_participant_id = NULL
2. participant 생성     방장
3. room 갱신            host_participant_id = 방장 id
4. room_question 생성   카테고리별 3개씩 총 12개를 question 에서 복사
```

`host_participant_id` 를 nullable 로 둔 이유가 1~3 순서다. 전체가 한 트랜잭션이어야 한다.

#### 질문 12개 선정 규칙

위 4번 단계의 상세 규칙이다.

| 항목 | 규칙 |
| --- | --- |
| 배분 | 카테고리 4개에서 각각 3개씩. 합쳐서 12개 |
| 후보 조건 | `active = TRUE` 인 질문만 |
| 추출 방식 | 카테고리 안에서 무작위 |
| 후보 부족 | 네 카테고리 중 하나라도 3개를 채우지 못하면 방 생성을 실패시킨다. 트랜잭션 전체를 롤백한다 |
| 방 사이 중복 | 허용한다. 방마다 독립적으로 뽑고 방장의 이전 방 이력을 보지 않는다 |

후보 조회는 `question` 의 `idx_question_category_active (category, active)` 를 탄다.

**후보가 모자라면 방을 만들지 않는다.** 채워진 만큼만 만들면 방마다 질문 수가 달라져
케미 점수를 방끼리 비교할 수 없게 된다. 위 RoomQuestion 절의 "방 하나는 질문 12개를 가진다"
불변 규칙을 애플리케이션이 지키는 곳이 여기다. 운영 중에 질문을 잘못 비활성화하면
방 생성이 즉시 실패하므로, 질문을 내릴 때는 카테고리별 잔여 개수를 먼저 확인해야 한다.

**방 사이 중복을 막지 않는 이유는 두 가지다.** 현재 질문 풀이 카테고리별 3개뿐이라
직전 방과 다르게 뽑는 규칙을 넣으면 두 번째 방 생성이 항상 실패한다. 그리고 방 생성 시점에
방장의 이전 방 이력을 조회하지 않아도 되므로 트랜잭션에 들어가는 테이블이 늘지 않는다.

한 방 안에서의 중복은 규칙이 아니라 DB 가 막는다: `UNIQUE (room_id, source_question_id)`.

**뽑기는 풀이 작아도 무작위로 구현한다.** 지금은 카테고리별 후보가 정확히 3개라
무작위 추출의 결과가 항상 같지만, 질문을 추가하면 코드를 고치지 않고 그대로 쓴다.

### 방장 답변 제출

```text
participant.answer_status = SUBMITTED
participant.submitted_at  = now()
room.status               = OPEN
```

세 갱신이 한 트랜잭션이다. 부분 적용되면 상태가 어긋난다.

## TBD

코드나 스키마로 확인되지 않아 확정하지 않은 것들.

- `room_question.display_order` 1~12 를 부여하는 순서. 카테고리끼리 묶어서 배치할지
  전체를 섞을지 미확정. 선정 규칙(어떤 질문을 뽑는가)과 별개 문제다
- `share_code` 생성 규칙. 길이 22자(128-bit base64url)는 스키마 주석에서 확인되나
  생성 코드가 없어 미확정
- 참여자 수 상한
- 방·참여자의 만료 또는 정리 정책
- 답변(`answer`) 도메인. 다음 이슈에서 추가 예정
- 케미 점수·순위 산출 규칙
