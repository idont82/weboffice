package weboffice.excel;

/**
 * 픽셀 ↔ xlsx 크기 단위 변환. POI 경계에서만 쓴다.
 *
 * <p>DB 와 Univer 는 픽셀을 쓰는데 xlsx 는 행과 열이 서로 <b>다른 단위</b>를 쓴다.
 * 행 높이는 포인트, 열 너비는 "기본 글꼴 숫자 한 글자의 1/256" 이다. 변환을 여기
 * 한 곳에 모으지 않으면 import 와 export 가 서로 다른 상수를 쓰다 왕복마다 크기가
 * 조금씩 달라진다({@link Emu} 와 같은 이유다).
 *
 * <p>96 DPI 기준 1픽셀 = 0.75포인트다.
 *
 * <p>열 너비에는 <b>5픽셀 여백</b>이 붙는다. OOXML 의 열 너비 단위는 "기본 글꼴
 * 숫자 한 글자"(Calibri 11 에서 7픽셀)인데, 실제 그려지는 폭은
 * {@code 글자수 × 글자폭 + 5} 다. 엑셀 기본 8.43글자가 64픽셀인 것이 이 식에서
 * 나온다({@code 8.43 × 7 + 5 = 64}).
 *
 * <p>이 여백을 빠뜨려 내보낸 파일의 열이 화면보다 5픽셀씩 넓었다(2026-08-07
 * 사용자 실측). 행은 포인트 단위 그대로라 여백이 없다.
 */
public final class Px {

    /**
     * 엑셀 기본 칸 크기. 화면(Univer)도 이 값을 쓴다(2026-08-07 결정).
     *
     * <p>Univer 기본은 열 88px·행 24px 이라 엑셀 기본(8.43글자=64px, 15pt=20px)과
     * 달랐다. 우리는 손댄 줄만 저장하므로 그 차이가 손대지 않은 줄에 그대로 남아,
     * 화면에서 들어가던 글자가 엑셀에서 잘렸다(사용자 실측).
     */
    public static final int DEFAULT_COLUMN_WIDTH = 64;
    public static final int DEFAULT_ROW_HEIGHT = 20;

    private static final double POINTS_PER_PIXEL = 0.75;
    private static final double PIXELS_PER_CHARACTER = 7.0;
    private static final int UNITS_PER_CHARACTER = 256;
    /** 셀 안쪽 여백. 엑셀이 열 너비를 그릴 때 글자 폭 위에 늘 더한다. */
    private static final int COLUMN_PADDING = 5;

    private Px() {
    }

    public static float toPoints(int pixels) {
        return (float) (pixels * POINTS_PER_PIXEL);
    }

    /**
     * 포인트를 픽셀로.
     *
     * <p>엑셀처럼 버린다. 올리면 20pt 짜리 줄이 엑셀에서 26px 인데 우리는 27px 이
     * 된다(2026-08-18 지적).
     */
    public static int fromPoints(double points) {
        return (int) (points / POINTS_PER_PIXEL + 1e-9);
    }

    public static int toColumnUnits(int pixels) {
        return toColumnUnits(pixels, (int) PIXELS_PER_CHARACTER);
    }

    /**
     * 픽셀을 xlsx 열 너비 단위로.
     *
     * <p>{@link #fromColumnChars(double, int)} 의 반대다 - 글자 폭으로 나눈다.
     */
    public static int toColumnUnits(int pixels, int digitWidth) {
        int width = digitWidth <= 0 ? (int) PIXELS_PER_CHARACTER : digitWidth;
        if (pixels <= 0) {
            return 0;
        }
        return (int) Math.max(0, Math.round((double) pixels / width * UNITS_PER_CHARACTER));
    }

    public static int fromColumnUnits(int units) {
        return fromColumnUnits(units, (int) PIXELS_PER_CHARACTER);
    }

    /**
     * 파일이 쓰는 글자 폭으로 잰 열 너비(px).
     *
     * <p>엑셀은 파일의 기본 글꼴로 잰다. 늘 7px(Calibri 11)로 재면 큰 글꼴로 만든
     * 문서의 열이 원본보다 좁게 나온다(2026-08-13 지적: 맑은 고딕 12pt 문서).
     */
    /**
     * 파일에 적힌 글자 수(소수 그대로)로 잰 열 너비(px).
     *
     * <p>POI 는 열 너비를 1/256 정수로 깎아 준다. 그 바람에 엑셀보다 1px 좁은 열이
     * 생겼다(2026-08-18 지적: E열 우리 98px, 엑셀 99px). xlsx 의 {@code col@width}
     * 를 소수 그대로 받으면 엑셀과 같은 값이 나온다.
     */
    public static int fromColumnChars(double chars, int digitWidth) {
        if (chars <= 0) {
            return 0;
        }
        int width = digitWidth <= 0 ? (int) PIXELS_PER_CHARACTER : digitWidth;
        // 파일에 적힌 글자 수에 글자 폭을 곱하면 그대로 화면 픽셀이다. 엑셀이 창에
        // 보여 주는 글자 수는 여백(5px)을 뺀 값이고, 파일 값은 여백까지 넣은 값이다
        // - 그래서 여백을 따로 더하거나 빼면 안 된다(2026-08-18 엑셀 대조:
        // A 15.875→127, B 6.625→53, C 42.375→339, 기본열 9.140625×7→64).
        return (int) Math.round(chars * width);
    }

    public static int fromColumnUnits(int units, int digitWidth) {
        return fromColumnChars((double) units / UNITS_PER_CHARACTER, digitWidth);
    }
}
