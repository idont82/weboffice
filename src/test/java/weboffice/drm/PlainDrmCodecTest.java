package weboffice.drm;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class PlainDrmCodecTest {

    private final PlainDrmCodec codec = new PlainDrmCodec();

    @Test
    void 들여올_때_바이트를_그대로_돌려준다() {
        byte[] data = "PK\u0003\u0004 xlsx 인 척".getBytes(StandardCharsets.UTF_8);

        assertThat(codec.decrypt(data, "a.xlsx")).isEqualTo(data);
    }

    @Test
    void 내보낼_때_바이트를_그대로_돌려준다() {
        byte[] data = "내보낼 것".getBytes(StandardCharsets.UTF_8);

        assertThat(codec.encrypt(data, "a.xlsx", "u-1")).isEqualTo(data);
    }

    /**
     * 돌려주는 배열을 호출한 쪽이 고쳐도 원본이 바뀌지 않아야 한다. 같은 배열을
     * 그대로 넘기면 내보내기 경로가 응답 버퍼를 만지는 순간 캐시에 든 원본이
     * 함께 바뀐다.
     */
    @Test
    void 같은_배열을_넘기지_않는다() {
        byte[] data = {1, 2, 3};

        byte[] got = codec.decrypt(data, "a.xlsx");
        got[0] = 99;

        assertThat(data[0]).isEqualTo((byte) 1);
    }

    @Test
    void 빈_것도_터지지_않는다() {
        assertThat(codec.decrypt(new byte[0], "a.xlsx")).isEmpty();
        assertThat(codec.encrypt(new byte[0], "a.xlsx", "u-1")).isEmpty();
    }
}
