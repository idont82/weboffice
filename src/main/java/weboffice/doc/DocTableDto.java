package weboffice.doc;

import java.util.List;

/**
 * 가져온 표 하나.
 *
 * <p>어느 문단 뒤에 놓이는지와 칸 글자, 칸 너비, 칸 바탕색, 칸 합치기를 담는다.
 * 테두리는 담지 않는다 - 문서 형식마다 셈이 다르고, 반만 옮긴 서식은 안 옮긴
 * 것보다 더 어긋나 보인다.
 *
 * <p>합치기는 화면(Univer)이 쓰는 규칙 그대로다: 주인 칸은 차지하는 칸 수(2 이상),
 * 합치기에 가려진 칸은 0, 보통 칸은 1 이다. 가려진 칸도 자리는 지킨다 - 자리를
 * 빼면 뒤 칸이 앞으로 밀려 표가 어긋난다.
 *
 * @param afterParagraphNo 이 문단 뒤에 놓인다. -1 이면 문서 맨 앞이다.
 * @param columnWidths 칸 너비(px). 비어 있으면 화면이 고르게 나눈다.
 * @param cellShades 칸 바탕색({@code #rrggbb}). 색이 없는 칸은 null 이다.
 * @param columnSpans 가로로 차지하는 칸 수.
 * @param rowSpans 세로로 차지하는 줄 수.
 * @param hasBorders 선이 그어진 표인지. 줄 세우기 용도로 쓴 테두리 없는 표를
 *                   격자로 그리면 원본과 딴판이 된다.
 * @param cellRuns 칸 글자 서식. 칸 글자 안 자리로 적는다. 서식이 없으면 null 이다.
 * @param align 표가 앉는 자리. 1 왼쪽 · 2 가운데 · 3 오른쪽. 문단 정렬과 같은 셈이다.
 * @param rowHeights 못박은 줄 높이(px). 안 적힌 줄은 null 이다 - 글에 맞춰 늘어난다.
 * @param cellAligns 칸 글의 가로 맞춤. 1 왼쪽 · 2 가운데 · 3 오른쪽. 안 적혔으면 1.
 * @param cellSpaces 칸 안 문단 사이 간격(px). 안 적혔으면 0 이다 - 이것을 흘리면
 *                   박스가 원본보다 낮아져 뒷글이 앞 쪽으로 올라온다.
 * @param opensNewPage 이 표가 새 쪽을 여는지. 표지 다음 목차가 표로 시작하는 문서가
 *                     있는데, 이것을 흘리면 목차 표만 표지 쪽에 남는다.
 * @param cellMargin 칸 안쪽 여백(px). 없으면 화면 기본을 쓴다 - 원본보다 넉넉해
 *                   표가 높아진다.
 * @param exactRowHeights 줄 높이를 못박은 것인지. 한글 문서는 "이만큼 찼다" 는 뜻이라
 *                         못박으면 글이 잘린다.
 * @param borders 그은 변만 담은 JSON({@code {"bottom":{"color":"#fc5e20","size":2.25}}}).
 *                네 변 가운데 아래만 긋는 제목 표가 흔한데, "선이 있다" 로만 담으면
 *                격자로 그려져 원본과 딴판이 된다(2026-08-26 지적).
 */
