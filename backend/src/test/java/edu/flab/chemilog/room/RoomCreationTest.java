package edu.flab.chemilog.room;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasLength;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import edu.flab.chemilog.common.ApiErrorCode;
import edu.flab.chemilog.support.IntegrationTest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * POST /api/rooms 통합 테스트.
 *
 * 검증을 JPA 리포지토리가 아니라 JdbcTemplate 으로 합니다. ddl-auto 가 none 이라
 * 엔티티와 스키마가 어긋나도 애플리케이션은 뜹니다. 실제 컬럼 값을 읽어야 그 어긋남이 드러납니다.
 *
 * 테스트에 @Transactional 을 붙이면 컨트롤러가 테스트의 트랜잭션에 참여해 커밋이 일어나지
 * 않습니다. 그러면 롤백 검증이 검증한 척만 하게 됩니다.
 */
@IntegrationTest
@DisplayName("방 생성")
class RoomCreationTest {

    private static final String NICKNAME = "민수";

    /**
     * 위키 API 규약의 인증 절이 정한 값입니다. 400일이 Chrome 계열이 잘라내지 않는 최댓값이라
     * PRD 의 "사이트 데이터를 지울 때까지 유지" (AC-TOKEN-RESTART) 에 가장 가깝습니다.
     */
    private static final int PARTICIPANT_TOKEN_MAX_AGE_SECONDS = (int) Duration.ofDays(400).toSeconds();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * 참여자를 먼저 지웁니다. fk_participant_room 이 ON DELETE RESTRICT 라
     * 참여자가 남아 있으면 방 삭제가 1451 로 거부됩니다 (database/init/01-schema.sql).
     * room_question 은 ON DELETE CASCADE 라 방과 함께 사라집니다.
     */
    @AfterEach
    void 남긴_데이터를_지운다() {
        jdbcTemplate.update("DELETE FROM participant");
        jdbcTemplate.update("DELETE FROM room");
        jdbcTemplate.update("UPDATE question SET active = TRUE");
    }

    @Test
    void 공유_코드와_상태를_돌려준다() throws Exception {
        mockMvc.perform(createRoom(NICKNAME))
                .andExpect(status().isCreated())
                // 128비트 난수를 base64url 로 인코딩하면 패딩 없이 22자다.
                .andExpect(jsonPath("$.shareCode").value(hasLength(22)))
                .andExpect(jsonPath("$.status").value("HOST_ANSWERING"));
    }

