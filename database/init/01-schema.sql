-- 케미방 생성 스키마 (이슈 #1)
-- 설계 근거는 docs/database.md 참고.
--
-- room 과 participant 가 서로 참조하므로 CREATE TABLE 만으로는 순서를 잡을 수 없다.
-- room 의 host FK 는 파일 끝에서 ALTER TABLE 로 추가한다.

-- ---------------------------------------------------------------------------
-- question : 질문 Pool
-- 방 생성 시 카테고리별 3개씩 뽑아 room_question 으로 복사한다.
-- ---------------------------------------------------------------------------
CREATE TABLE question (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    category   ENUM('CONVERSATION', 'TRAVEL', 'LIFESTYLE', 'SPENDING') NOT NULL,
    content    VARCHAR(200) NOT NULL,
    option_a   VARCHAR(100) NOT NULL,
    option_b   VARCHAR(100) NOT NULL,
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_question_category_active (category, active)
) ENGINE = InnoDB;

-- ---------------------------------------------------------------------------
-- room : 케미방
-- host_participant_id 의 FK 는 participant 생성 후 ALTER TABLE 로 건다.
-- ---------------------------------------------------------------------------
CREATE TABLE room (
    id                  BIGINT      NOT NULL AUTO_INCREMENT,
    -- 128-bit 난수를 URL-safe base64 로 인코딩하면 22자.
    -- 서버 기본 collation 은 대소문자를 구분하지 않아 다른 코드가 같은 값으로 취급된다.
    -- base64url 은 대소문자를 구분하므로 바이너리 비교(_bin)가 필요하다.
    --
    -- ascii_bin 이 아니라 utf8mb4_0900_bin 인 이유가 두 가지 있다.
    -- 1) ascii_bin 은 PAD SPACE 라 비교할 때 후행 공백을 무시한다. 공유 코드는 입력 문자열을
    --    그대로 비교해야 하는데, PAD SPACE 면 'abc' 와 'abc   ' 가 같은 방으로 조회되고
    --    UNIQUE 인덱스에서도 충돌한다. MySQL 8.0 이상에서 NO PAD 인 문자 collation 은
    --    utf8mb4_0900_* 계열뿐이다 (charset 이 binary 인 binary collation 은 예외로 NO PAD).
    -- 2) 커넥션은 utf8mb4 인데 컬럼이 ascii 면, 비ASCII 입력으로 조회할 때 0건이 아니라
    --    예외가 난다 (1267 Illegal mix of collations / 3988 Conversion impossible).
    --    404 가 나와야 할 자리에 500 이 나온다.
    share_code          VARCHAR(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL,
    host_participant_id BIGINT      NULL,
    status              ENUM('HOST_ANSWERING', 'OPEN') NOT NULL DEFAULT 'HOST_ANSWERING',
    created_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_room_share_code (share_code)
) ENGINE = InnoDB;

-- ---------------------------------------------------------------------------
-- room_question : 방에 고정된 질문 스냅샷
-- source_question_id 에는 FK 를 걸지 않는다. 참조 무결성이 보장되지 않는 참고값이다.
-- ---------------------------------------------------------------------------
CREATE TABLE room_question (
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    room_id            BIGINT       NOT NULL,
    -- NOT NULL 이어야 아래 uk_room_question_source 가 의도대로 작동한다.
    -- MySQL 은 UNIQUE 인덱스에서 NULL 을 서로 다른 값으로 취급하므로,
    -- nullable 이면 같은 방에 같은 질문이 두 번 들어가도 막지 못한다.
    -- room_question 은 항상 question 에서 복사해 만들어지므로 NULL 이 될 이유가 없다.
    source_question_id BIGINT       NOT NULL,
    category           ENUM('CONVERSATION', 'TRAVEL', 'LIFESTYLE', 'SPENDING') NOT NULL,
    content            VARCHAR(200) NOT NULL,
    option_a           VARCHAR(100) NOT NULL,
    option_b           VARCHAR(100) NOT NULL,
    display_order      TINYINT UNSIGNED NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_room_question_order (room_id, display_order),
    UNIQUE KEY uk_room_question_source (room_id, source_question_id),
    CONSTRAINT ck_room_question_order CHECK (display_order BETWEEN 1 AND 12),
    CONSTRAINT fk_room_question_room FOREIGN KEY (room_id)
        REFERENCES room (id) ON DELETE CASCADE
) ENGINE = InnoDB;

-- ---------------------------------------------------------------------------
-- participant : 참여자
-- ---------------------------------------------------------------------------
CREATE TABLE participant (
    id                BIGINT      NOT NULL AUTO_INCREMENT,
    room_id           BIGINT      NOT NULL,
    nickname          VARCHAR(12) NOT NULL,
    -- 서버 기본 collation 은 accent-insensitive 라 cafe 와 café 를 같게 본다.
    -- 정규화를 애플리케이션이 끝내므로 이 컬럼은 있는 그대로 비교한다.
    --
    -- 폭이 nickname 보다 넓은 이유: 소문자화는 문자 수를 늘릴 수 있다.
    -- U+0130 (İ) 의 소문자 매핑은 U+0069 U+0307 두 코드포인트이고 NFC 로 재결합되지 않는다.
    -- 12자 닉네임이 24자 키가 되어 저장이 실패했다 (1406 Data too long).
    -- 사용자에게 안 보이는 내부 컬럼이므로 표시용 길이에 맞출 이유가 없다.
    nickname_key      VARCHAR(48) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_cs NOT NULL,
    -- 토큰 원본은 발급 시 한 번만 쿠키로 내려주고 저장하지 않는다.
    access_token_hash BINARY(32)  NOT NULL,
    answer_status     ENUM('ANSWERING', 'SUBMITTED') NOT NULL DEFAULT 'ANSWERING',
    created_at        DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    submitted_at      DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_participant_room_nickname (room_id, nickname_key),
    -- 중복 방지가 아니라 room 복합 FK 의 참조 대상 인덱스.
    UNIQUE KEY uk_participant_room_id (room_id, id),
    UNIQUE KEY uk_participant_token (access_token_hash),
    CONSTRAINT ck_participant_submitted CHECK (
        (answer_status = 'SUBMITTED' AND submitted_at IS NOT NULL) OR
        (answer_status = 'ANSWERING' AND submitted_at IS NULL)
    ),
    CONSTRAINT fk_participant_room FOREIGN KEY (room_id)
        REFERENCES room (id) ON DELETE RESTRICT
) ENGINE = InnoDB;

-- ---------------------------------------------------------------------------
-- 생성자가 반드시 해당 room 의 participant 여야 한다.
-- 복합 FK 라 한 컬럼이라도 NULL 이면 검사를 건너뛰므로,
-- host_participant_id 가 NULL 인 상태로 방을 먼저 만드는 순서가 유지된다.
-- ---------------------------------------------------------------------------
ALTER TABLE room
    ADD CONSTRAINT fk_room_host_participant
        FOREIGN KEY (id, host_participant_id)
        REFERENCES participant (room_id, id) ON DELETE RESTRICT;
