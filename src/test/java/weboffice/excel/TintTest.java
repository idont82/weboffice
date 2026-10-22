package weboffice.excel;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 엑셀 명암(tint) 셈.
 *
 * <p>기준값은 엑셀 색 고르기 상자가 보여 주는 표준 색이다. 엑셀은 밝기(HSL 의 L)만
 * 건드리고 색상·채도는 그대로 둔다.
 */
class TintTest {

    private static String hex(int red, int green, int blue, double tint) {
        byte[] out = Tint.applied(new byte[] { (byte) red, (byte) green, (byte) blue }, tint);
        return String.format("#%02X%02X%02X", out[0] & 0xFF, out[1] & 0xFF, out[2] & 0xFF);
    }

    @Test
    void 흰색을_15퍼센트_어둡게() {
        // "흰색, 배경1, 15% 더 어둡게". 업무 진행표의 회색 막대가 이 색이다.
        assertThat(hex(0xFF, 0xFF, 0xFF, -0.149998474074526)).isEqualTo("#D9D9D9");
    }

    @Test
    void 검정을_25퍼센트_밝게() {
        // "검정, 텍스트1, 25% 더 밝게". 업무 진행표의 머리글 칸이 이 색이다.
        assertThat(hex(0x00, 0x00, 0x00, 0.249977111117893)).isEqualTo("#404040");
    }

    @Test
    void 색이_있는_테마도_색상을_지킨다() {
        // "파랑, 강조1, 40% 더 밝게". 채널마다 따로 계산하면 여기서 색이 틀어진다.
        assertThat(hex(0x44, 0x72, 0xC4, 0.399975585192419)).isEqualTo("#8FAADC");
    }

    @Test
    void 명암이_없으면_그대로다() {
        assertThat(hex(0x12, 0x34, 0x56, 0)).isEqualTo("#123456");
    }
}
