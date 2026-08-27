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
 * 닉네임 정규화 규칙은 PRD 7장 "참여자" 절에서 왔다.
 *
 * DB 를 띄우지 않는다. 정규화가 전부 애플리케이션에서 끝나야 한다는 것이 이 프로젝트의 결정이고
 * (docs/conventions.md), 그 결정이 지켜지면 이 테스트에 DB 가 필요 없다.
 *
 * 유니코드가 들어가는 값은 \\u 이스케이프로 적는다. 소스에 직접 쓰면 조합형과 완성형이
 * 눈으로 구분되지 않아, 무엇을 검증하는 테스트인지 읽어서 알 수 없다.
 */
class NicknameTest {

    /** e + U+0301 결합 악센트. 화면에는 완성형 café 와 똑같이 보인다. */
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
         * PRD 7장이 accent 는 구분하라고 요구한다. nickname_key 의 collation 이
         * utf8mb4_0900_as_cs 인 것도 같은 근거다 (database/init/01-schema.sql).
         */
        @Test
        void accent_가_다르면_다른_키가_된다() {
            assertThat(Nickname.of("cafe").key()).isNotEqualTo(Nickname.of(CAFE_COMPOSED).key());
        }

        /**
         * 조합형과 완성형은 코드포인트가 다르지만 사람에게는 같은 글자다.
         * NFC 정규화가 이 둘을 같은 키로 만든다.
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
         * 이모지 하나는 Java 의 char 두 개다. String.length() 로 세면 12개짜리가 24 로 나와
         * 멀쩡한 닉네임이 거절된다. 반대로 nickname VARCHAR(12) 는 문자 수로 세므로
         * char 로 24자인 값도 DB 에는 들어간다. 길이 판정은 코드포인트 기준이어야 한다.
         */
        @Test
        void 이모지도_한_글자로_센다() {
            String twelveEmoji = "🙂".repeat(12);
            assertThat(Nickname.of(twelveEmoji).display()).isEqualTo(twelveEmoji);
        }

        /** U+0007 BEL. 화면에 표시할 수 없는 문자는 PRD 7장이 허용하지 않는다. */
        @Test
        void 제어_문자가_있으면_거절한다() {
            assertThatNicknameInvalid("민\u0007수");
        }

        private void assertThatNicknameInvalid(String raw) {
            assertThatThrownBy(() -> Nickname.of(raw))
                    .asInstanceOf(InstanceOfAssertFactories.type(ApiException.class))
                    .extracting(ApiException::errorCode)
                    .isEqualTo(ApiErrorCode.NICKNAME_INVALID);
        }
    }
}
