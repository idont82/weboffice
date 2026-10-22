package weboffice.excel;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EmuTest {

    @Test
    void 픽셀과_EMU_가_96DPI_로_변환된다() {
        // OOXML 은 1인치 = 914400 EMU 로 정의한다. 화면은 96 DPI 기준이므로
        // 1픽셀 = 914400 / 96 = 9525 EMU 다. 이 상수가 틀리면 그림이
        // xlsx 왕복 때마다 조금씩 밀린다.
        assertThat(Emu.toEmu(1)).isEqualTo(9525);
        assertThat(Emu.toEmu(100)).isEqualTo(952500);
        assertThat(Emu.toPixels(9525)).isEqualTo(1);
        assertThat(Emu.toPixels(952500)).isEqualTo(100);
    }

    @Test
    void 영은_영이다() {
        assertThat(Emu.toEmu(0)).isZero();
        assertThat(Emu.toPixels(0)).isZero();
    }

    @Test
    void 딱_떨어지지_않는_값은_버림한다() {
        // 반올림하면 왕복마다 1픽셀씩 커지거나 작아질 수 있다. 버림은 한 방향이다.
        assertThat(Emu.toPixels(9524)).isZero();
        assertThat(Emu.toPixels(19049)).isEqualTo(1);
    }

    @Test
    void 픽셀에서_EMU_로_갔다_와도_같다() {
        for (int pixels : new int[] {0, 1, 7, 96, 500, 4096}) {
            assertThat(Emu.toPixels(Emu.toEmu(pixels))).isEqualTo(pixels);
        }
    }
}
