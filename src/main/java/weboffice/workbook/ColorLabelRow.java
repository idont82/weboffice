package weboffice.workbook;

/**
 * 색에 붙인 내 이름. "빨강" 대신 "급함" 처럼 부른다.
 *
 * <p>이름을 안 지은 색은 아예 행이 없다. 화면이 기본 이름으로 채운다.
 */
public record ColorLabelRow(
        String colorCode,
        String labelName
) {
}
