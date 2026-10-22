package weboffice.slide;

import java.util.List;

/**
 * 가져온 프레젠테이션 한 벌.
 *
 * <p>고쳐 쓰기는 하지 않는다. 무엇이 적혀 있는지 보여 주기만 한다(2026-08-13 결정:
 * 슬라이드 편집기는 쓸 만한 오픈소스가 없다. 뷰어·추출만 한다).
 *
 * <p>길이는 모두 픽셀이다. pptx 는 포인트로 적고 화면은 픽셀로 그린다.
 *
 * @param width 슬라이드 한 장의 가로(px).
 * @param height 슬라이드 한 장의 세로(px).
 */
public record SlideDeck(double width, double height, List<Slide> slides,
                        int firstSlideNo) {

    /**
     * 첫 장에 매길 쪽 번호를 안 적었을 때. 파워포인트 기본이다.
     */
    public static final int FIRST_SLIDE_NO = 1;

    public SlideDeck(double width, double height, List<Slide> slides) {
        this(width, height, slides, FIRST_SLIDE_NO);
    }

    /**
     * 슬라이드 한 장.
     *
     * @param slideNo 0부터 세는 장 번호.
     * @param notes 발표자 노트. 없으면 null.
     * @param backgroundImage 바탕에 깐 그림. 없으면 null.
     */
    public record Slide(int slideNo, List<SlideText> texts, List<SlideTable> tables,
                        List<SlidePicture> pictures, String notes,
                        List<SlideShape> shapes, String background,
                        byte[] backgroundImage) {

        public Slide(int slideNo, List<SlideText> texts, List<SlideTable> tables,
                     List<SlidePicture> pictures, String notes,
                     List<SlideShape> shapes, String background) {
            this(slideNo, texts, tables, pictures, notes, shapes, background, null);
        }

        public Slide(int slideNo, List<SlideText> texts, List<SlideTable> tables,
                     List<SlidePicture> pictures, String notes) {
            this(slideNo, texts, tables, pictures, notes, List.of(), null);
        }
    }

    /**
     * 색을 칠한 도형 하나.
     *
     * <p>발표 자료는 색 띠와 사각형이 화면 대부분이다. 글자만 읽으면 흰 종이에
     * 검은 글씨만 남아 원본과 딴판이 된다(2026-08-21 지적).
     *
     * @param fill 채운 색. 안 칠했으면 null.
     * @param line 테두리 색. 없으면 null.
     * @param lineWidth 테두리 굵기(px).
     * @param shape 도형 모양. 화면이 아는 이름만 온다 - 모르는 모양은 네모로 본다.
     * @param rotation 시계 방향으로 돌린 각도. 안 돌렸으면 0 이다.
     * @param gradient 번지는 색. 단색이면 빈 목록이다.
     * @param gradientAngle 색이 번지는 방향(도). 0 이 위에서 아래다.
     * @param image 도형에 깔아 둔 그림. 없으면 null 이다.
     * @param corner 둥근 네모의 모서리. 짧은 변의 비율이다(0.15 면 15%).
     */
    public record SlideShape(double left, double top, double width, double height,
                             String fill, String line, double lineWidth, String shape,
                             double rotation, List<ColorStop> gradient,
                             double gradientAngle, byte[] image, double corner,
                             List<Point> outline) {

        public SlideShape(double left, double top, double width, double height,
                          String fill, String line, double lineWidth, String shape,
                          double rotation, List<ColorStop> gradient,
                          double gradientAngle, byte[] image, double corner) {
            this(left, top, width, height, fill, line, lineWidth, shape, rotation,
                    gradient, gradientAngle, image, corner, List.of());
        }

        public SlideShape(double left, double top, double width, double height,
                          String fill, String line, double lineWidth, String shape,
                          double rotation) {
            this(left, top, width, height, fill, line, lineWidth, shape, rotation,
                    List.of(), 0, null, 0);
        }
    }

    /**
     * 손으로 그린 도형의 꼭짓점 하나.
     *
     * <p>도형 크기를 1로 본 비율이다. 크기가 바뀌어도 모양이 같이 늘어난다.
     */
    public record Point(double x, double y) {
    }

    /**
     * 번지는 색의 한 자리.
     *
     * @param at 0(시작)에서 1(끝) 사이 자리.
     */
    public record ColorStop(String color, double at) {
    }

    /**
     * 글상자 하나.
     *
     * @param fontSize 글자 크기(pt). 적혀 있지 않으면 0. 첫 줄 크기다.
     * @param lines 줄마다의 크기·굵기·색. 한 상자 안에서 갈리는 경우를 담는다.
     * @param shrinkToFit 넘치면 글자를 줄여 상자에 맞추라고 적혀 있는가.
     * @param font 글꼴 이름. 안 적혀 있으면 null.
     * @param anchor 상자 안 세로 맞춤({@code center}·{@code bottom}). 위쪽이면 null.
     * @param rotation 상자를 돌려 놓은 각도(도).
     * @param align 가로 맞춤({@code center}·{@code right}). 왼쪽이면 null.
     * @param padding 상자 안쪽 여백(px).
     */
    public record SlideText(String text, double left, double top, double width,
                            double height, double fontSize, boolean bold, String color,
                            List<SlideLine> lines, boolean shrinkToFit, String font,
                            String anchor, double rotation, String align,
                            Padding padding) {

        public SlideText(String text, double left, double top, double width,
                         double height, double fontSize, boolean bold, String color,
                         List<SlideLine> lines, boolean shrinkToFit, String font,
                         String anchor, double rotation, String align) {
            this(text, left, top, width, height, fontSize, bold, color, lines,
                    shrinkToFit, font, anchor, rotation, align, Padding.NONE);
        }

        public SlideText(String text, double left, double top, double width,
                         double height, double fontSize, boolean bold, String color,
                         List<SlideLine> lines, boolean shrinkToFit, String font,
                         String anchor, double rotation) {
            this(text, left, top, width, height, fontSize, bold, color, lines,
                    shrinkToFit, font, anchor, rotation, null);
        }

        public SlideText(String text, double left, double top, double width,
                         double height, double fontSize, boolean bold, String color,
                         List<SlideLine> lines, boolean shrinkToFit, String font,
                         String anchor) {
            this(text, left, top, width, height, fontSize, bold, color, lines,
                    shrinkToFit, font, anchor, 0);
        }

        public SlideText(String text, double left, double top, double width,
                         double height, double fontSize, boolean bold, String color,
                         List<SlideLine> lines, boolean shrinkToFit, String font) {
            this(text, left, top, width, height, fontSize, bold, color, lines,
                    shrinkToFit, font, null);
        }

        public SlideText(String text, double left, double top, double width,
                         double height, double fontSize, boolean bold, String color,
                         List<SlideLine> lines, boolean shrinkToFit) {
            this(text, left, top, width, height, fontSize, bold, color, lines,
                    shrinkToFit, null);
        }

        public SlideText(String text, double left, double top, double width,
                         double height, double fontSize, boolean bold, String color,
                         List<SlideLine> lines) {
            this(text, left, top, width, height, fontSize, bold, color, lines, false);
        }

        public SlideText(String text, double left, double top, double width,
                         double height, double fontSize, boolean bold, String color) {
            this(text, left, top, width, height, fontSize, bold, color, List.of());
        }

        public SlideText(String text, double left, double top, double width,
                         double height, double fontSize, boolean bold) {
            this(text, left, top, width, height, fontSize, bold, null);
        }
    }

    /**
     * 상자 안쪽 여백(px).
     *
     * <p>파워포인트 글상자는 안쪽에 여백이 있다. 흘리면 글이 테두리에 붙는다.
     */
    public record Padding(double left, double top, double right, double bottom) {

        /** 여백 없음. */
        public static final Padding NONE = new Padding(0, 0, 0, 0);
    }

    /**
     * 글상자 안의 한 줄.
     *
     * @param fontSize 글자 크기(pt). 적혀 있지 않으면 0.
     * @param bullet 글머리 기호. 없으면 null.
     * @param indent 왼쪽 들여쓰기(px).
     * @param hanging 기호를 내어 쓰는 너비(px).
     * @param align 가로 맞춤({@code center}·{@code right}). 왼쪽이면 null.
     */
    public record SlideLine(String text, double fontSize, boolean bold, String color,
                            String font, String bullet, double indent, double hanging,
                            String align) {

        public SlideLine(String text, double fontSize, boolean bold, String color,
                         String font, String bullet, double indent, double hanging) {
            this(text, fontSize, bold, color, font, bullet, indent, hanging, null);
        }

        public SlideLine(String text, double fontSize, boolean bold, String color,
                         String font) {
            this(text, fontSize, bold, color, font, null, 0, 0);
        }

        public SlideLine(String text, double fontSize, boolean bold, String color) {
            this(text, fontSize, bold, color, null);
        }
    }

    /**
     * 표 하나.
     *
     * @param cells 줄마다의 칸. 칸에 글과 그 칸의 꾸밈이 함께 들어 있다.
     */
    public record SlideTable(List<List<SlideCell>> cells, double left, double top,
                             double width, double height) {
    }

    /**
     * 표의 칸 하나.
     *
     * <p>바탕색·글자 색·정렬·크기를 칸마다 따로 들고 있다. 표 하나에 꾸밈 격자를
     * 여러 벌 두면 줄·칸 번호가 어긋날 때 찾기가 어렵다(2026-08-21 지적).
     *
     * @param fill 바탕색. 안 칠했으면 null.
     * @param color 글자 색. 안 적혀 있으면 null.
     * @param align 가로 맞춤({@code center}·{@code right}). 왼쪽이면 null.
     * @param fontSize 글자 크기(pt). 안 적혀 있으면 0.
     * @param font 글꼴 이름. 안 적혀 있으면 null.
     */
    public record SlideCell(String text, String fill, String color, String align,
                            double fontSize, boolean bold, String font) {

        public SlideCell(String text) {
            this(text, null, null, null, 0, false, null);
        }
    }

    /** 그림 하나. 알맹이는 읽는 쪽이 그림 저장소에 넣는다. */
    public record SlidePicture(byte[] bytes, double left, double top, double width,
                               double height) {
    }
}
