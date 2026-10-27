package weboffice.doc;

/**
 * 한글 문서(hwpx)의 길이 단위.
 *
 * <p>우리는 길이를 px(96dpi)로 담는다. 한글은 HWPUNIT(1/7200인치)으로 적는다.
 * 1인치가 96px 이고 7200HWPUNIT 이므로 px 하나가 75다. 글자 크기만 따로인데,
 * pt 의 100배로 적는다(10pt = 1000).
 *
 * <p>변환을 한 군데로 모으는 까닭: 여기저기서 75를 곱하면 한 군데만 빠뜨려도
 * 표가 다른 자리에 앉는다. 읽기 쪽도 같은 수를 쓴다({@code HWPUNIT_PER_PX}).
 */
public final class HwpxUnit {

    /** px 하나가 몇 HWPUNIT 인가. */
    private static final double PER_PX = 75;

    /** pt 하나를 몇으로 적는가. */
    private static final double PER_PT = 100;

    private HwpxUnit() {
    }

    /** px 를 HWPUNIT 으로. */
    public static long px(double px) {
        return Math.round(px * PER_PX);
    }

    /** pt 를 한글이 적는 글자 크기로. */
    public static int pt(double pt) {
        return (int) Math.round(pt * PER_PT);
    }
}