public record DocTableDto(int afterParagraphNo, List<List<String>> cells,
                          List<Double> columnWidths, List<List<String>> cellShades,
                          List<List<Integer>> columnSpans, List<List<Integer>> rowSpans,
                          boolean hasBorders, List<List<String>> cellRuns,
                          int align, List<Double> rowHeights,
                          List<List<Integer>> cellAligns, List<List<Double>> cellSpaces,
                          boolean opensNewPage, String borders,
                          boolean exactRowHeights, CellMargin cellMargin) {

    /** 칸 안쪽 여백(px). */
    public record CellMargin(double left, double right, double top, double bottom) {
    }

    public DocTableDto(int afterParagraphNo, List<List<String>> cells,
                       List<Double> columnWidths, List<List<String>> cellShades,
                       List<List<Integer>> columnSpans, List<List<Integer>> rowSpans,
                       boolean hasBorders, List<List<String>> cellRuns,
                       int align, List<Double> rowHeights,
                       List<List<Integer>> cellAligns, List<List<Double>> cellSpaces,
                       boolean opensNewPage, String borders, boolean exactRowHeights) {
        this(afterParagraphNo, cells, columnWidths, cellShades, columnSpans, rowSpans,
                hasBorders, cellRuns, align, rowHeights, cellAligns, cellSpaces,
                opensNewPage, borders, exactRowHeights, null);
    }

    public DocTableDto(int afterParagraphNo, List<List<String>> cells,
                       List<Double> columnWidths, List<List<String>> cellShades,
                       List<List<Integer>> columnSpans, List<List<Integer>> rowSpans,
                       boolean hasBorders, List<List<String>> cellRuns,
                       int align, List<Double> rowHeights,
                       List<List<Integer>> cellAligns, List<List<Double>> cellSpaces,
                       boolean opensNewPage, String borders) {
        this(afterParagraphNo, cells, columnWidths, cellShades, columnSpans, rowSpans,
                hasBorders, cellRuns, align, rowHeights, cellAligns, cellSpaces,
                opensNewPage, borders, true);
    }

    public DocTableDto(int afterParagraphNo, List<List<String>> cells,
                       List<Double> columnWidths, List<List<String>> cellShades,
                       List<List<Integer>> columnSpans, List<List<Integer>> rowSpans,
                       boolean hasBorders, List<List<String>> cellRuns,
                       int align, List<Double> rowHeights,
                       List<List<Integer>> cellAligns, List<List<Double>> cellSpaces,
                       boolean opensNewPage) {
        this(afterParagraphNo, cells, columnWidths, cellShades, columnSpans, rowSpans,
                hasBorders, cellRuns, align, rowHeights, cellAligns, cellSpaces,
                opensNewPage, null, true);
    }

    public DocTableDto(int afterParagraphNo, List<List<String>> cells,
                       List<Double> columnWidths, List<List<String>> cellShades,
                       List<List<Integer>> columnSpans, List<List<Integer>> rowSpans,
                       boolean hasBorders, List<List<String>> cellRuns,
                       int align, List<Double> rowHeights,
                       List<List<Integer>> cellAligns, List<List<Double>> cellSpaces) {
        this(afterParagraphNo, cells, columnWidths, cellShades, columnSpans, rowSpans,
                hasBorders, cellRuns, align, rowHeights, cellAligns, cellSpaces, false, null,
                true);
    }

    public DocTableDto(int afterParagraphNo, List<List<String>> cells,
                       List<Double> columnWidths, List<List<String>> cellShades,
                       List<List<Integer>> columnSpans, List<List<Integer>> rowSpans,
                       boolean hasBorders, List<List<String>> cellRuns,
                       int align, List<Double> rowHeights,
                       List<List<Integer>> cellAligns) {
        this(afterParagraphNo, cells, columnWidths, cellShades, columnSpans, rowSpans,
                hasBorders, cellRuns, align, rowHeights, cellAligns, List.of(), false, null,
                true);
    }

    public DocTableDto(int afterParagraphNo, List<List<String>> cells,
                       List<Double> columnWidths, List<List<String>> cellShades,
                       List<List<Integer>> columnSpans, List<List<Integer>> rowSpans,
                       boolean hasBorders, List<List<String>> cellRuns,
                       int align, List<Double> rowHeights) {
        this(afterParagraphNo, cells, columnWidths, cellShades, columnSpans, rowSpans,
                hasBorders, cellRuns, align, rowHeights, List.of(), List.of(), false, null,
                true);
    }

    /** 정렬을 안 적은 표는 왼쪽이다. 워드 기본이다. */
    public static final int ALIGN_LEFT = 1;
    public static final int ALIGN_CENTER = 2;
    public static final int ALIGN_RIGHT = 3;

    public DocTableDto(int afterParagraphNo, List<List<String>> cells,
                       List<Double> columnWidths, List<List<String>> cellShades,
                       List<List<Integer>> columnSpans, List<List<Integer>> rowSpans,
                       boolean hasBorders, List<List<String>> cellRuns) {
        this(afterParagraphNo, cells, columnWidths, cellShades, columnSpans, rowSpans,
                hasBorders, cellRuns, ALIGN_LEFT, List.of());
    }

    public DocTableDto(int afterParagraphNo, List<List<String>> cells) {
        this(afterParagraphNo, cells, List.of(), List.of(), List.of(), List.of(), true,
                List.of());
    }

    public DocTableDto(int afterParagraphNo, List<List<String>> cells,
                       List<Double> columnWidths) {
        this(afterParagraphNo, cells, columnWidths, List.of(), List.of(), List.of(), true,
                List.of());
    }

    public DocTableDto(int afterParagraphNo, List<List<String>> cells,
                       List<Double> columnWidths, List<List<String>> cellShades,
                       List<List<Integer>> columnSpans, List<List<Integer>> rowSpans) {
        this(afterParagraphNo, cells, columnWidths, cellShades, columnSpans, rowSpans, true,
                List.of());
    }

    public DocTableDto(int afterParagraphNo, List<List<String>> cells,
                       List<Double> columnWidths, List<List<String>> cellShades,
                       List<List<Integer>> columnSpans, List<List<Integer>> rowSpans,
                       boolean hasBorders) {
        this(afterParagraphNo, cells, columnWidths, cellShades, columnSpans, rowSpans,
                hasBorders, List.of());
    }
}
