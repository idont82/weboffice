package weboffice.doc;

/**
 * 가져온 그림 하나.
 *
 * <p>바이트는 그림 저장소(IMAGE_SEQ)에 두고 여기에는 자리와 크기만 담는다. 문단
 * 표에 그림을 담을 자리가 없기도 하고, 같은 그림이 여러 번 나오면 바이트를 그만큼
 * 다시 실어야 해서다.
 *
 * @param afterParagraphNo 이 문단 뒤에 놓인다. -1 이면 문서 맨 앞이다.
 * @param url 화면이 읽을 주소({@code /api/images/123}).
 * @param width 그린 크기(px).
 * @param height 그린 크기(px).
 * @param floating 글 흐름 밖에 떠 있는 그림인지. 표지 배경이 그렇다.
 * @param behind 글 뒤에 깔리는지.
 * @param left 떠 있는 그림의 가로 자리(px).
 * @param top 떠 있는 그림의 세로 자리(px).
 * @param fromH 가로 자리를 어디서부터 재는지. 0 쪽 · 1 단 · 2 글자 · 3 여백.
 * @param fromV 세로 자리를 어디서부터 재는지. 0 쪽 · 1 문단 · 2 줄 · 3 여백.
 */
public record DocImageDto(int afterParagraphNo, String url, double width, double height,
                          boolean floating, boolean behind, double left, double top,
                          int fromH, int fromV) {

    /** 글 흐름에 든 보통 그림. */
    public DocImageDto(int afterParagraphNo, String url, double width, double height) {
        this(afterParagraphNo, url, width, height, false, false, 0, 0, 0, 1);
    }
}
