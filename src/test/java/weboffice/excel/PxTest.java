package weboffice.excel;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 픽셀 ↔ xlsx 크기 단위.
 *
 * <p>열 너비는 "기본 글꼴 숫자 한 글자" 단위다. 화면 픽셀로 옮기는 식은 OOXML 이
 * 정해 두었다: {@code px = trunc(((256×글자수 + trunc(128/글자폭))/256) × 글자폭)}.
 * 여백은 파일에 적힌 글자 수에 이미 들어 있다 - 엑셀 기본 열은 8.43글자로 보이지만
 * 파일에는 9.140625 로 적힌다.
 *
 * <p>우리는 거기에 여백 5px 을 또 더하고 있었다. 그래서 올린 파일의 열이 엑셀보다
 * 열마다 5px 씩 넓었다(2026-08-18 지적).
 */
class PxTest {

    @Test
    void 엑셀_기본_열이_파일에_9_14글자로_적힌다() {
        // 엑셀은 이 열을 "8.43글자" 로 보여 주지만 파일에는 여백까지 넣은 9.140625 를
        // 적는다. 이 값이 어긋나면 손대지 않은 열이 화면과 엑셀에서 다르게 보인다.
        int units = Px.toColumnUnits(Px.DEFAULT_COLUMN_WIDTH);

        // 같은 64px 을 내는 글자 수가 여러 개다(엑셀 식이 버림이라). 그 구간 안이면 된다.
        assertThat(units / 256.0).isBetween(9.0, 9.15);
        assertThat(Px.fromColumnUnits(units)).isEqualTo(Px.DEFAULT_COLUMN_WIDTH);
    }

    @Test
    void 열_너비가_왕복한다() {
        for (int px : new int[] { 33, 64, 80, 118, 150, 216 }) {
            assertThat(Px.fromColumnUnits(Px.toColumnUnits(px)))
                    .as("%dpx", px).isBetween(px - 1, px);
        }
    }

    @Test
    void 아주_좁은_열도_음수가_되지_않는다() {
        // 음수를 적으면 엑셀이 파일을 거부한다.
        assertThat(Px.toColumnUnits(0)).isZero();
        assertThat(Px.toColumnUnits(3)).isNotNegative();
    }

    @Test
    void 숨긴_열은_0_으로_돌아온다() {
        assertThat(Px.fromColumnUnits(0)).isZero();
    }

    @Test
    void 행_높이는_여백이_없다() {
        // 행은 포인트 단위 그대로다. 열과 달리 글자 폭을 거치지 않는다.
        assertThat(Px.toPoints(20)).isEqualTo(15.0f);
        assertThat(Px.fromPoints(15.0)).isEqualTo(20);
    }

    @Test
    void 행_높이가_왕복한다() {
        for (int px : new int[] { 20, 24, 48, 59, 100 }) {
            assertThat(Px.fromPoints(Px.toPoints(px))).as("%dpx", px).isEqualTo(px);
        }
    }

    @Test
    void 파일_글자수에_글자폭을_곱한다() {
        // 엑셀이 보여 주는 글자수는 여백(5px)을 뺀 값이고, 파일에 적힌 값은 여백까지
        // 넣은 값이다. 그래서 파일 값에 글자폭을 곱하면 그대로 화면 픽셀이 된다.
        // 실측(맑은 고딕 12pt, 글자폭 8): A 15.875→127, B 6.625→53, C 42.375→339
        // (2026-08-18 사용자 엑셀 대조).
        assertThat(Px.fromColumnChars(15.875, 8)).isEqualTo(127);
        assertThat(Px.fromColumnChars(6.625, 8)).isEqualTo(53);
        assertThat(Px.fromColumnChars(42.375, 8)).isEqualTo(339);
        // 엑셀 기본 열은 파일에 9.140625 로 적히고 화면에서 64px 이다.
        assertThat(Px.fromColumnChars(9.140625, 7)).isEqualTo(64);
    }

    @Test
    void 픽셀에서_되돌린_값이_같은_픽셀로_돌아온다() {
        for (int px : new int[] {20, 64, 100, 150, 302, 443}) {
            assertThat(Px.fromColumnUnits(Px.toColumnUnits(px, 7), 7))
                    .as(px + "px 왕복").isBetween(px - 1, px + 1);
        }
    }

    @Test
    void 소수점_그대로_옮긴다() {
        // POI 는 열 너비를 1/256 정수로 깎아 준다. 파일에 적힌 소수를 그대로 써야
        // 엑셀과 같은 값이 나온다(2026-08-18 지적).
        assertThat(Px.fromColumnChars(12.3125, 8)).isEqualTo(99);
        assertThat(Px.fromColumnChars(12.25, 8)).isEqualTo(98);
    }

    @Test
    void 행_높이는_엑셀처럼_버린다() {
        // 엑셀은 pt→px 에서 버린다. 우리가 올리는 바람에 20pt 짜리 줄이 엑셀 26px,
        // 우리 27px 이었다(2026-08-18 지적).
        assertThat(Px.fromPoints(20.0)).isEqualTo(26);
        assertThat(Px.fromPoints(15.0)).isEqualTo(20);
        assertThat(Px.fromPoints(16.5)).isEqualTo(22);
        assertThat(Px.fromPoints(21.0)).isEqualTo(28);
    }

}
