package edu.flab.chemilog.participant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import edu.flab.chemilog.common.ApiErrorCode;
import edu.flab.chemilog.common.ApiException;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 닉네임 정규화 규칙은 PRD 7장 "참여자" 절에서 왔습니다.
 *
 * DB 를 띄우지 않습니다. 정규화가 전부 애플리케이션에서 끝나야 한다는 것이 이 프로젝트의 결정이고
 * (docs/conventions.md), 그 결정이 지켜지면 이 테스트에 DB 가 필요 없습니다.
 *
 * 유니코드가 들어가는 값은 \\u 이스케이프로 적습니다. 소스에 직접 쓰면 조합형과 완성형이
 * 눈으로 구분되지 않아, 무엇을 검증하는 테스트인지 읽어서 알 수 없습니다.
 */
class NicknameTest {

    /**
     * 짝이 맞지 않는 UTF-16 대리 문자.
     *
     * 이스케이프가 아니라 char 캐스팅으로 적습니다. 소스에 이스케이프로 쓰면 javac 가
     * 컴파일 전에 그것을 문자로 바꾸는데, 짝 없는 대리 문자는 소스 인코딩 단계에서
     * 다뤄지는 값이라 도구를 거치며 온전히 보존되지 않을 수 있습니다.
     */
    private static final char HIGH_SURROGATE = (char) 0xD800;

    private static final char LOW_SURROGATE = (char) 0xDC00;

    /** e + U+0301 결합 악센트. 화면에는 완성형 café 와 똑같이 보입니다. */
    private static final String CAFE_DECOMPOSED = "cafe\u0301";

    /** U+00E9 를 쓴 완성형. */
    private static final String CAFE_COMPOSED = "caf\u00e9";

    @Nested
    @DisplayName("표시값")
    class Display {

        @Test
        void 앞뒤_공백을_제거한다() {
            assertThat(Nickname.of("  민수  ").display()).isEqualTo("민수");
        }

        @Test
        void 연속_공백을_한_칸으로_줄인다() {
            assertThat(Nickname.of("김   민수").display()).isEqualTo("김 민수");
        }

        @Test
        void 입력한_대소문자를_그대로_보여준다() {
            assertThat(Nickname.of("MinSu").display()).isEqualTo("MinSu");
        }
    }

    @Nested
    @DisplayName("중복 판정 키")
    class Key {

        @Test
        void 대소문자만_다른_닉네임은_같은_키가_된다() {
            assertThat(Nickname.of("MinSu").key()).isEqualTo(Nickname.of("minsu").key());
        }

        @Test
        void 앞뒤_공백만_다른_닉네임은_같은_키가_된다() {
            assertThat(Nickname.of(" minsu ").key()).isEqualTo(Nickname.of("minsu").key());
        }

        /**
         * PRD 7장이 중복 판정에서 공백을 전부 지우라고 정합니다. 목업 05 가 안내하는
         * `지은 · 지 은 · JIEUN · jieun 은 모두 같아요` 가 이 규칙 위에 성립합니다.
         */
        @Test
        void 내부_공백만_다른_닉네임은_같은_키가_된다() {
            assertThat(Nickname.of("지 은").key()).isEqualTo(Nickname.of("지은").key());
        }

        /**
         * 접히는 공백은 `\p{Zs}` 전부입니다. 눈으로 구분되지 않으므로 이스케이프로 적습니다.
         * U+00A0 은 NBSP, U+3000 은 전각 공백입니다.
         */
        @Test
        void 비ASCII_공백이_들어가도_같은_키가_된다() {
            assertThat(Nickname.of("\uc9c0\u00a0\uc740").key()).isEqualTo(Nickname.of("지은").key());
            assertThat(Nickname.of("\uc9c0\u3000\uc740").key()).isEqualTo(Nickname.of("지은").key());
        }

        /** 공백을 지우는 것은 키뿐입니다. 표시값에는 입력한 모양이 남습니다 (PRD 7장). */
        @Test
        void 공백_제거는_표시값에_영향을_주지_않는다() {
            assertThat(Nickname.of("지 은").display()).isEqualTo("지 은");
        }

        /**
         * 공백 제거가 서로 다른 사람을 뭉치게 만들 수 있습니다. 위키가 그것을 감수하기로
         * 정한 것이라 규칙이 살아 있는지만 확인합니다.
         */
        @Test
        void 대소문자와_공백이_함께_달라도_같은_키가_된다() {
            assertThat(Nickname.of("Min Su").key()).isEqualTo(Nickname.of("minsu").key());
        }

        /**
         * PRD 7장이 accent 는 구분하라고 요구합니다. nickname_key 의 collation 이
         * utf8mb4_0900_as_cs 인 것도 같은 근거입니다 (database/init/01-schema.sql).
         */
        @Test
        void accent_가_다르면_다른_키가_된다() {
            assertThat(Nickname.of("cafe").key()).isNotEqualTo(Nickname.of(CAFE_COMPOSED).key());
        }

        /**
         * 조합형과 완성형은 코드포인트가 다르지만 사람에게는 같은 글자입니다.
         * NFC 정규화가 이 둘을 같은 키로 만듭니다.
         */
        @Test
        void 유니코드_정규화_형태가_달라도_같은_키가_된다() {
            assertThat(Nickname.of(CAFE_DECOMPOSED).key()).isEqualTo(Nickname.of(CAFE_COMPOSED).key());
        }
    }

    @Nested
    @DisplayName("거절")
    class Reject {

