package weboffice.workbook;

/**
 * 내가 문서에 달아 둔 표시 한 줄.
 *
 * <p>개인 것이다 - 남의 별표도 남의 색도 내 목록에 오지 않는다. 그래서 공유받은
 * 남의 문서에도 내 표시를 달 수 있다(2026-08-31 결정).
 *
 * @param starredYn 즐겨찾기면 'Y'
 * @param colorCode 색 딱지. 안 칠했으면 null
 */
public record WorkbookMarkRow(
        Long workbookSeq,
        String starredYn,
        String colorCode
) {

    public boolean starred() {
        return "Y".equals(starredYn);
    }
}
