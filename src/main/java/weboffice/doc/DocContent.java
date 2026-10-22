package weboffice.doc;

import java.util.List;

/**
 * 가져온 문서 한 벌.
 *
 * <p>문단과 표와 그림을 따로 담는다. 표 칸 글자를 문단으로 펼치면 세로로 길게
 * 늘어서 원본과 너무 달라 보였다(2026-08-12 지적: 워드에서 표였던 것이 줄 목록이
 * 됐다). 그림도 문단 표에는 담을 자리가 없다.
 *
 * <p>그림 바이트는 여기 없다. 읽는 쪽이 그림 저장소에 넣고 주소만 담는다.
 */
public record DocContent(List<DocParagraphDto> paragraphs, List<DocTableDto> tables,
                         List<DocPicture> pictures, DocPage page,
                         DocEdge header, DocEdge footer,
                         boolean titlePage, DocEdge firstHeader, DocEdge firstFooter,
                         DocDefaults defaults) {

    public DocContent(List<DocParagraphDto> paragraphs, List<DocTableDto> tables,
                      List<DocPicture> pictures, DocPage page,
                      DocEdge header, DocEdge footer,
                      boolean titlePage, DocEdge firstHeader, DocEdge firstFooter) {
        this(paragraphs, tables, pictures, page, header, footer, titlePage,
                firstHeader, firstFooter, null);
    }

    /**
     * 문서 기본 서식.
     *
     * <p>워드는 문단이 안 적은 것을 여기서 가져다 쓴다. 내보낸 문서에 이것이 없으면
     * 워드가 제 기본 글꼴로 그린다 - 맑은 고딕은 행간이 커서 9pt 목차 줄이 16.8pt
     * 가 됐다(2026-08-24 실측: 원본은 11.7pt).
     *
     * @param font 기본 글꼴(로마자).
     * @param eastAsiaFont 기본 글꼴(동아시아). 없으면 null.
     * @param fontSize 기본 글자 크기(pt).
     * @param lineSpacing 기본 줄 간격 배수. 없으면 null.
     * @param spaceAfter 기본 문단 뒤 간격(pt). 없으면 null.
     * @param linePitch 줄 격자 간격(트윕). 없으면 0.
     */
    public record DocDefaults(String font, String eastAsiaFont, double fontSize,
                              Double lineSpacing, Double spaceAfter, int linePitch) {
    }

    public DocContent(List<DocParagraphDto> paragraphs, List<DocTableDto> tables) {
        this(paragraphs, tables, List.of(), null, null, null, false, null, null);
    }

    public DocContent(List<DocParagraphDto> paragraphs, List<DocTableDto> tables,
                      List<DocPicture> pictures) {
        this(paragraphs, tables, pictures, null, null, null, false, null, null);
    }

    public DocContent(List<DocParagraphDto> paragraphs, List<DocTableDto> tables,
                      List<DocPicture> pictures, DocPage page) {
        this(paragraphs, tables, pictures, page, null, null, false, null, null);
    }

    public DocContent(List<DocParagraphDto> paragraphs, List<DocTableDto> tables,
                      List<DocPicture> pictures, DocPage page,
                      DocEdge header, DocEdge footer) {
        this(paragraphs, tables, pictures, page, header, footer, false, null, null);
    }

    public static DocContent of(List<DocParagraphDto> paragraphs) {
        return new DocContent(paragraphs, List.of(), List.of(), null, null, null,
                false, null, null);
    }

    /**
     * 쪽마다 되풀이되는 글. 머릿글이나 꼬릿글이다.
     *
     * <p>본문과 같은 모양(문단·표·그림)이다. 워드 머릿글에 표를 넣는 문서가 흔하다 -
     * 이 문서의 머릿글도 표 하나이고 그 안에 회사 로고가 있다(2026-08-13 실측).
     */
    public record DocEdge(List<DocParagraphDto> paragraphs, List<DocTableDto> tables,
                          List<DocPicture> pictures) {

        public DocEdge(List<DocParagraphDto> paragraphs, List<DocTableDto> tables) {
            this(paragraphs, tables, List.of());
        }
    }

    /**
     * 종이 크기와 여백(px).
     *
     * <p>여백이 다르면 본문 폭이 달라져 표와 그림이 원본과 다른 자리에 앉는다
     * (2026-08-13 지적: 표 크기가 다르다).
     */
    public record DocPage(double width, double height, double marginLeft,
                          double marginRight, double marginTop, double marginBottom,
                          double marginHeader, double marginFooter) {

        /** 머릿글 여백을 아직 몰랐을 적에 저장한 것. 워드 기본값 720twip(48px)을 쓴다. */
        public DocPage(double width, double height, double marginLeft,
                       double marginRight, double marginTop, double marginBottom) {
            this(width, height, marginLeft, marginRight, marginTop, marginBottom, 48, 48);
        }
    }

    /**
     * 아직 저장소에 넣기 전 그림.
     *
     * @param afterParagraphNo 이 문단 뒤에 놓인다. -1 이면 문서 맨 앞이다.
     * @param bytes 그림 알맹이.
     * @param width 그린 크기(px).
     * @param height 그린 크기(px).
     * @param floating 글 흐름 밖에 떠 있는 그림인지. 표지 배경이 그렇다 - 흐름에
     *                 넣으면 첫 쪽을 통째로 차지한다(2026-08-25 지적).
     * @param behind 글 뒤에 깔리는지.
     * @param left 떠 있는 그림의 가로 자리(px). 쪽 왼쪽 끝에서 잰다.
     * @param top 떠 있는 그림의 세로 자리(px).
     * @param fromH 가로 자리를 어디서부터 재는지. 0 쪽 · 1 단 · 2 글자 · 3 여백.
     * @param fromV 세로 자리를 어디서부터 재는지. 0 쪽 · 1 문단 · 2 줄 · 3 여백.
     */
    public record DocPicture(int afterParagraphNo, byte[] bytes, double width, double height,
                             boolean floating, boolean behind, double left, double top,
                             int fromH, int fromV) {

        public DocPicture(int afterParagraphNo, byte[] bytes, double width, double height,
                          boolean floating, boolean behind, double left, double top) {
            this(afterParagraphNo, bytes, width, height, floating, behind, left, top, 0, 1);
        }

        /** 글 흐름에 든 보통 그림. */
        public DocPicture(int afterParagraphNo, byte[] bytes, double width, double height) {
            this(afterParagraphNo, bytes, width, height, false, false, 0, 0, 0, 1);
        }
    }
}
