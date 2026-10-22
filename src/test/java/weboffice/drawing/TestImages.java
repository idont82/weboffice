package weboffice.drawing;

import java.nio.charset.StandardCharsets;

/**
 * 테스트용 이미지 바이트.
 *
 * <p>형식 판별만 검증하면 되므로 진짜 이미지가 필요 없다. 파일 시그니처만 맞춘
 * 짧은 배열이면 충분하다. POI 가 실제로 읽어야 하는 곳에서는
 * {@code TestWorkbooks.onePixelPng()} 를 쓴다.
 */
public final class TestImages {

    private TestImages() {
    }

    public static byte[] png() {
        return new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3};
    }

    public static byte[] jpeg() {
        return new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 1, 2, 3};
    }

    public static byte[] gif() {
        return new byte[] {0x47, 0x49, 0x46, 0x38, 0x39, 0x61, 1, 2, 3};
    }

    public static byte[] bmp() {
        return new byte[] {0x42, 0x4D, 1, 2, 3, 4, 5, 6};
    }

    /** SVG 는 거부 대상이다. 안의 script 가 우리 오리진에서 돈다. */
    public static byte[] svg() {
        return "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>"
                .getBytes(StandardCharsets.UTF_8);
    }
}
