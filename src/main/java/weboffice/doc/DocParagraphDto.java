package weboffice.doc;

/**
 * 글 문서의 문단 한 줄.
 *
 * <p>{@code style} 과 {@code runs} 는 Univer 모양 그대로의 JSON 문자열이다. 서버는
 * 안을 들여다보지 않고 왕복만 시킨다 - 서식 종류가 늘어도 서버를 고칠 일이 없다.
 * 셀의 {@code styleValue} 와 같은 판단이다.
 */
public record DocParagraphDto(int paragraphNo, String text, String style, String runs) {
}
