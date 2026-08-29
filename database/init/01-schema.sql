-- 케미방 스키마 (이슈 #1)
-- 설계 근거는 docs/database.md 참고.
--
-- 질문 불변 전제: question 의 category, content, option_a, option_b 는 한 번 넣으면 고치지 않는다.
-- 문구를 바꾸려면 새 행을 넣고 옛 행을 active = FALSE 로 내린다.
-- 이 전제 덕분에 room_question 이 question 을 참조만 해도 방의 질문이 고정된다.
--
-- **이 전제는 DB 가 강제하지 않는다.** 지키는 것은 애플리케이션과 운영 규칙이다.
-- 컬럼 단위 GRANT 로 막는 방법을 검토했으나, 테이블을 추가할 때마다 권한을 함께
-- 관리해야 하는 값이 얻는 것보다 크다고 판단했다.
--
-- 누군가 question 의 내용을 UPDATE 하면 **이미 만들어진 방의 질문이 바뀐다.**
-- 그러면 먼저 답한 사람과 나중에 답한 사람이 서로 다른 질문에 답한 것이 되고,
-- 점수 계산은 그것을 알지 못한 채 일치를 센다. 오류도 나지 않는다.

-- ---------------------------------------------------------------------------
-- question : 질문 Pool
-- 방 생성 시 카테고리별 3개씩 뽑아 room_question 이 참조한다.
-- ---------------------------------------------------------------------------
CREATE TABLE question (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    category   ENUM('CONVERSATION', 'TRAVEL', 'LIFESTYLE', 'SPENDING') NOT NULL,
    -- collation 을 명시한다. 아래 UNIQUE 가 무엇을 막는지가 여기 달렸고,
    -- 서버 기본값에 맡기면 적재하는 곳마다 달라진다. share_code 와 nickname_key 도 같다.
    -- ai_ci 는 대소문자와 accent 를 같은 값으로 본다. 문구가 그만큼만 다른 두 문항은
    -- 서로 다른 질문이 아니므로 막는 것이 맞다.
    content    VARCHAR(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
    option_a   VARCHAR(100) NOT NULL,
    option_b   VARCHAR(100) NOT NULL,
    -- 질문을 내릴 때 쓴다. 내려도 행은 남으므로 옛 방의 질문은 그대로 보인다.
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    -- 시드를 두 번 적용하면 여기서 1062 로 멈춘다. 없으면 조용히 통과한다.
    --
    -- 없을 때 무슨 일이 생기는가. 같은 문항이 새 id 로 120행 더 들어간다.
    -- room_question 의 UNIQUE (room_id, question_id) 는 id 로 비교하므로 안 걸린다.
    -- 그러면 한 방에 글자가 똑같은 질문이 두 번 나오고, 그 카테고리 점수는
    -- 서로 다른 3문항이 아니라 같은 문항 두 개로 계산된다.
    --
    -- **무엇이 막히는지는 content 의 collation 이 정한다.** 위에 명시한
    -- utf8mb4_0900_ai_ci 기준으로 아래가 동작이고, 서버 설정과 무관하게 같다.
    --
    --   막힌다   똑같은 글자
    --   막힌다   대소문자만 다른 값 ('Plan' 과 'PLAN'). ai_ci 는 case-insensitive
    --   막힌다   accent 만 다른 값 ('cafe' 와 'café'). ai_ci 는 accent-insensitive
    --   통과한다 후행 공백을 붙인 값. utf8mb4_0900_* 계열은 NO PAD
    --   통과한다 다른 카테고리의 같은 글자
    --
    -- **collation 을 지우고 서버 기본값에 맡기지 마라.** NO PAD 는 8.0 이상 전체의
    -- 성질이 아니다. utf8mb4_general_ci 와 utf8mb4_unicode_ci 는 PAD SPACE 라
    -- 그런 서버에서는 위 표의 후행 공백 줄이 뒤집힌다.
    --
    -- 문구가 비슷한 질문을 걸러내는 장치가 아니다. 그것은 시드를 쓰는 사람이 본다.
    UNIQUE KEY uk_question_category_content (category, content),
    KEY idx_question_category_active (category, active)
) ENGINE = InnoDB;

-- ---------------------------------------------------------------------------
-- room : 케미방
-- 방이 가진 고유한 사실은 공유 코드뿐이다.
-- 방 상태(HOST_ANSWERING / OPEN)는 컬럼으로 두지 않는다.
-- 방장의 submitted_at 이 있으면 OPEN 이다 (PRD 7장). 한 곳에만 적혀 있어 어긋날 수 없다.
-- ---------------------------------------------------------------------------
CREATE TABLE room (
    id                  BIGINT      NOT NULL AUTO_INCREMENT,
    -- 128-bit 난수를 URL-safe base64 로 인코딩하면 22자.
    -- 서버 기본 collation 은 대소문자를 구분하지 않아 다른 코드가 같은 값으로 취급된다.
    -- base64url 은 대소문자를 구분하므로 바이너리 비교(_bin)가 필요하다.
    --
    -- ascii_bin 이 아니라 utf8mb4_0900_bin 인 이유가 두 가지 있다.
    -- 1) ascii_bin 은 PAD SPACE 라 비교할 때 후행 공백을 무시한다. 공유 코드는 입력 문자열을
    --    그대로 비교해야 하는데, PAD SPACE 면 'abc' 와 'abc   ' 가 같은 방으로 조회된다.
    --    MySQL 8.0 이상에서 NO PAD 인 문자 collation 은
    --    utf8mb4_0900_* 계열뿐이다 (charset 이 binary 인 binary collation 은 예외로 NO PAD).
    -- 2) 커넥션은 utf8mb4 인데 컬럼이 ascii 면, 비ASCII 입력으로 조회할 때 0건이 아니라
    --    예외가 난다 (1267 Illegal mix of collations / 3988 Conversion impossible).
    --    404 가 나와야 할 자리에 500 이 나온다.
    share_code          VARCHAR(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL,
    created_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_room_share_code (share_code)
) ENGINE = InnoDB;

-- ---------------------------------------------------------------------------
-- room_question : 방에 고정된 질문 12개
-- question 의 내용을 복사하지 않고 참조만 한다. 질문이 불변이므로 조인해도 안전하다.
--
-- 표시 순서 컬럼을 두지 않는다. 순서는 id 오름차순이다.
-- 방을 만들 때 12개를 무작위로 섞어 INSERT 하므로 방마다 순서가 다르고,
-- 한 방 안에서는 모든 참여자가 같은 순서로 본다 (PRD 7장, FR-03).
-- 카테고리를 묶지 않는다. 대화 질문 다음에 소비 질문이 나올 수 있다.
--
-- 조회할 때 ORDER BY id 를 빠뜨리면 순서가 보장되지 않는다.
-- 스키마가 이 규칙을 강제하지 못하므로 조회하는 쪽이 지켜야 한다.
-- ---------------------------------------------------------------------------
CREATE TABLE room_question (
    id          BIGINT NOT NULL AUTO_INCREMENT,
    room_id     BIGINT NOT NULL,
    question_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    -- 같은 방에 같은 질문이 두 번 들어가지 않는다.
    UNIQUE KEY uk_room_question_question (room_id, question_id),
    CONSTRAINT fk_room_question_room FOREIGN KEY (room_id)
        REFERENCES room (id) ON DELETE CASCADE,
    -- RESTRICT 라 어느 방이든 쓰고 있는 질문은 지워지지 않는다.
    -- 질문을 내리려면 삭제가 아니라 question.active = FALSE 로 한다.
    CONSTRAINT fk_room_question_question FOREIGN KEY (question_id)
        REFERENCES question (id) ON DELETE RESTRICT
) ENGINE = InnoDB;

-- ---------------------------------------------------------------------------
-- participant : 참여자
-- 방장도 참여자 중 한 명이다. is_host 로 표시한다 (PRD 5장).
--
-- "방마다 방장은 한 명" 을 DB 로 강제하지 않는다. 빠뜨린 것이 아니라 결정이다.
-- is_host = TRUE 를 쓰는 곳은 방 생성 한 군데뿐이고, 링크로 들어오는 참여자는
-- 항상 FALSE 로 만들어진다. 두 번째 방장이 생길 경로가 없다.
--
-- 반면 아래 UNIQUE 두 개는 남긴다. 근거는 서로 다르다.
--   uk_participant_room_nickname  앱이 원리적으로 막지 못한다.
--                                 두 요청이 동시에 중복 조회를 통과한 뒤 각각 INSERT 할 수 있다
--   uk_participant_token          앱은 토큰 중복을 조회하지 않고 128-bit 난수를 그대로 쓴다.
--                                 이쪽이 막는 것은 경합이 아니라 난수 충돌이다.
--                                 토큰으로 참여자를 찾는 조회의 인덱스이기도 하다
-- ---------------------------------------------------------------------------
CREATE TABLE participant (
    id                BIGINT      NOT NULL AUTO_INCREMENT,
    room_id           BIGINT      NOT NULL,
    nickname          VARCHAR(12) NOT NULL,
    -- 서버 기본 collation 은 accent-insensitive 라 cafe 와 café 를 같게 본다.
    -- 정규화를 애플리케이션이 끝내므로 이 컬럼은 있는 그대로 비교한다.
    --
    -- 폭이 nickname 보다 넓은 이유: 정규화가 문자 수를 늘릴 수 있다. 늘어나는 경로가 둘이다.
    --   소문자화        U+0130 (İ) → U+0069 U+0307.  2배. NFC 로 재결합되지 않는다
    --   NFC 합성 제외   U+FB2C 같은 히브리 표현형은 3 코드포인트로 분해된다.  3배
    -- 12자 닉네임의 키가 최대 36자가 된다. 폭이 좁으면 1406 Data too long 으로 저장이 실패한다.
    -- 48 은 그 상한에 여유를 둔 값이다. 36 아래로 줄이지 마라.
    nickname_key      VARCHAR(48) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_cs NOT NULL,
    -- 토큰 원본은 발급 시 한 번만 쿠키로 내려주고 저장하지 않는다.
    access_token_hash BINARY(32)  NOT NULL,
    is_host           BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at        DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    -- 제출 여부를 이 컬럼 하나로 판단한다. NULL 이면 아직 답변 중이다.
    -- 방이 OPEN 인지도 방장 행의 이 값으로 정해진다.
    --
    -- **채울 때 NOW(6) 를 써라.** created_at 은 DB 가 DEFAULT 로 만드는데
    -- 이 컬럼만 앱이 LocalDateTime.now() 로 채우면 두 시각의 기준이 달라진다.
    -- DATETIME 은 시간대를 담지 않으므로 JVM 이 UTC 인 컨테이너에서는
    -- submitted_at 이 created_at 보다 9시간 이르게 저장되고 DB 는 오류를 내지 않는다.
    -- 위키 API-규약 이 응답 시각을 Asia/Seoul 로 정해 두었다.
    submitted_at      DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_participant_room_nickname (room_id, nickname_key),
    UNIQUE KEY uk_participant_token (access_token_hash),
    -- RESTRICT 다. room_question 의 CASCADE 와 방향이 다른 것은 의도다.
    -- 방을 지울 때 파생 데이터(질문 목록)는 따라 지워도 되지만, 사람이 남긴 것은
    -- 실수로 지워지면 안 된다. 그래서 참여자가 남아 있으면 방 삭제를 막는다.
    --
    -- 그 대가로 DELETE FROM room 이 1451 로 거부된다. 지우려면 순서를 지켜야 한다.
    --   1) 그 방의 participant 를 지운다   answer 는 CASCADE 로 함께 지워진다
    --   2) room 을 지운다                  room_question 은 CASCADE 로 함께 지워진다
    -- MVP 에는 방 삭제 기능이 없다 (PRD 7장). 운영상 필요할 때 이 순서를 따른다.
    CONSTRAINT fk_participant_room FOREIGN KEY (room_id)
        REFERENCES room (id) ON DELETE RESTRICT
) ENGINE = InnoDB;

