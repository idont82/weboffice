package weboffice.snapshot;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * 스냅샷 본문 누르기.
 *
 * <p>문서 하나를 JSON 으로 펴면 340줄 시트가 1~2MB 다. 열쇠말이 칸마다 되풀이돼
 * gzip 이 잘 듣는다 - 실측으로 십분의 일 아래로 준다.
 */
public final class GzipBody {

    private GzipBody() {
    }

    public static byte[] pack(String text) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
            gzip.write(text.getBytes(StandardCharsets.UTF_8));
        } catch (IOException impossible) {
            // 메모리에만 쓴다. 여기서 실패하면 고칠 수 있는 것이 없다.
            throw new IllegalStateException("스냅샷을 누르지 못했습니다", impossible);
        }
        return out.toByteArray();
    }

    /**
     * 다시 편다.
     *
     * <p>깨진 바이트는 던진다. 조용히 빈 문서를 내주면 복원이 문서를 지우는 일이 된다.
     */
    public static String unpack(byte[] packed) {
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(packed))) {
            return new String(gzip.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException broken) {
            throw new IllegalStateException("스냅샷을 펴지 못했습니다", broken);
        }
    }
}
