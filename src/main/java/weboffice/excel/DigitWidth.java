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
 *
 * <p><b>흔한 글꼴은 재지 않고 적어 둔 값을 쓴다.</b> 운영 이미지는 리눅스라
 * Calibri·맑은 고딕이 깔려 있지 않다. 없는 글꼴을 AWT 에 물으면 엉뚱한 대체
 * 글꼴을 재 준다 - Calibri 11pt 가 7 이 아니라 9 가 되어, 올린 xlsx 의 열이
 * 28% 넓게 벌어졌다(2026-10-27 실측: Windows 7, 컨테이너 9). 글꼴이 깔린 곳에서
 * 나오던 값과 같으므로 Windows 의 결과는 달라지지 않는다.
 */
public final class DigitWidth {

    /** 못 재면 이 값을 쓴다. Calibri 11 의 폭이고 엑셀 기본이다. */
    public static final int FALLBACK = 7;

    /** 글자가 이보다 좁을 수는 없다. 0 이 되면 열 너비가 통째로 사라진다. */
    private static final int SMALLEST = 4;

    private static final double DPI_RATIO = 96.0 / 72.0;

    private static final Map<String, Integer> CACHE = new ConcurrentHashMap<>();

    /**
     * 적어 둔 글꼴의 숫자 한 글자 폭(em 비율).
     *
     * <p>글꼴이 깔린 Windows 에서 8·9·10·11·12·14·18pt 로 재어 소수 넷째 자리까지
     * 같은 값이 나오는 것을 확인하고 적었다(2026-10-27). 글자 크기에 그대로
     * 비례하므로 비율 하나면 어느 크기든 낼 수 있다.
     *
     * <p>Calibri 의 0.5068 은 글꼴 명세의 숫자 폭(1038/2048) 과도 맞는다.
     *
     * <p>이름은 소문자로 바꾸고 빈칸을 뗀 꼴로 찾는다 - 같은 글꼴이 "맑은 고딕"
     * 과 "Malgun Gothic" 두 이름으로 들어온다.
     */
    private static final Map<String, Double> KNOWN = Map.of(
            "calibri", 0.5068,
            "맑은고딕", 0.5508,
            "malgungothic", 0.5508,
            "굴림", 0.5742,
            "gulim", 0.5742,
            "돋움", 0.5830,
            "dotum", 0.5830,
            "바탕", 0.5957,
            "batang", 0.5957);

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
        Double perEm = KNOWN.get(fontName.toLowerCase(java.util.Locale.ROOT)
                .replace(" ", ""));
        if (perEm != null) {
            return widthOf(sizeInPoints * DPI_RATIO * perEm);
        }
        return CACHE.computeIfAbsent(fontName + "|" + sizeInPoints,
                key -> measure(fontName, sizeInPoints));
    }

    /** 잰 폭을 쓸 수 있는 픽셀 값으로. 너무 좁으면 기본값으로 물러선다. */
    private static int widthOf(double measured) {
        int width = (int) Math.floor(measured);
        return width < SMALLEST ? FALLBACK : width;
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
            return widthOf(widest);
        } catch (RuntimeException | LinkageError cannotMeasure) {
            // 글꼴이 없는 서버(컨테이너)에서도 문서는 열려야 한다.
            return FALLBACK;
        }
    }
}
