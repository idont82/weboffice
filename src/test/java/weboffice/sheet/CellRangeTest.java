package weboffice.sheet;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CellRangeTest {

    @Test
    void 완전히_떨어진_범위는_겹치지_않는다() {
        assertThat(new CellRange(0, 0, 9, 9).overlaps(new CellRange(10, 10, 19, 19))).isFalse();
    }

    @Test
    void 행은_겹치지만_열이_떨어지면_겹치지_않는다() {
        assertThat(new CellRange(0, 0, 9, 4).overlaps(new CellRange(0, 5, 9, 9))).isFalse();
    }

    @Test
    void 열은_겹치지만_행이_떨어지면_겹치지_않는다() {
        assertThat(new CellRange(0, 0, 4, 9).overlaps(new CellRange(5, 0, 9, 9))).isFalse();
    }

    @Test
    void 모서리가_한_칸_닿으면_겹친다() {
        assertThat(new CellRange(0, 0, 9, 9).overlaps(new CellRange(9, 9, 20, 20))).isTrue();
    }

    @Test
    void 한쪽이_다른쪽을_완전히_포함하면_겹친다() {
        assertThat(new CellRange(0, 0, 99, 99).overlaps(new CellRange(10, 10, 20, 20))).isTrue();
    }

    @Test
    void 겹침_판정은_대칭이다() {
        CellRange a = new CellRange(0, 0, 9, 9);
        CellRange b = new CellRange(5, 5, 14, 14);

        assertThat(a.overlaps(b)).isEqualTo(b.overlaps(a));
        assertThat(a.overlaps(b)).isTrue();
    }

    @Test
    void 시작이_끝보다_크면_만들_수_없다() {
        assertThatThrownBy(() -> new CellRange(9, 0, 0, 9))
                .as("뒤집힌 범위를 허용하면 겹침 판정이 조용히 어긋난다")
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CellRange(0, 9, 9, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
