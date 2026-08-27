package edu.flab.chemilog.room;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasLength;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edu.flab.chemilog.support.IntegrationTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.MvcResult;

/**
 * POST /api/rooms 통합 테스트.
 *
 * 검증을 JPA 리포지토리가 아니라 JdbcTemplate 으로 한다. ddl-auto 가 none 이라
 * 엔티티와 스키마가 어긋나도 애플리케이션은 뜬다. 실제 컬럼 값을 읽어야 그 어긋남이 드러난다.
 *
 * 테스트에 @Transactional 을 붙이지 않는다. 붙이면 컨트롤러가 테스트의 트랜잭션에 참여해
 * 커밋이 일어나지 않고, 롤백 검증이 검증한 척만 하게 된다.
 */
@IntegrationTest
@DisplayName("방 생성")
class RoomCreationTest {

    private static final String NICKNAME = "민수";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * 방을 지우기 전에 host_participant_id 를 비운다. fk_room_host_participant 가
     * ON DELETE RESTRICT 라 방이 가리키는 방장을 먼저 지울 수 없다.
     * room_question 은 ON DELETE CASCADE 로 방과 함께 사라진다.
     */
    @AfterEach
    void 남긴_데이터를_지운다() {
        jdbcTemplate.update("UPDATE room SET host_participant_id = NULL");
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
                .andReturn();

        // MockMvc 의 쿠키 매처에 SameSite 가 없어 원본 헤더를 본다.
        String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).contains("SameSite=Lax").contains("Path=/");
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

    @Test
    void 방장이_그_방의_첫_참여자로_등록된다() throws Exception {
        mockMvc.perform(createRoom("  Min Su  ")).andExpect(status().isCreated());

        Map<String, Object> host = jdbcTemplate.queryForMap("""
                SELECT p.id, p.nickname, p.nickname_key, p.answer_status, p.submitted_at,
                       r.id AS room_id, r.host_participant_id
                  FROM room r
                  JOIN participant p ON p.room_id = r.id
                """);

        assertThat(host.get("host_participant_id")).isEqualTo(host.get("id"));
        // 표시값은 앞뒤 공백 제거와 연속 공백 축소까지만 적용한다 (PRD 7장).
        assertThat(host.get("nickname")).isEqualTo("Min Su");
        // 키는 거기서 공백을 전부 지우고 소문자화한다. 표시값과 달라지는 것이 정상이다.
        assertThat(host.get("nickname_key")).isEqualTo("minsu");
        assertThat(host.get("answer_status")).isEqualTo("ANSWERING");
        assertThat(host.get("submitted_at")).isNull();
    }

    @Test
    void 질문_12개가_카테고리별_3개씩_고정된다() throws Exception {
        mockMvc.perform(createRoom(NICKNAME)).andExpect(status().isCreated());

        List<String> categoriesInOrder = jdbcTemplate.queryForList(
                "SELECT category FROM room_question ORDER BY display_order", String.class);

        assertThat(categoriesInOrder).containsExactly(
                "CONVERSATION", "CONVERSATION", "CONVERSATION",
                "TRAVEL", "TRAVEL", "TRAVEL",
                "LIFESTYLE", "LIFESTYLE", "LIFESTYLE",
                "SPENDING", "SPENDING", "SPENDING");

        List<Integer> orders = jdbcTemplate.queryForList(
                "SELECT display_order FROM room_question ORDER BY display_order", Integer.class);
        assertThat(orders).containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12);
    }

    @Test
    void 질문은_원본을_참조하지_않고_값으로_복사된다() throws Exception {
        mockMvc.perform(createRoom(NICKNAME)).andExpect(status().isCreated());

        Integer mismatched = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                  FROM room_question rq
                  JOIN question q ON q.id = rq.source_question_id
                 WHERE rq.content <> q.content
                    OR rq.option_a <> q.option_a
                    OR rq.option_b <> q.option_b
                    OR rq.category <> q.category
                """, Integer.class);
        assertThat(mismatched).isZero();
    }

    /**
     * 카테고리 하나라도 활성 질문이 3개를 못 채우면 방 자체를 만들지 않는다 (docs/domain.md).
     * 채워진 만큼만 만들면 방마다 문항 수가 달라져 케미 점수를 방끼리 비교할 수 없다.
     */
    @Test
    void 활성_질문이_모자라면_방을_만들지_않는다() throws Exception {
        jdbcTemplate.update(
                "UPDATE question SET active = FALSE WHERE category = 'TRAVEL' ORDER BY id LIMIT 1");

        mockMvc.perform(createRoom(NICKNAME))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));

        assertThat(countOf("room")).isZero();
        assertThat(countOf("participant")).isZero();
        assertThat(countOf("room_question")).isZero();
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
     * 매핑되지 않은 요청은 Spring MVC 가 정한 상태로 나가야 한다.
     *
     * @ExceptionHandler(Exception.class) 만 두면 ExceptionHandlerExceptionResolver 가
     * DefaultHandlerExceptionResolver 보다 먼저 돌아 404 와 405 까지 500 으로 바꾼다.
     * 오타난 경로 하나가 서버 로그에 ERROR 스택 트레이스를 남기게 된다.
     */
    @Test
    void 없는_경로와_지원하지_않는_메서드는_500_이_아니다() throws Exception {
        mockMvc.perform(post("/api/rooms/없는경로"))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/rooms"))
                .andExpect(status().isMethodNotAllowed());
    }

    /**
     * 서버는 프론트를 믿지 않는다 (docs/domain.md). 브라우저를 거치지 않는 요청이
     * 제어 문자를 그대로 실어 보낼 수 있다.
     *
     * Java 의 \p{Cntrl} 은 ASCII 범위만 보므로 U+0085 같은 문자를 잡지 못한다.
     * 통과하면 화면에서 구분되지 않는 참여자 두 명이 한 방에 생긴다.
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
     * U+00A0 와 U+3000 은 보통 공백과 화면에서 구분되지 않는다.
     * 접지 않으면 nickname_key 가 달라져 uk_participant_room_nickname 이 중복을 막지 못한다.
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

    private Integer countOf(String table) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }
}
