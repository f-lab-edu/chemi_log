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
정규화(trim, 연속 공백 축소, NFC, 공백 전부 제거, `toLowerCase(Locale.ROOT)`)는
**전부 애플리케이션에서** 수행한다.
`Locale` 인자를 빼면 터키어 로케일에서 `I` 가 `ı` 로 바뀌어 서버마다 결과가 달라진다.

**공백 전부 제거는 `nickname_key` 에만 적용한다.** `nickname` 에는 내부 공백이 남는다.
PRD 7장이 공백 제거를 중복 판정에만 적용하도록 나눠 뒀다. `지 은` 으로 입력한 사람은
화면에 `지 은` 으로 보이고, 중복 판정에서만 `지은` 과 같은 사람으로 취급된다.
사전 중복 조회에서 이 단계를 빠뜨리면 `min su` 와 `minsu` 를 다른 이름으로 통과시킨 뒤
INSERT 에서 `UNIQUE (room_id, nickname_key)` 위반이 나서 409 대신 500 이 나간다.

**프론트엔드가 trim 과 연속 공백 축소를 이미 하지만 서버는 그것을 믿지 않는다.**
프론트의 정규화는 입력 결과를 사용자에게 미리 보여주기 위한 것이다.
`POST /api/rooms` 는 브라우저를 거치지 않고도 부를 수 있으므로, 저장되는 값은 서버가 정한다.
길이 제한과 문자 제한도 마찬가지로 서버에서 다시 본다.

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

#### display_order 부여 순서

**카테고리를 묶어서 고정 순서로 부여한다.** `RoomQuestionFactory` 가 `QuestionCategory` 의
선언 순서를 그대로 쓴다.

```text
1~3    CONVERSATION      카테고리 안에서의 순서는 무작위 추출 결과를 따른다
4~6    TRAVEL
7~9    LIFESTYLE
10~12  SPENDING
```

한 화면에 한 문항씩 넘기는 답변 화면(UI-MVP 03번)에서 같은 주제가 이어져야 사용자가
맥락을 유지한 채 답한다. 결과 화면의 카테고리 점수와 문항 순서도 이어진다.
`QuestionCategory` 의 선언 순서를 바꾸면 새로 만들어지는 방의 문항 순서가 바뀐다.

#### share_code 생성 규칙

`ShareCodeGenerator` 가 `SecureRandom` 으로 16바이트(128비트)를 뽑아 패딩 없는
base64url 로 인코딩한다. 결과는 22자다. `share_code` 는 `VARCHAR(32)` 라 여유가 있다.

`java.util.Random` 을 쓰지 않는다. 방 접근 수단이 이 코드 하나뿐이라 코드를 맞히면
방에 들어올 수 있고, 그래서 자격증명에 준한다. 로그에 남기지 않는다.

URL 경로에 그대로 들어가므로 `+` 와 `/` 가 없는 base64url 이어야 인코딩이 필요 없다.
충돌은 재시도하지 않는다. `uk_room_share_code` 가 거절하면 500 으로 나간다.

### 방장 답변 제출

```text
participant.answer_status = SUBMITTED
participant.submitted_at  = now()
room.status               = OPEN
```

세 갱신이 한 트랜잭션이다. 부분 적용되면 상태가 어긋난다.

## TBD

코드나 스키마로 확인되지 않아 확정하지 않은 것들.

- 참여자 수 상한
- 방·참여자의 만료 또는 정리 정책
- 답변(`answer`) 도메인. 다음 이슈에서 추가 예정
- 케미 점수·순위 산출 규칙
