package weboffice.excel;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 기본 글꼴 숫자 폭 재기.
 *
 * <p>글꼴이 깔려 있지 않은 서버도 있으므로 값 자체를 못 박지 않는다. 대신 "재면
 * 그럴듯한 값이 나오고, 못 재면 기본값" 이라는 규칙을 지킨다.
 */
class DigitWidthTest {

    @Test
    void 글꼴이_없으면_기본값이다() {
        assertThat(DigitWidth.of(null, 11)).isEqualTo(DigitWidth.FALLBACK);
        assertThat(DigitWidth.of("", 11)).isEqualTo(DigitWidth.FALLBACK);
    }

    @Test
    void 크기가_없으면_기본값이다() {
        assertThat(DigitWidth.of("Calibri", 0)).isEqualTo(DigitWidth.FALLBACK);
    }

    @Test
    void 글자가_크면_폭도_넓다() {
        // 맑은 고딕 12pt 문서의 열이 원본보다 좁게 나왔다(2026-08-13 지적).
        // 크기를 안 보고 늘 7px 로 재던 탓이다.
        assertThat(DigitWidth.of("Malgun Gothic", 20))
                .isGreaterThan(DigitWidth.of("Malgun Gothic", 10));
    }

    @Test
    void 잘_알려진_글꼴은_깔려_있지_않아도_같은_값을_준다() {
        // 운영 이미지는 리눅스라 Calibri·맑은 고딕이 깔려 있지 않다. AWT 에 맡기면
        // 엉뚱한 대체 글꼴을 재서 Calibri 11pt 가 7 이 아니라 9 로 나오고, 올린
        // xlsx 의 열이 28% 넓어진다(2026-10-27 실측: Windows 7, 컨테이너 9).
        assertThat(DigitWidth.of("Calibri", 11)).isEqualTo(7);
        assertThat(DigitWidth.of("맑은 고딕", 12)).isEqualTo(8);
        assertThat(DigitWidth.of("Malgun Gothic", 12)).isEqualTo(8);
        assertThat(DigitWidth.of("굴림", 11)).isEqualTo(8);
    }

    @Test
    void 잰_값은_쓸_만한_범위다() {
        assertThat(DigitWidth.of("Calibri", 11)).isBetween(6, 9);
    }
}