        @Test
        void null_은_거절한다() {
            assertThatNicknameInvalid(null);
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "   ", "\t"})
        void 정규화하면_비는_입력은_거절한다(String raw) {
            assertThatNicknameInvalid(raw);
        }

        @Test
        void 열두_자를_넘으면_거절한다() {
            assertThatNicknameInvalid("가나다라마바사아자차카타파");
        }

        @Test
        void 열두_자까지는_받는다() {
            assertThat(Nickname.of("가나다라마바사아자차카타").display()).hasSize(12);
        }

        /**
         * 이모지 하나는 Java 의 char 두 개입니다. String.length() 로 세면 12개짜리가 24 로 나와
         * 멀쩡한 닉네임이 거절됩니다. 반대로 nickname VARCHAR(12) 는 문자 수로 세므로
         * char 로 24자인 값도 DB 에는 들어갑니다. 길이 판정은 코드포인트 기준이어야 합니다.
         */
        @Test
        void 이모지도_한_글자로_센다() {
            String twelveEmoji = "🙂".repeat(12);
            assertThat(Nickname.of(twelveEmoji).display()).isEqualTo(twelveEmoji);
        }

        /** U+0007 BEL. 화면에 표시할 수 없는 문자는 PRD 7장이 허용하지 않습니다. */
        @Test
        void 제어_문자가_있으면_거절한다() {
            assertThatNicknameInvalid("민\u0007수");
        }

        /**
         * 짝 없는 대리 문자를 거절합니다.
         *
         * 통과시키면 오류 없이 닉네임이 바뀝니다. JDBC 가 UTF-8 로 인코딩할 때 물음표로 치환하는데
         * 치환이 드라이버 안에서 일어나 MySQL 이 1366 을 내지 않습니다. 아래 셋과 물음표 문자가
         * 전부 같은 한 바이트로 저장돼 nickname_key 도 같아집니다.
         *
         * 실제 앱에 넣어 재현한 뒤 남긴 회귀입니다. 세 입력 모두 201 로 방이 만들어졌고
         * participant.nickname 이 0x3F 였습니다. 이모지가 든 닉네임을 클라이언트가 UTF-16
         * 단위로 자르면 실제로 만들어지는 값입니다.
         */
        @Test
        void 짝_없는_대리_문자를_거절한다() {
            assertThatNicknameInvalid(String.valueOf(HIGH_SURROGATE));
            assertThatNicknameInvalid(String.valueOf(LOW_SURROGATE));
            assertThatNicknameInvalid("민" + HIGH_SURROGATE + "수");
        }

        /** 위 규칙이 정상 이모지까지 막으면 안 됩니다. 온전한 짝은 코드포인트 하나입니다. */
        @Test
        void 온전한_이모지는_그대로_받는다() {
            assertThat(Nickname.of("민수🙂").display()).isEqualTo("민수🙂");
        }

        /**
         * Lo, So, Mn 이라 카테고리 금지 목록에 걸리지 않습니다. 통과하면 `지\u3164은` 이
         * `지은` 과 화면에서 같아 보이는 채로 저장되고, as_cs collation 이 U+3164 에
         * weight 를 줘서 uk_participant_room_nickname 도 두 행을 막지 않습니다.
         */
        @ParameterizedTest
        @ValueSource(
                strings = {
                    "지\u3164은",
                    "\u3164\u3164\u3164",
                    "\u115f\u1160",
                    "\uffa0민수",
                    "\u2800\u2800\u2800",
                    "민\u034f수"
                })
        void 보이지_않는_문자를_거절한다(String raw) {
            assertThatNicknameInvalid(raw);
        }

        /** 결합 문자와 변형 선택자는 혼자서는 아무것도 그리지 않습니다. 금지 목록에 넣으면 아래 이모지도 막힙니다. */
        @ParameterizedTest
        @ValueSource(strings = {"\u0301\u0301\u0301", "\ufe0f\ufe0f"})
        void 보이는_글자가_없으면_거절한다(String raw) {
            assertThatNicknameInvalid(raw);
        }

        /** 앞의 U+2764 가 보이는 글자라 통과합니다. */
        @Test
        void 변형_선택자가_붙은_이모지는_받는다() {
            assertThat(Nickname.of("민수\u2764\ufe0f").display()).isEqualTo("민수\u2764\ufe0f");
        }

        /**
         * Java 21 의 유니코드는 15.0 이라 그 뒤에 배정된 이모지를 UNASSIGNED 로 봅니다.
         * L·N·P·S 허용 목록으로 판정하면 브라우저가 통과시킨 닉네임을 서버만 거절합니다.
         * 사용자에게는 길이 오류 문구가 나가고 줄여도 계속 실패합니다.
         */
        @ParameterizedTest
        @ValueSource(strings = {"\ud83e\udea9", "\ud83e\udedc", "\ud83e\ude89"})
        void 유니코드_15_이후에_배정된_이모지도_받는다(String raw) {
            assertThat(Nickname.of(raw).display()).isEqualTo(raw);
        }

        /** 사설 영역은 글꼴마다 다르게 그려지거나 아무것도 안 그립니다. */
        @Test
        void 사설_영역_문자만으로는_거절한다() {
            assertThatNicknameInvalid("\ue000\ue001");
        }

        private void assertThatNicknameInvalid(String raw) {
            assertThatThrownBy(() -> Nickname.of(raw))
                    .asInstanceOf(InstanceOfAssertFactories.type(ApiException.class))
                    .extracting(ApiException::errorCode)
                    .isEqualTo(ApiErrorCode.NICKNAME_INVALID);
        }
    }
}