    @Test
    void 참여자_토큰_쿠키를_규약대로_내려준다() throws Exception {
        MvcResult result = mockMvc.perform(createRoom(NICKNAME))
                .andExpect(cookie().exists("participantToken"))
                .andExpect(cookie().httpOnly("participantToken", true))
                .andExpect(cookie().secure("participantToken", true))
                .andExpect(cookie().maxAge("participantToken", PARTICIPANT_TOKEN_MAX_AGE_SECONDS))
                .andReturn();

        // Path 는 그 방의 경로입니다. Path=/ 로 돌아가면 다음 방을 만들 때 이 자격을 잃습니다.
        String shareCode = JsonPath.read(result.getResponse().getContentAsString(), "$.shareCode");
        // MockMvc 의 쿠키 매처에 SameSite 가 없어 원본 헤더를 봅니다.
        String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).contains("SameSite=Lax").contains("Path=/api/rooms/" + shareCode + ";");
    }

    /**
     * 자격은 방별로 보존해야 합니다 (PRD 7장, AC-TOKEN-SCOPE).
     *
     * 쿠키는 이름과 Path 가 함께 식별자라 Path 가 방마다 다르면 같은 이름이어도 브라우저가
     * 덮어쓰지 않습니다. 이름이 같은데 Path 까지 같으면 방 A 를 만든 뒤 방 B 를 만들 때
     * A 의 자격이 사라집니다. 규약이 Path 로 나누기로 정했으므로 여기서 그 회귀를 잡습니다.
     */
    @Test
    void 방마다_다른_Path_로_쿠키를_내려준다() throws Exception {
        String firstPath = cookiePathOf(createRoom("민수"));
        String secondPath = cookiePathOf(createRoom("지은"));

        assertThat(firstPath).startsWith("/api/rooms/").isNotEqualTo(secondPath);
    }

    @Test
    void 토큰_원본은_저장하지_않고_해시만_남긴다() throws Exception {
        MvcResult result = mockMvc.perform(createRoom(NICKNAME)).andReturn();
        String rawToken = result.getResponse().getCookie("participantToken").getValue();

        // SHA-256 출력 길이. access_token_hash 가 BINARY(32) 인 근거다.
        Integer hashLength = jdbcTemplate.queryForObject(
                "SELECT LENGTH(access_token_hash) FROM participant", Integer.class);
        assertThat(hashLength).isEqualTo(32);

        Integer storedAsPlainText = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM participant WHERE access_token_hash = ?", Integer.class, rawToken);
        assertThat(storedAsPlainText).isZero();
    }

    /**
     * 방장 표시가 room 이 아니라 participant 에 있다. 2026-08-27 재설계로
     * room.host_participant_id 가 없어지고 is_host 로 옮겨졌다 (docs/database.md).
     */
    @Test
    void 방장이_그_방의_첫_참여자로_등록된다() throws Exception {
        mockMvc.perform(createRoom("  Min Su  ")).andExpect(status().isCreated());

        Map<String, Object> host = jdbcTemplate.queryForMap("""
                SELECT p.nickname, p.nickname_key, p.is_host, p.submitted_at, p.room_id, r.id AS room
                  FROM room r
                  JOIN participant p ON p.room_id = r.id
                """);

        assertThat(host.get("room_id")).isEqualTo(host.get("room"));
        assertThat(host.get("is_host")).isEqualTo(true);
        // 표시값은 앞뒤 공백 제거와 연속 공백 축소까지만 적용합니다 (PRD 7장).
        assertThat(host.get("nickname")).isEqualTo("Min Su");
        // 키는 거기서 공백을 전부 지우고 소문자화합니다. 표시값과 달라지는 것이 정상입니다.
        assertThat(host.get("nickname_key")).isEqualTo("minsu");
        // 방 상태를 이 컬럼으로 판정합니다. NULL 이면 HOST_ANSWERING 입니다.
        assertThat(host.get("submitted_at")).isNull();
    }

    @Test
    void 질문_12개가_카테고리별_3개씩_고정된다() throws Exception {
        mockMvc.perform(createRoom(NICKNAME)).andExpect(status().isCreated());

        // room_question 에 category 컬럼이 없습니다. 참조하는 question 에서 읽습니다.
        List<Map<String, Object>> countsByCategory = jdbcTemplate.queryForList("""
                SELECT q.category, COUNT(*) AS count
                  FROM room_question rq
                  JOIN question q ON q.id = rq.question_id
                 GROUP BY q.category
                """);

        assertThat(countsByCategory).hasSize(4);
        assertThat(countsByCategory).allSatisfy(
                row -> assertThat(row.get("count")).isEqualTo(3L));
        assertThat(countOf("room_question")).isEqualTo(12);
    }

    /**
     * 한 방에 같은 질문이 두 번 들어가지 않습니다. uk_room_question_question 이 막지만
     * 그것은 id 로 비교하므로, 글자가 같은 다른 행이 들어오는 것은 question 의
     * UNIQUE (category, content) 가 막습니다. 두 제약이 함께 있어야 성립합니다.
     */
    @Test
    void 한_방에_같은_질문이_두_번_들어가지_않는다() throws Exception {
        mockMvc.perform(createRoom(NICKNAME)).andExpect(status().isCreated());

        Integer distinctQuestions = jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT question_id) FROM room_question", Integer.class);
        assertThat(distinctQuestions).isEqualTo(12);

        Integer distinctContents = jdbcTemplate.queryForObject("""
                SELECT COUNT(DISTINCT q.content)
                  FROM room_question rq
                  JOIN question q ON q.id = rq.question_id
                """, Integer.class);
        assertThat(distinctContents).isEqualTo(12);
    }

    /**
     * 값을 복사하지 않고 question 을 참조합니다. 2026-08-27 재설계로 뒤집힌 부분이라
     * 되돌아가는 것을 여기서 막습니다 (docs/database.md).
     *
     * room_question 이 가진 컬럼은 id, room_id, question_id 셋뿐입니다.
     * content 나 option_a 가 다시 생겼다면 스냅샷이 값 복사로 되돌아갔습니다.
     */
    @Test
    void 질문을_값으로_복사하지_않고_참조한다() throws Exception {
        mockMvc.perform(createRoom(NICKNAME)).andExpect(status().isCreated());

        List<String> columns = jdbcTemplate.queryForList("""
                SELECT column_name
                  FROM information_schema.columns
                 WHERE table_schema = DATABASE() AND table_name = 'room_question'
                 ORDER BY ordinal_position
                """, String.class);

        assertThat(columns).containsExactlyInAnyOrder("id", "room_id", "question_id");
    }

    /**
     * 표시 순서는 카테고리로 묶이지 않는다 (사용자 결정, docs/domain.md).
     * 옛 설계는 display_order 1~3 을 CONVERSATION 으로 고정했다.
     *
     * 무작위를 검증하므로 방을 셋 만들어 그중 하나라도 묶이지 않으면 통과로 본다.
     * 한 방이 우연히 묶일 확률은 카테고리 배열 369,600 가지 중 1 이고,
     * 세 방이 모두 묶일 확률은 그 세제곱이라 실행마다 흔들리지 않는다.
     */
    @Test
    void 문항_순서가_카테고리로_묶이지_않는다() throws Exception {
        List<String> groupedOrder = List.of(
                "CONVERSATION", "CONVERSATION", "CONVERSATION",
                "TRAVEL", "TRAVEL", "TRAVEL",
                "LIFESTYLE", "LIFESTYLE", "LIFESTYLE",
                "SPENDING", "SPENDING", "SPENDING");

        for (int attempt = 0; attempt < 3; attempt++) {
            mockMvc.perform(createRoom("민수" + attempt)).andExpect(status().isCreated());
        }

        List<Long> roomIds = jdbcTemplate.queryForList("SELECT id FROM room ORDER BY id", Long.class);
        boolean anyShuffled = roomIds.stream().anyMatch(roomId -> !categoriesInOrder(roomId).equals(groupedOrder));

        assertThat(anyShuffled)
                .as("세 방 모두 카테고리가 묶인 순서다. display_order 방식으로 되돌아갔을 수 있다")
                .isTrue();
    }

    /**
     * 순서를 정하는 수단이 id 오름차순뿐입니다. 표시 순서 컬럼이 없으므로
     * 저장한 순서가 그대로 사용자가 보는 순서가 됩니다 (docs/domain.md).
     */
    @Test
    void 한_방_안에서_문항_순서가_고정된다() throws Exception {
        mockMvc.perform(createRoom(NICKNAME)).andExpect(status().isCreated());
        Long roomId = jdbcTemplate.queryForObject("SELECT id FROM room", Long.class);

        List<Long> firstRead = questionIdsInOrder(roomId);
        List<Long> secondRead = questionIdsInOrder(roomId);

        assertThat(firstRead).hasSize(12).containsExactlyElementsOf(secondRead);
    }

    /**
     * 방끼리 질문 중복은 허용하지만 조합까지 같으면 무작위 추출이 동작하지 않습니다.
     * 시드를 12문항으로 되돌리면 후보와 필요 개수가 같아져 모든 방이 같은 질문을 받습니다
     * (docs/domain.md). 그 회귀를 여기서 잡습니다.
     *
     * **집합으로 비교합니다.** 표시 순서는 방마다 무작위로 섞는 것이 확정된 규칙이라,
     * 리스트로 비교하면 두 방이 같은 12문항을 받아도 셔플 순서만 다르면 통과합니다.
     * 그러면 이 테스트가 선언한 회귀를 순서 무작위가 가려 줍니다.
     * 순서가 무작위인지는 문항_순서가_카테고리로_묶이지_않는다 가 따로 봅니다.
     */
    @Test
    void 방마다_다른_질문_조합을_받는다() throws Exception {
        mockMvc.perform(createRoom("민수")).andExpect(status().isCreated());
        mockMvc.perform(createRoom("지은")).andExpect(status().isCreated());

        List<Long> roomIds = jdbcTemplate.queryForList("SELECT id FROM room ORDER BY id", Long.class);
        List<Long> first = questionIdsInOrder(roomIds.get(0));
        List<Long> second = questionIdsInOrder(roomIds.get(1));

        assertThat(Set.copyOf(first)).isNotEqualTo(Set.copyOf(second));
    }

    /**
     * 카테고리 하나라도 활성 질문이 3개를 못 채우면 방 자체를 만들지 않습니다 (docs/domain.md).
     * 채워진 만큼만 만들면 방마다 문항 수가 달라져 케미 점수를 방끼리 비교할 수 없습니다.
     */
    @Test
    void 활성_질문이_모자라면_방을_만들지_않는다() throws Exception {
        // 카테고리별 30문항이라 28개를 내려야 3개 미만이 됩니다.
        jdbcTemplate.update(
                "UPDATE question SET active = FALSE WHERE category = 'TRAVEL' ORDER BY id LIMIT 28");

        mockMvc.perform(createRoom(NICKNAME))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));

        assertThat(countOf("room")).isZero();
        assertThat(countOf("participant")).isZero();
        assertThat(countOf("room_question")).isZero();
    }

    /** 내려간 질문은 후보에서 빠집니다. active = FALSE 를 무시하면 여기서 드러납니다. */
    @Test
    void 내려간_질문은_방에_들어가지_않는다() throws Exception {
        jdbcTemplate.update("UPDATE question SET active = FALSE WHERE category = 'TRAVEL'");
        jdbcTemplate.update("""
                UPDATE question SET active = TRUE
                 WHERE category = 'TRAVEL' ORDER BY id LIMIT 3
                """);

        mockMvc.perform(createRoom(NICKNAME)).andExpect(status().isCreated());

        Integer inactivePicked = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                  FROM room_question rq
                  JOIN question q ON q.id = rq.question_id
                 WHERE q.active = FALSE
                """, Integer.class);
        assertThat(inactivePicked).isZero();
    }

    @Test
    void 닉네임_규칙에_어긋나면_NICKNAME_INVALID_로_거절한다() throws Exception {
        mockMvc.perform(createRoom("가나다라마바사아자차카타파"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("NICKNAME_INVALID"));

        assertThat(countOf("room")).isZero();
    }

    @Test
    void 닉네임_필드가_없으면_VALIDATION_FAILED_로_거절한다() throws Exception {
        mockMvc.perform(post("/api/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    /**
     * 본문이 JSON null 이면 @Valid 는 돌지 않습니다. null 객체는 검증 대상이 아닙니다.
     *
     * 그래도 500 이 아닙니다. RequestResponseBodyMethodProcessor 가 검증보다 먼저
     * "역직렬화 결과가 null 인데 @RequestBody 가 required 인가" 를 확인하고
     * HttpMessageNotReadableException 을 던집니다. 그것은 부모 핸들러가 400 으로 바꿉니다.
     *
     * 자동 리뷰가 이 경로를 컨트롤러의 NullPointerException 으로 보고 500 이라고 지적해서
     * 확인했습니다. 회귀로 남깁니다.
     */
    @Test
    void 본문이_JSON_null_이면_VALIDATION_FAILED_로_거절한다() throws Exception {
        mockMvc.perform(post("/api/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("null"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        assertThat(countOf("room")).isZero();
        assertThat(countOf("participant")).isZero();
    }

    /**
     * 매핑되지 않은 요청은 Spring MVC 가 정한 상태와 규약의 공통 코드로 나가야 합니다.
     *
     * @ExceptionHandler(Exception.class) 만 두면 ExceptionHandlerExceptionResolver 가
     * DefaultHandlerExceptionResolver 보다 먼저 돌아 404 와 405 까지 500 으로 바꿉니다.
     * 오타난 경로 하나가 서버 로그에 ERROR 스택 트레이스를 남기게 됩니다.
     *
     * 없는 경로가 ROOM_NOT_FOUND 로 나가면 안 됩니다. 프론트가 code 로만 분기하므로
     * 그 코드를 받으면 "없는 방" 화면을 띄웁니다.
     */
    @Test
    void 없는_경로와_지원하지_않는_메서드는_전용_코드로_나간다() throws Exception {
        mockMvc.perform(post("/api/rooms/없는경로"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        mockMvc.perform(delete("/api/rooms"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    /**
     * 매핑에 produces 가 없으면 Spring 이 핸들러를 먼저 실행하고 반환값을 쓸 때 협상에 실패합니다.
     * 그 시점에는 roomService.create() 가 이미 커밋돼 방이 남는데 클라이언트는 실패를 받습니다.
     *
     * **406 과 남은 행 0 을 함께 봐야 합니다.** 상태만 보면 서비스가 돌고 나서 실패한 경우와
     * 구분되지 않습니다. 이 테스트가 잡는 것은 상태가 아니라 부작용이 없다는 사실입니다.
     *
     * 오류 응답의 Content-Type 을 함께 봅니다. GlobalExceptionHandler 가 그것을 명시하지 않으면
     * 이 응답 역시 Accept 로 협상해 빈 본문이 나가고 규약의 {code, message} 가 깨집니다.
     * jsonPath 는 본문만 파싱하므로 헤더는 contentTypeCompatibleWith 가 따로 봐야 합니다.
     * 그 줄이 없으면 오류 응답을 application/problem+json 으로 바꿔도 이 테스트가 통과합니다.
     */
    @Test
    void 지원하지_않는_Accept_는_방을_만들지_않는다() throws Exception {
        mockMvc.perform(post("/api/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_HTML)
                        .content("{\"nickname\":\"%s\"}".formatted(NICKNAME)))
                .andExpect(status().isNotAcceptable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("NOT_ACCEPTABLE"));

        assertThat(countOf("room")).isZero();
        assertThat(countOf("participant")).isZero();
        assertThat(countOf("room_question")).isZero();
    }

    /**
     * ApiErrorCode 는 코드마다 HTTP 상태를 하나씩 들고 있고, 프론트는 code 만 보고 화면을 정합니다.
     * 그 둘이 어긋나면 프론트가 code 로 분기할 수 없습니다.
     *
     * **응답에서 읽은 code 로 다시 enum 을 찾아 비교합니다.** 상태와 코드를 각각 적어 두면
     * 둘 다 틀린 값을 적었을 때 통과합니다. 한쪽을 다른 쪽의 기댓값으로 쓰면 그 경로가 막힙니다.
     *
     * 나온 코드 목록도 함께 봅니다. 그 줄이 없으면 네 요청이 전부 400 VALIDATION_FAILED 로
     * 나가도 상태와 코드가 짝이 맞아 통과합니다. 상태별 분기를 지웠을 때 걸리는 것이 이 줄입니다.
     *
     * 여기 넷이 GlobalExceptionHandler.toErrorCode 가 상태별로 고르는 전부입니다.
     * 5xx 는 INTERNAL_ERROR, 남은 4xx 는 400 VALIDATION_FAILED 로 갑니다.
     */
    @Test
    void 공통_코드는_선언한_HTTP_상태로만_나간다() throws Exception {
        List<MockHttpServletRequestBuilder> requests = List.of(
                post("/api/rooms/없는경로"),
                delete("/api/rooms"),
                post("/api/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_HTML)
                        .content("{\"nickname\":\"%s\"}".formatted(NICKNAME)),
                post("/api/rooms")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content(NICKNAME));

        List<String> codes = new ArrayList<>();
        for (MockHttpServletRequestBuilder request : requests) {
            MvcResult result = mockMvc.perform(request)
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andReturn();

            String code = JsonPath.read(result.getResponse().getContentAsString(), "$.code");
            codes.add(code);
            assertThat(result.getResponse().getStatus())
                    .as("code=%s", code)
                    .isEqualTo(ApiErrorCode.valueOf(code).status().value());
        }

        assertThat(codes).containsExactly("NOT_FOUND", "METHOD_NOT_ALLOWED",
                "NOT_ACCEPTABLE", "UNSUPPORTED_MEDIA_TYPE");
        assertThat(countOf("room")).isZero();
    }

    /**
     * 서버는 프론트를 믿지 않습니다 (docs/domain.md). 브라우저를 거치지 않는 요청이
     * 제어 문자를 그대로 실어 보낼 수 있습니다.
     *
     * Java 의 \p{Cntrl} 은 ASCII 범위만 보므로 U+0085 같은 문자를 잡지 못합니다.
     * 통과하면 화면에서 구분되지 않는 참여자 두 명이 한 방에 생깁니다.
     */
    @Test
    void ASCII_밖의_제어_문자도_거절한다() throws Exception {
        // U+0085 NEL, U+2028 LINE SEPARATOR, 마크업
        for (String raw : List.of("민\\u0085수", "민\\u2028수", "<b>민수</b>")) {
            mockMvc.perform(post("/api/rooms")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"nickname\":\"%s\"}".formatted(raw)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("NICKNAME_INVALID"));
        }
        assertThat(countOf("room")).isZero();
    }

    /**
     * U+00A0 와 U+3000 은 보통 공백과 화면에서 구분되지 않습니다.
     * 접지 않으면 nickname_key 가 달라져 uk_participant_room_nickname 이 중복을 막지 못합니다.
     */
    @Test
    void 보통_공백이_아닌_공백도_접는다() throws Exception {
        mockMvc.perform(post("/api/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"\\u00a0김\\u3000\\u3000민수\\u00a0\"}"))
                .andExpect(status().isCreated());

        Map<String, Object> host = jdbcTemplate.queryForMap(
                "SELECT nickname, nickname_key FROM participant");
        assertThat(host.get("nickname")).isEqualTo("김 민수");
        assertThat(host.get("nickname_key")).isEqualTo("김민수");
    }

    @Test
    void 방마다_다른_공유_코드를_받는다() throws Exception {
        mockMvc.perform(createRoom("민수")).andExpect(status().isCreated());
        mockMvc.perform(createRoom("지은")).andExpect(status().isCreated());

        List<String> codes = jdbcTemplate.queryForList("SELECT share_code FROM room", String.class);
        assertThat(codes).hasSize(2).doesNotHaveDuplicates();
    }

    private MockHttpServletRequestBuilder createRoom(String nickname) {
        return post("/api/rooms")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\":\"%s\"}".formatted(nickname));
    }

    private String cookiePathOf(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request)
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getCookie("participantToken")
                .getPath();
    }

    private List<String> categoriesInOrder(Long roomId) {
        return jdbcTemplate.queryForList("""
                SELECT q.category
                  FROM room_question rq
                  JOIN question q ON q.id = rq.question_id
                 WHERE rq.room_id = ?
                 ORDER BY rq.id
                """, String.class, roomId);
    }

    private List<Long> questionIdsInOrder(Long roomId) {
        return jdbcTemplate.queryForList(
                "SELECT question_id FROM room_question WHERE room_id = ? ORDER BY id",
                Long.class, roomId);
    }

    private Integer countOf(String table) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }
}
