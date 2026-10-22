package weboffice.snapshot;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 스냅샷 본문은 눌러서 담는다. 340줄 시트가 1~2MB 인데 그대로 담을 수 없다. */
class GzipBodyTest {

    @Test
    void 누른_것을_다시_펴면_같은_글이_나온다() {
        String text = "{\"sheets\":[{\"sheetName\":\"시트1\"}]}";

        byte[] packed = GzipBody.pack(text);

        assertThat(GzipBody.unpack(packed)).isEqualTo(text);
    }

    @Test
    void 되풀이가_많은_글은_눈에_띄게_줄어든다() {
        // 시트 본문이 이렇게 생겼다 - 같은 열쇠말이 칸마다 되풀이된다.
        String text = "{\"rowNo\":1,\"columnNo\":1,\"valueType\":\"STRING\"}".repeat(200);

        byte[] packed = GzipBody.pack(text);

        assertThat(packed.length).isLessThan(text.getBytes(StandardCharsets.UTF_8).length / 10);
    }

    @Test
    void 빈_글도_다룬다() {
        assertThat(GzipBody.unpack(GzipBody.pack(""))).isEmpty();
    }

    @Test
    void 누른_것이_아니면_알려_준다() {
        // 표에 든 바이트가 깨졌을 때 조용히 빈 문서로 복원하면 안 된다.
        assertThatThrownBy(() -> GzipBody.unpack(new byte[] { 1, 2, 3 }))
                .isInstanceOf(IllegalStateException.class);
    }
}
