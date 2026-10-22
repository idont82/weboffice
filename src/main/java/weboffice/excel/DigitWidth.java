package weboffice.excel;

import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.awt.geom.AffineTransform;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 기본 글꼴 숫자 한 글자의 폭(px).
 *
 * <p>xlsx 의 열 너비 단위가 이것이다. 엑셀은 파일의 기본 글꼴로 재는데 우리는 늘
 * 7px(Calibri 11)로 재고 있었다. 맑은 고딕 12pt 로 만든 문서를 올리면 열이 원본보다
 * 10% 남짓 좁게 나왔다(2026-08-13 지적).
 *
 * <p>AWT 는 72dpi 를 쓰고 엑셀은 96dpi 로 그린다. 그래서 크기에 4/3 을 곱해 잰다.
 * 잰 값은 내림한다 - AWT 가 글자 사이 여백까지 재어 엑셀보다 조금 크게 나온다
 * (Calibri 11: AWT 7.60, 엑셀 7).
 */
public final class DigitWidth {

    /** 못 재면 이 값을 쓴다. Calibri 11 의 폭이고 엑셀 기본이다. */
    public static final int FALLBACK = 7;

    /** 글자가 이보다 좁을 수는 없다. 0 이 되면 열 너비가 통째로 사라진다. */
    private static final int SMALLEST = 4;

    private static final double DPI_RATIO = 96.0 / 72.0;

    private static final Map<String, Integer> CACHE = new ConcurrentHashMap<>();

    private DigitWidth() {
    }

    /**
     * 글꼴 이름과 크기(pt)로 잰 숫자 한 글자의 폭(px).
     *
     * <p>이름이 없거나 잴 수 없으면 {@link #FALLBACK} 이다.
     */
    public static int of(String fontName, double sizeInPoints) {
        if (fontName == null || fontName.isBlank() || sizeInPoints <= 0) {
            return FALLBACK;
        }
        return CACHE.computeIfAbsent(fontName + "|" + sizeInPoints,
                key -> measure(fontName, sizeInPoints));
    }

    private static int measure(String fontName, double sizeInPoints) {
        try {
            FontRenderContext context =
                    new FontRenderContext(new AffineTransform(), true, true);
            Font font = new Font(fontName, Font.PLAIN, 1)
                    .deriveFont((float) (sizeInPoints * DPI_RATIO));
            double widest = 0;
            for (char digit = '0'; digit <= '9'; digit++) {
                widest = Math.max(widest,
                        font.getStringBounds(String.valueOf(digit), context).getWidth());
            }
            int width = (int) Math.floor(widest);
            return width < SMALLEST ? FALLBACK : width;
        } catch (RuntimeException | LinkageError cannotMeasure) {
            // 글꼴이 없는 서버(컨테이너)에서도 문서는 열려야 한다.
            return FALLBACK;
        }
    }
}
