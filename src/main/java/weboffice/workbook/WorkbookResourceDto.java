package weboffice.workbook;

/**
 * 문서 리소스 한 덩어리.
 *
 * <p>{@code name} 은 Univer 가 정한 이름이다(예: {@code SHEET_FILTER_PLUGIN}).
 * {@code data} 는 그 플러그인이 통째로 넘기는 JSON 문자열이다 - 우리가 안을 들여다
 * 보지 않는다. 모양을 우리가 정하지 않았으므로 해석하려 들면 판올림마다 깨진다.
 */
public record WorkbookResourceDto(String name, String data) {
}
