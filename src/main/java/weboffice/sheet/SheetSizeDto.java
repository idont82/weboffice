package weboffice.sheet;

/**
 * 손댄 행 높이·열 너비 한 줄.
 *
 * <p>기본 크기인 줄은 담지 않는다. 시트 하나에 행이 백만 개일 수 있어 전부 적으면
 * 표가 터진다 - 손댄 것만 남기고 나머지는 화면 기본값이 채운다.
 *
 * <p>단위는 픽셀이다. Univer 가 픽셀로 다루고 xlsx 변환만 따로 환산한다.
 * {@code sizePx} 는 지울 때 쓰이지 않으므로 그때는 null 이 온다.
 */
public record SheetSizeDto(String axisType, int indexNo, Integer sizePx) {

    /** 행이냐 열이냐. 둘은 모양이 같아 표를 나누지 않고 이 값으로 가른다. */
    public enum AxisType {
        ROW, COLUMN;

        /** 아무 문자열이나 들어가면 조회하는 쪽이 갈래를 못 나눈다. */
        public static AxisType parse(String value) {
            try {
                return valueOf(value);
            } catch (IllegalArgumentException | NullPointerException e) {
                throw new IllegalArgumentException("알 수 없는 축입니다: " + value);
            }
        }
    }
}