-- ---------------------------------------------------------------------------
-- answer : 참여자의 선택
-- 제출된 답변은 수정할 수 없다 (PRD 9장, FR-06). INSERT 만 일어난다.
-- ---------------------------------------------------------------------------
CREATE TABLE answer (
    id               BIGINT NOT NULL AUTO_INCREMENT,
    participant_id   BIGINT NOT NULL,
    room_question_id BIGINT NOT NULL,
    choice           ENUM('A', 'B') NOT NULL,
    PRIMARY KEY (id),
    -- 한 사람이 한 질문에 한 번만 답한다.
    --
    -- **이것은 멱등성의 구현이 아니라 안전망이다.** 위키 API-규약 은 같은 내용의 재제출에
    -- 최초와 같은 성공 응답을, 다른 내용에는 409 를 요구하는데 이 제약은 둘을 구분하지
    -- 못한다. 앱이 판정하지 않으면 두 번째 요청은 1062 로 롤백돼 500 이 된다.
    -- docs/domain.md 의 "멱등성은 UNIQUE 로 달성되지 않습니다" 절을 보라.
    UNIQUE KEY uk_answer_participant_question (participant_id, room_question_id),
    CONSTRAINT fk_answer_participant FOREIGN KEY (participant_id)
        REFERENCES participant (id) ON DELETE CASCADE,
    CONSTRAINT fk_answer_room_question FOREIGN KEY (room_question_id)
        REFERENCES room_question (id) ON DELETE CASCADE
) ENGINE = InnoDB;
