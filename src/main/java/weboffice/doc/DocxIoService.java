package weboffice.doc;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * docx 와 문단 사이 옮기기.
 *
 * <p>서버는 서식을 해석하지 않는 것이 원칙이다(셀의 {@code styleValue} 가 그렇다).
 * docx 만은 예외다 - 워드 파일 형식이 우리 모양을 모르니 누군가는 옮겨야 한다.
 * 그 자리를 여기 하나로 모아 둔다.
 *
 * <p>담는 것은 글자와 굵게·기울임·밑줄·취소선, 그리고 문단 정렬이다. 더 담을 수도
 * 있지만 옮기지 못하는 것을 옮긴 척하면 열어 본 사람이 파일이 깨진 줄 안다 - 늘리는
 * 것은 실제로 왕복을 확인한 뒤에 한다.
 */
@Service
public class DocxIoService {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** 워드가 쓰는 twip 을 px 로. twip 은 1/20 pt 이고 화면은 96dpi 다. */
    private static final org.slf4j.Logger logger =
            org.slf4j.LoggerFactory.getLogger(DocxIoService.class);

    /** 본문 폭을 이만큼까지 넘치는 그림은 그대로 둔다. */
    private static final double OVERFLOW_ALLOW = 1.1;

    /** 용지를 모를 때 보는 본문 폭(px). A4 에 워드 기본 여백이다. */
    private static final double DEFAULT_BODY_WIDTH = 794 - 96 - 96;

    /** 목록 단 수. 워드 기본과 같다. */
    private static final int LIST_LEVELS = 9;

    /** 만들어 준 목록 번호. 한 파일 안에서 겹치지 않게만 하면 된다. */
    private static final java.util.concurrent.atomic.AtomicInteger NEXT_LIST =
            new java.util.concurrent.atomic.AtomicInteger(1000);

    private static final double TWIP_PER_PX = 20d * 72d / 96d;

    /** twip 을 pt 로. twip 은 1/20 pt 다. */
    private static final double TWIP_PER_PT = 20d;

    /**
     * pt 를 px 로. 화면은 96dpi 이고 pt 는 1/72 인치다.
     *
     * <p>문단 간격도 이것으로 고쳐 적는다. 화면은 단위 없는 값을 픽셀로 읽는다 -
     * 포인트를 그대로 적어 문단마다 2.7px 씩 짧았고, 그만큼 쪽마다 한 줄씩 더
     * 들어가 다음 쪽에 있어야 할 제목이 앞 쪽 밑에 붙었다(2026-08-19 지적).
     */
    private static final double PX_PER_PT = 96d / 72d;

    /**
     * Univer 의 줄 간격 규칙. 1 이 "적어도 이만큼" 이다.
     *
     * <p>0("알아서")을 쓰면 화면이 "몇 줄" 을 글자 상자 높이로 잰다. Calibri 는
     * 그 상자가 글자 크기의 1.23배라 워드보다 줄마다 20% 넘게 높아졌다 - 쪽마다
     * 서너 줄씩 덜 들어가 쪽 나눔이 어긋났다(2026-08-19 지적). 워드처럼 글자
     * 크기로 재어 픽셀로 적고, 글자가 크면 그만큼 늘어나게 둔다.
     */
    private static final int SPACING_AT_LEAST = 1;

    /**
     * "정확히 이 높이". 워드가 적은 줄 높이를 그대로 쓴다.
     *
     * <p>화면은 이 규칙도 "적어도" 처럼 다뤄 글자 상자보다 작게 못 준다 - 그래서
     * 빌드할 때 고쳐 두었다(frontend 의 exactLine.ts).
     */
    private static final int SPACING_EXACT = 2;

    /**
     * 워드 파일이 크기를 안 적었을 때 쓰는 크기(pt).
     *
     * <p>파일 규칙에서 크기를 안 적으면 20 half-point, 곧 10pt 다. 워드가 새
     * 문서를 11pt 로 만드는 것은 기본 서식(Normal)에 22 를 적기 때문이지 규칙이
     * 그런 것이 아니다. 11 로 보면 크기를 안 적은 문서가 원본보다 커진다
     * (2026-08-26 지적).
     */
    private static final double DEFAULT_FONT_SIZE = 10d;

    /**
     * 글꼴 한 줄이 글자 크기의 몇 배인가.
     *
     * <p>워드가 "여러 줄" 로 적은 줄 간격은 글자 크기가 아니라 글꼴이 요구하는 줄
     * 높이에 곱해진다. 그 줄 높이는 글꼴마다 다르고(대개 글자 크기의 1.15~1.35배)
     * 우리는 서버에서 글꼴 파일을 열지 않는다. 흔한 본문 글꼴들의 가운데 값을
     * 쓴다 - 이것을 빼면 쪽마다 몇 줄씩 더 들어가 원본과 쪽 나눔이 어긋난다
     * (2026-08-21 지적: 목차가 한 쪽을 못 채웠다).
     */
    private static final double FONT_LINE_HEIGHT = 1.1d;

    /** Univer 의 가로 맞춤 값. 이름이 없어 숫자를 그대로 쓴다. */
    private static final int ALIGN_LEFT = 1;
    private static final int ALIGN_CENTER = 2;
    private static final int ALIGN_RIGHT = 3;
    private static final int ALIGN_JUSTIFY = 4;

    /** 문단을 docx 로. */
    /**
     * 내보낼 그림 하나.
     *
     * <p>{@link DocImageDto} 와 같되 주소 대신 알맹이를 든다 - 내려받을 파일에는
     * 그림이 실려 있어야 한다.
     *
     * @param afterParagraphNo 이 문단 뒤에 놓인다. -1 이면 문서 맨 앞이다.
     * @param bytes 그림 알맹이.
     * @param width 그린 크기(px).
     * @param height 그린 크기(px).
     */
    public record Drawing(int afterParagraphNo, byte[] bytes, double width, double height,
                          boolean floating, boolean behind, double left, double top) {

        /** 글 흐름에 든 보통 그림. */
        public Drawing(int afterParagraphNo, byte[] bytes, double width, double height) {
            this(afterParagraphNo, bytes, width, height, false, false, 0, 0);
        }
    }

    public byte[] toDocx(List<DocParagraphDto> rows) throws IOException {
        return toDocx(rows, List.of(), List.of());
    }

    /**
     * 문단·표·그림을 docx 로.
     *
     * <p>표와 그림은 "몇 번째 문단 뒤" 로 자리를 안다. 문단만 싣던 때에는 표와
     * 그림이 통째로 사라졌다(2026-08-24 지적).
     */
    public byte[] toDocx(List<DocParagraphDto> rows, List<DocTableDto> tables,
                         List<Drawing> pictures) throws IOException {
        return toDocx(rows, tables, pictures, null, null, null);
    }

    /**
     * 문단·표·그림에 용지와 머릿글·꼬릿글까지.
     *
     * <p>용지를 흘리면 A4 원본이 워드 기본(레터)으로 나오고, 머릿글·꼬릿글을 흘리면
     * 쪽마다 되풀이되던 글이 사라진다(2026-08-24 지적).
     */
    public byte[] toDocx(List<DocParagraphDto> rows, List<DocTableDto> tables,
                         List<Drawing> pictures, DocContent.DocPage page,
                         DocContent.DocEdge header, DocContent.DocEdge footer)
            throws IOException {
        return toDocx(rows, tables, pictures, page, header, footer, null);
    }

    /**
     * 문서 기본 서식까지 실어 docx 로.
     *
     * <p>기본 서식이 없으면 워드가 제 기본 글꼴로 그린다 - 행간이 달라 쪽 수가
     * 어긋난다(2026-08-24 실측).
     */
    public byte[] toDocx(List<DocParagraphDto> rows, List<DocTableDto> tables,
                         List<Drawing> pictures, DocContent.DocPage page,
                         DocContent.DocEdge header, DocContent.DocEdge footer,
                         DocContent.DocDefaults defaults)
            throws IOException {
        return toDocx(rows, tables, pictures, page, header, footer, defaults,
                false, null, null);
    }

    /**
     * 첫 쪽만 다른 머릿글·꼬릿글까지.
     *
     * <p>표지에는 머릿글을 안 찍고 둘째 쪽부터 찍는 문서가 흔하다(titlePg). 그것을
     * 흘리면 둘째 쪽부터 머릿글이 통째로 사라진다(2026-08-25 실측).
     */
    public byte[] toDocx(List<DocParagraphDto> rows, List<DocTableDto> tables,
                         List<Drawing> pictures, DocContent.DocPage page,
                         DocContent.DocEdge header, DocContent.DocEdge footer,
                         DocContent.DocDefaults defaults, boolean titlePage,
                         DocContent.DocEdge firstHeader, DocContent.DocEdge firstFooter)
            throws IOException {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            // 목록 이름 하나에 워드 번호 하나. 같은 이름을 쓰는 문단끼리 한 목록이 된다.
            java.util.Map<String, java.math.BigInteger> lists = new java.util.HashMap<>();
            writeDefaults(document, defaults);
            writePage(document, page);
            writeGrid(document, defaults);
            writeEdges(document, header, footer, titlePage, firstHeader, firstFooter);
            double room = bodyWidthOf(page);
            writeAfter(document, null, -1, tables, pictures, room);
            XWPFParagraph previous = null;
            for (DocParagraphDto row : rows) {
                // 앞 줄에서 줄만 바꾼 줄은 새 문단을 만들지 않고 그 문단에 잇는다.
                if (previous != null && joinsPreviousLine(row.style())) {
                    previous.createRun().addBreak();
                    writeRuns(previous, row);
                    writeAfter(document, previous, row.paragraphNo(), tables, pictures, room);
                    continue;
                }
                XWPFParagraph paragraph = document.createParagraph();
                previous = paragraph;
                // 새 쪽을 여는 문단은 문단 속성으로 적는다. 쪽 나눔 글자를 앞에
                // 넣으면 그 문단이 앞 쪽 끝에 걸려 자리가 어긋난다(2026-08-25 실측).
                if (opensNewPage(row.style())) {
                    paragraph.setPageBreak(true);
                }
                applyAlignment(paragraph, row.style());
                applySpacing(paragraph, row);
                applyIndent(paragraph, row.style());
                applyBullet(document, paragraph, row.style(), lists);
                applyTab(paragraph, row.style());
                applyKeep(paragraph, row.style());
                applyMarkSize(paragraph, row.runs());
                writeRuns(paragraph, row);
                writeAfter(document, paragraph, row.paragraphNo(), tables, pictures, room);
            }

            // 마지막 문단보다 뒤에 놓인 표·그림도 쓴다. 안 쓰면 문서 끝의 표가
            // 통째로 빠진다(2026-08-25 실측: 연락처 표가 사라졌다).
            int lastNo = rows.isEmpty() ? -1 : rows.get(rows.size() - 1).paragraphNo();
            for (DocTableDto table : tables) {
                if (table.afterParagraphNo() > lastNo) {
                    writeTable(document, table);
                }
            }
            for (Drawing picture : pictures) {
                if (picture.afterParagraphNo() > lastNo) {
                    writePicture(document.createParagraph(), fitted(picture, room));
                }
            }
            document.write(out);
            return out.toByteArray();
        }
    }

    /** 문서 기본 서식(글꼴·크기·간격). 안 실으면 워드가 제 기본으로 그린다. */
    private void writeDefaults(XWPFDocument document, DocContent.DocDefaults defaults) {
        if (defaults == null) {
            return;
        }
        var styles = document.createStyles();
        var made = org.openxmlformats.schemas.wordprocessingml.x2006.main
                .CTStyles.Factory.newInstance();
        var basics = made.addNewDocDefaults();
        var run = basics.addNewRPrDefault().addNewRPr();
        if (defaults.font() != null || defaults.eastAsiaFont() != null) {
            var fonts = run.addNewRFonts();
            if (defaults.font() != null) {
                fonts.setAscii(defaults.font());
                fonts.setHAnsi(defaults.font());
            }
            if (defaults.eastAsiaFont() != null) {
                fonts.setEastAsia(defaults.eastAsiaFont());
            }
        }
        if (defaults.fontSize() > 0) {
            var half = java.math.BigInteger.valueOf(Math.round(defaults.fontSize() * 2));
            run.addNewSz().setVal(half);
            run.addNewSzCs().setVal(half);
        }
        if (defaults.lineSpacing() != null || defaults.spaceAfter() != null) {
            var spacing = basics.addNewPPrDefault().addNewPPr().addNewSpacing();
            if (defaults.lineSpacing() != null) {
                spacing.setLine(java.math.BigInteger.valueOf(
                        Math.round(defaults.lineSpacing() * 240)));
                spacing.setLineRule(org.openxmlformats.schemas.wordprocessingml.x2006.main
                        .STLineSpacingRule.AUTO);
            }
            if (defaults.spaceAfter() != null) {
                spacing.setAfter(java.math.BigInteger.valueOf(
                        Math.round(defaults.spaceAfter() * TWIP_PER_PT)));
            }
        }
        styles.setStyles(made);
    }

    /** 줄 격자. 워드는 이것으로 한글 줄 높이를 맞춘다. */
    private void writeGrid(XWPFDocument document, DocContent.DocDefaults defaults) {
        if (defaults == null || defaults.linePitch() <= 0) {
            return;
        }
        var body = document.getDocument().getBody();
        var section = body.isSetSectPr() ? body.getSectPr() : body.addNewSectPr();
        var grid = section.isSetDocGrid() ? section.getDocGrid() : section.addNewDocGrid();
        grid.setLinePitch(java.math.BigInteger.valueOf(defaults.linePitch()));
    }

    /** 용지 크기와 여백. 안 적혀 있으면 워드 기본을 그대로 둔다. */
    private void writePage(XWPFDocument document, DocContent.DocPage page) {
        if (page == null) {
            return;
        }
        var body = document.getDocument().getBody();
        var section = body.isSetSectPr() ? body.getSectPr() : body.addNewSectPr();
        if (page.width() > 0 && page.height() > 0) {
            var size = section.isSetPgSz() ? section.getPgSz() : section.addNewPgSz();
            size.setW(java.math.BigInteger.valueOf(twipOf(page.width())));
            size.setH(java.math.BigInteger.valueOf(twipOf(page.height())));
        }
        var margin = section.isSetPgMar() ? section.getPgMar() : section.addNewPgMar();
        margin.setLeft(java.math.BigInteger.valueOf(twipOf(page.marginLeft())));
        margin.setRight(java.math.BigInteger.valueOf(twipOf(page.marginRight())));
        margin.setTop(java.math.BigInteger.valueOf(twipOf(page.marginTop())));
        margin.setBottom(java.math.BigInteger.valueOf(twipOf(page.marginBottom())));
        // 머릿글·꼬릿글 여백을 안 적으면 워드가 0 으로 보고 머릿글이 본문을 밀어낸다.
        margin.setHeader(java.math.BigInteger.valueOf(twipOf(page.marginHeader())));
        margin.setFooter(java.math.BigInteger.valueOf(twipOf(page.marginFooter())));
    }

    /** 쪽마다 되풀이되는 글. 우리는 한 벌씩만 쓴다 - 읽을 때도 첫 벌만 본다. */
    private void writeEdges(XWPFDocument document, DocContent.DocEdge header,
                            DocContent.DocEdge footer, boolean titlePage,
                            DocContent.DocEdge firstHeader, DocContent.DocEdge firstFooter) {
        if (header == null && footer == null && firstHeader == null && firstFooter == null) {
            return;
        }
        var policy = new org.apache.poi.xwpf.model.XWPFHeaderFooterPolicy(document);
        if (header != null) {
            writeEdge(policy.createHeader(
                    org.apache.poi.xwpf.model.XWPFHeaderFooterPolicy.DEFAULT), header);
        }
        if (footer != null) {
            writeEdge(policy.createFooter(
                    org.apache.poi.xwpf.model.XWPFHeaderFooterPolicy.DEFAULT), footer);
        }
        if (firstHeader != null) {
            writeEdge(policy.createHeader(
                    org.apache.poi.xwpf.model.XWPFHeaderFooterPolicy.FIRST), firstHeader);
        }
        if (firstFooter != null) {
            writeEdge(policy.createFooter(
                    org.apache.poi.xwpf.model.XWPFHeaderFooterPolicy.FIRST), firstFooter);
        }
        if (titlePage) {
            // 첫 쪽만 다른 머릿글을 쓴다는 표시다. 없으면 워드가 첫 쪽에도 기본
            // 머릿글을 찍는다.
            var body = document.getDocument().getBody();
            var section = body.isSetSectPr() ? body.getSectPr() : body.addNewSectPr();
            if (!section.isSetTitlePg()) {
                section.addNewTitlePg();
            }
        }
    }

    private void writeEdge(org.apache.poi.xwpf.usermodel.XWPFHeaderFooter part,
                           DocContent.DocEdge edge) {
        // 새로 만든 머릿글에는 빈 문단이 하나 들어 있다. 그것부터 채운다.
        int at = 0;
        for (DocParagraphDto row : edge.paragraphs()) {
            XWPFParagraph paragraph = at < part.getParagraphs().size()
                    ? part.getParagraphs().get(at) : part.createParagraph();
            applyAlignment(paragraph, row.style());
            applyTab(paragraph, row.style());
            writeRuns(paragraph, row);
            at++;
        }
        // 이 문서의 머릿글은 로고와 글을 표 한 줄에 담는다. 표를 빼면 머릿글이
        // 통째로 빈다(2026-08-25 실측).
        XWPFTable first = null;
        for (DocTableDto table : edge.tables()) {
            XWPFTable made = writeEdgeTable(part, table);
            if (first == null) {
                first = made;
            }
        }
        // 로고는 그 표의 첫 칸에 둔다. 따로 문단을 만들면 머릿글이 한 줄 높아져
        // 본문이 그만큼 줄고 쪽이 늘어난다(2026-08-25 실측: 24쪽이 25쪽).
        for (DocContent.DocPicture picture : edge.pictures()) {
            XWPFParagraph paragraph = firstCellParagraph(first, part);
            writePicture(paragraph, new Drawing(picture.afterParagraphNo(), picture.bytes(),
                    picture.width(), picture.height()));
        }
    }

    /** 로고를 앉힐 자리. 표가 있으면 그 첫 칸, 없으면 머릿글 첫 문단이다. */
    private XWPFParagraph firstCellParagraph(XWPFTable table,
            org.apache.poi.xwpf.usermodel.XWPFHeaderFooter part) {
        if (table != null && !table.getRows().isEmpty()
                && !table.getRow(0).getTableCells().isEmpty()) {
            var cell = table.getRow(0).getCell(0);
            return cell.getParagraphs().isEmpty()
                    ? cell.addParagraph() : cell.getParagraphs().get(0);
        }
        return part.getParagraphs().isEmpty()
                ? part.createParagraph() : part.getParagraphs().get(0);
    }

    /** 머릿글·꼬릿글 안의 표. */
    private XWPFTable writeEdgeTable(org.apache.poi.xwpf.usermodel.XWPFHeaderFooter part,
                                     DocTableDto dto) {
        List<List<String>> cells = dto.cells();
        if (cells.isEmpty()) {
            return null;
        }
        int columns = cells.stream().mapToInt(List::size).max().orElse(0);
        XWPFTable table = part.createTable(cells.size(), columns);
        if (!dto.hasBorders()) {
            noBorders(table);
        }
        for (int rowNo = 0; rowNo < cells.size(); rowNo++) {
            var row = table.getRow(rowNo);
            for (int columnNo = 0; columnNo < columns; columnNo++) {
                writeCell(row.getCell(columnNo), dto, rowNo, columnNo);
            }
        }
        return table;
    }

    /**
     * 그 문단에 딸린 표와 그림을 쓴다.
     *
     * <p>그림은 그 문단 안에 넣는다 - 읽을 때도 그림이 든 문단의 번호로 자리를
     * 적기 때문이다. 새 문단을 만들어 넣으면 다시 읽을 때 자리가 한 칸씩 밀린다.
     */
    private void writeAfter(XWPFDocument document, XWPFParagraph paragraph, int paragraphNo,
                            List<DocTableDto> tables, List<Drawing> pictures, double room) {
        for (DocTableDto table : tables) {
            if (table.afterParagraphNo() == paragraphNo) {
                writeTable(document, table);
            }
        }
        for (Drawing picture : pictures) {
            if (picture.afterParagraphNo() == paragraphNo) {
                XWPFParagraph mine = paragraph == null
                        ? document.createParagraph() : paragraph;
                freeLineHeight(mine);
                writePicture(mine, fitted(picture, room));
            }
        }
    }

    /** 표 하나. 칸 글자와 너비·바탕색·합치기·테두리·자리를 옮긴다. */
    private void writeTable(XWPFDocument document, DocTableDto dto) {
        List<List<String>> cells = dto.cells();
        if (cells.isEmpty()) {
            return;
        }
        int columns = cells.stream().mapToInt(List::size).max().orElse(0);
        XWPFTable table = document.createTable(cells.size(), columns);
        table.setTableAlignment(tableAlignOf(dto.align()));
        applyTableWidth(table, dto);
        if (!dto.hasBorders()) {
            // 줄 세우기 용도로 쓴 표다. 격자를 그리면 원본과 딴판이 된다.
            noBorders(table);
        }
        for (int rowNo = 0; rowNo < cells.size(); rowNo++) {
            XWPFTableRow row = table.getRow(rowNo);
            Double high = at(dto.rowHeights(), rowNo);
            if (high != null && high > 0) {
                row.setHeight((int) Math.round(high * TWIP_PER_PX));
                // 규칙까지 적어야 워드가 그 높이로 못박는다. 안 적으면 "최소 높이"
                // 로 보아 3pt 짜리 색 막대가 글자 높이만큼 굵어진다(2026-08-25 실측).
                var properties = row.getCtRow().isSetTrPr()
                        ? row.getCtRow().getTrPr() : row.getCtRow().addNewTrPr();
                if (properties.sizeOfTrHeightArray() > 0) {
                    properties.getTrHeightArray(0).setHRule(dto.exactRowHeights()
                            ? org.openxmlformats.schemas.wordprocessingml.x2006.main
                                    .STHeightRule.EXACT
                            : org.openxmlformats.schemas.wordprocessingml.x2006.main
                                    .STHeightRule.AT_LEAST);
                }
            }
            for (int columnNo = 0; columnNo < columns; columnNo++) {
                writeCell(row.getCell(columnNo), dto, rowNo, columnNo);
            }
        }
    }

    private void noBorders(XWPFTable table) {
        table.setInsideHBorder(XWPFTable.XWPFBorderType.NONE, 0, 0, "auto");
        table.setInsideVBorder(XWPFTable.XWPFBorderType.NONE, 0, 0, "auto");
        table.setTopBorder(XWPFTable.XWPFBorderType.NONE, 0, 0, "auto");
        table.setBottomBorder(XWPFTable.XWPFBorderType.NONE, 0, 0, "auto");
        table.setLeftBorder(XWPFTable.XWPFBorderType.NONE, 0, 0, "auto");
        table.setRightBorder(XWPFTable.XWPFBorderType.NONE, 0, 0, "auto");
    }

    /** 칸 하나. 글자와 너비·바탕색·합치기를 옮긴다. */
    private void writeCell(XWPFTableCell cell, DocTableDto dto, int rowNo, int columnNo) {
        if (cell == null) {
            return;
        }
        List<String> line = at(dto.cells(), rowNo);
        String written = line == null ? null : at(line, columnNo);
        // 칸 글자를 맨 글로 쓰면 표 안이 죄다 기본 크기·검정이 된다(2026-08-24 실측).
        List<String> runLine = at(dto.cellRuns(), rowNo);
        List<Integer> alignLine = at(dto.cellAligns(), rowNo);
        Integer align = alignLine == null ? null : at(alignLine, columnNo);
        List<Double> spaceLine = at(dto.cellSpaces(), rowNo);
        Double space = spaceLine == null ? null : at(spaceLine, columnNo);
        writeCellText(cell, written == null ? "" : written,
                runLine == null ? null : at(runLine, columnNo),
                align == null ? DocTableDto.ALIGN_LEFT : align,
                space == null ? 0 : space);

        // 너비 수가 칸 수와 어긋나면 칸 너비를 적지 않는다. 반만 맞는 너비를 적으면
        // 앞 칸만 넓어지고 뒤 칸이 찌그러진다(2026-08-26 지적: 한글 문서 표).
        int columns = dto.cells().stream().mapToInt(List::size).max().orElse(0);
        Double wide = dto.columnWidths().size() == columns
                ? at(dto.columnWidths(), columnNo) : null;
        if (wide != null && wide > 0) {
            cell.setWidth(String.valueOf(Math.round(wide * TWIP_PER_PX)));
        }
        List<String> shades = at(dto.cellShades(), rowNo);
        String shade = shades == null ? null : at(shades, columnNo);
        if (shade != null && shade.startsWith("#")) {
            cell.setColor(shade.substring(1));
        }
        Integer across = spanAt(dto.columnSpans(), rowNo, columnNo);
        if (across != null && across > 1) {
            cell.getCTTc().addNewTcPr().addNewGridSpan()
                    .setVal(java.math.BigInteger.valueOf(across));
        }
        Integer down = spanAt(dto.rowSpans(), rowNo, columnNo);
        if (down != null && down != 1) {
            var properties = cell.getCTTc().isSetTcPr() ? cell.getCTTc().getTcPr()
                    : cell.getCTTc().addNewTcPr();
            // 0 은 위 칸에 가려진 자리다. 워드는 이어짐으로 적는다.
            properties.addNewVMerge().setVal(down == 0
                    ? org.openxmlformats.schemas.wordprocessingml.x2006.main.STMerge.CONTINUE
                    : org.openxmlformats.schemas.wordprocessingml.x2006.main.STMerge.RESTART);
        }
    }

    /**
     * 표 전체 너비. 열 너비를 더해 못박는다.
     *
     * <p>안 적으면 워드가 쪽 너비로 늘린다 - 줄 세우기용 짧은 막대가 통째로
     * 늘어났다(2026-08-24 실측).
     */
    private void applyTableWidth(XWPFTable table, DocTableDto dto) {
        double total = 0;
        for (Double one : dto.columnWidths()) {
            if (one != null && one > 0) {
                total += one;
            }
        }
        if (total <= 0) {
            return;
        }
        // 너비 수가 칸 수와 어긋나면 고르게 나눈다. 한글 문서는 표 전체 너비 하나만
        // 알려 주는데, 그것을 눈금으로 그대로 쓰면 아홉 칸짜리 표가 한 칸이 되어
        // 통째로 무너진다(2026-08-26 지적).
        int columns = dto.cells().stream().mapToInt(List::size).max().orElse(0);
        List<Double> widths = dto.columnWidths();
        if (columns > 0 && widths.size() != columns) {
            double each = total / columns;
            widths = new ArrayList<>();
            for (int at = 0; at < columns; at++) {
                widths.add(each);
            }
        }
        var properties = table.getCTTbl().getTblPr() == null
                ? table.getCTTbl().addNewTblPr() : table.getCTTbl().getTblPr();
        var width = properties.isSetTblW() ? properties.getTblW() : properties.addNewTblW();
        width.setType(org.openxmlformats.schemas.wordprocessingml.x2006.main.STTblWidth.DXA);
        width.setW(java.math.BigInteger.valueOf(twipOf(total)));
        var layout = properties.isSetTblLayout()
                ? properties.getTblLayout() : properties.addNewTblLayout();
        layout.setType(org.openxmlformats.schemas.wordprocessingml.x2006.main
                .STTblLayoutType.FIXED);

        // 열 눈금도 함께 적는다. 워드도 우리도 표를 다시 읽을 때 이 눈금을 본다 -
        // 전체 너비만 적으면 열이 고르게 벌어져 짧은 막대가 늘어난다(2026-08-24 실측).
        var grid = table.getCTTbl().getTblGrid() == null
                ? table.getCTTbl().addNewTblGrid() : table.getCTTbl().getTblGrid();
        while (grid.sizeOfGridColArray() > 0) {
            grid.removeGridCol(0);
        }
        for (Double one : widths) {
            grid.addNewGridCol().setW(java.math.BigInteger.valueOf(
                    twipOf(one == null || one <= 0 ? 0 : one)));
        }
    }

    /** 칸 글자. 서식이 적혀 있으면 그대로 살린다. */
    private void writeCellText(XWPFTableCell cell, String text, String runs, int align,
                               double space) {
        // 줄 나눔마다 문단을 만든다. 한 문단에 몰아 담으면 원본보다 표가 낮아진다.
        int at = 0;
        boolean first = true;
        for (String line : text.split(String.valueOf(CELL_LINE_BREAK), -1)) {
            XWPFParagraph paragraph;
            if (first && !cell.getParagraphs().isEmpty()) {
                paragraph = cell.getParagraphs().get(0);
            } else if (first) {
                paragraph = cell.addParagraph();
            } else {
                paragraph = cell.addParagraph();
            }
            first = false;
            if (align == DocTableDto.ALIGN_CENTER) {
                paragraph.setAlignment(ParagraphAlignment.CENTER);
            } else if (align == DocTableDto.ALIGN_RIGHT) {
                paragraph.setAlignment(ParagraphAlignment.RIGHT);
            }
            if (space > 0 && at + line.length() < text.length()) {
                // 마지막 줄에는 간격을 두지 않는다. 워드 원본이 그렇게 적는다.
                paragraph.setSpacingAfter((int) Math.round(space / PX_PER_PT * TWIP_PER_PT));
            }
            writeRuns(paragraph, new DocParagraphDto(0, line, null, slicedRuns(runs, at,
                    at + line.length())));
            at += line.length() + 1;
        }
    }

    /** 칸 글 한 줄에 걸리는 서식만 그 줄 안 자리로 옮긴다. */
    private String slicedRuns(String runs, int from, int to) {
        if (runs == null || runs.isBlank()) {
            return null;
        }
        try {
            ArrayNode all = (ArrayNode) JSON.readTree(runs);
            ArrayNode part = JSON.createArrayNode();
            for (var one : all) {
                int start = Math.max(one.path("st").asInt(), from);
                int end = Math.min(one.path("ed").asInt(), to);
                if (start >= end) {
                    continue;
                }
                ObjectNode entry = JSON.createObjectNode();
                entry.put("st", start - from);
                entry.put("ed", end - from);
                entry.set("ts", one.get("ts"));
                part.add(entry);
            }
            return part.isEmpty() ? null : part.toString();
        } catch (Exception cannotRead) {
            // 못 읽는 서식은 없는 것으로 본다.
            return null;
        }
    }

    /**
     * 글이 놓이는 폭(px). 용지에서 좌우 여백을 뺀 값이다.
     *
     * <p>용지를 모르면 A4 에 워드 기본 여백으로 본다.
     */
    private double bodyWidthOf(DocContent.DocPage page) {
        if (page == null || page.width() <= 0) {
            return DEFAULT_BODY_WIDTH;
        }
        double room = page.width() - page.marginLeft() - page.marginRight();
        return room > 0 ? room : DEFAULT_BODY_WIDTH;
    }

    /**
     * 본문보다 넓은 그림을 줄인다.
     *
     * <p>워드는 인라인 그림을 스스로 줄이지 않는다. 넘치면 오른쪽이 잘려 보인다
     * (2026-08-24 지적). 비율은 그대로 둔다.
     */
    private Drawing fitted(Drawing picture, double room) {
        // 조금 넘치는 그림은 그대로 둔다. 워드는 여백으로 살짝 넘겨 그리고, 원본도
        // 본문보다 5% 쯤 넓게 넣어 두는 일이 흔하다(2026-08-24 실측).
        if (picture.width() <= room * OVERFLOW_ALLOW || picture.width() <= 0) {
            return picture;
        }
        double ratio = room / picture.width();
        if (picture.floating()) {
            // 떠 있는 그림은 본문 폭에 맞추지 않는다. 원본 자리와 크기가 곧 뜻이다.
            return picture;
        }
        return new Drawing(picture.afterParagraphNo(), picture.bytes(), room,
                picture.height() * ratio);
    }

    /**
     * 줄 높이 못박기를 푼다.
     *
     * <p>워드는 못박은 줄 높이에 그림을 맞춰 자른다. 글에는 필요한 못박기가 그림
     * 문단에서는 그림을 잘라 먹는다(2026-08-24 지적: 이미지가 잘려 보인다).
     */
    private void freeLineHeight(XWPFParagraph paragraph) {
        var properties = paragraph.getCTP().getPPr();
        if (properties != null && properties.isSetSpacing()
                && properties.getSpacing().isSetLine()) {
            properties.getSpacing().unsetLine();
            if (properties.getSpacing().isSetLineRule()) {
                properties.getSpacing().unsetLineRule();
            }
        }
    }

    /** 그림 하나. 문단 하나를 만들어 그 안에 앉힌다. */
    private void writePicture(XWPFParagraph paragraph, Drawing picture) {
        try (var in = new java.io.ByteArrayInputStream(picture.bytes())) {
            XWPFRun run = paragraph.createRun();
            run.addPicture(in, org.apache.poi.xwpf.usermodel.Document.PICTURE_TYPE_PNG,
                    "image.png",
                    org.apache.poi.util.Units.toEMU(picture.width() / PX_PER_PT),
                    org.apache.poi.util.Units.toEMU(picture.height() / PX_PER_PT));
            if (picture.floating()) {
                floatLast(run, picture);
            }
        } catch (Exception cannotWrite) {
            // 그림 하나 때문에 파일 전체를 못 만들면 손해가 더 크다.
            logger.warn("그림을 싣지 못했다: {}", cannotWrite.getMessage());
        }
    }

    /**
     * 방금 실은 그림을 글 흐름 밖으로 띄운다.
     *
     * <p>POI 는 흐름에 든 그림(inline)만 만든다. 표지 배경을 흐름에 두면 제 쪽을
     * 차지해 쪽이 하나 는다(2026-08-25 실측: 6쪽 문서가 7쪽).
     */
    private void floatLast(XWPFRun run, Drawing picture) {
        var drawings = run.getCTR().getDrawingList();
        if (drawings.isEmpty()) {
            return;
        }
        var drawing = drawings.get(drawings.size() - 1);
        if (drawing.sizeOfInlineArray() == 0) {
            return;
        }
        var inline = drawing.getInlineArray(0);
        var anchor = drawing.addNewAnchor();
        anchor.setBehindDoc(picture.behind());
        anchor.setDistT(0);
        anchor.setDistB(0);
        anchor.setDistL(0);
        anchor.setDistR(0);
        anchor.setSimplePos2(false);
        anchor.setLocked(false);
        anchor.setLayoutInCell(true);
        anchor.setAllowOverlap(true);
        anchor.setRelativeHeight(picture.behind() ? 0 : 1);
        anchor.addNewSimplePos().setX(0);
        anchor.getSimplePos().setY(0);
        var across = anchor.addNewPositionH();
        across.setRelativeFrom(org.openxmlformats.schemas.drawingml.x2006.wordprocessingDrawing
                .STRelFromH.PAGE);
        across.setPosOffset((int) Math.round(picture.left() * EMU_PER_PX));
        var down = anchor.addNewPositionV();
        down.setRelativeFrom(org.openxmlformats.schemas.drawingml.x2006.wordprocessingDrawing
                .STRelFromV.PARAGRAPH);
        down.setPosOffset((int) Math.round(picture.top() * EMU_PER_PX));
        anchor.setExtent(inline.getExtent());
        // 네 변 값을 다 적어야 한다. 빈 채로 두면 워드가 파일을 열지 못한다
        // (2026-08-25 실측: "파일을 열 때 오류가 발생했습니다").
        var edge = anchor.addNewEffectExtent();
        edge.setL(0);
        edge.setT(0);
        edge.setR(0);
        edge.setB(0);
        anchor.addNewWrapNone();
        anchor.addNewCNvGraphicFramePr();
        anchor.setDocPr(inline.getDocPr());
        anchor.setGraphic(inline.getGraphic());
        drawing.removeInline(0);
    }

    private org.apache.poi.xwpf.usermodel.TableRowAlign tableAlignOf(int align) {
        return switch (align) {
            case DocTableDto.ALIGN_CENTER ->
                    org.apache.poi.xwpf.usermodel.TableRowAlign.CENTER;
            case DocTableDto.ALIGN_RIGHT ->
                    org.apache.poi.xwpf.usermodel.TableRowAlign.RIGHT;
            default -> org.apache.poi.xwpf.usermodel.TableRowAlign.LEFT;
        };
    }

    /** 격자에서 한 칸. 자리가 없으면 null 이다 - 짧은 줄이 흔하다. */
    private <T> T at(List<T> line, int index) {
        return line == null || index < 0 || index >= line.size() ? null : line.get(index);
    }

    private Integer spanAt(List<List<Integer>> grid, int rowNo, int columnNo) {
        List<Integer> line = at(grid, rowNo);
        return line == null ? null : at(line, columnNo);
    }

    /**
     * docx 를 문단과 표로.
     *
     * <p>표를 문단으로 펼치지 않는다. 칸 글자가 세로로 길게 늘어서 원본과 너무
     * 달라 보였다(2026-08-12 지적). 표는 표대로 담아 화면이 표로 세운다.
     */
    public DocContent readDocx(InputStream in) throws IOException {
        try (XWPFDocument document = new XWPFDocument(in)) {
            documentDefault = defaultsOf(document);
            defaultSpacing = spacingOf(document);
            defaultStyleId = null;
            previousParagraph = null;
            pendingPageBreak = false;
            List<DocParagraphDto> rows = new ArrayList<>();
            List<DocTableDto> tables = new ArrayList<>();
            List<DocContent.DocPicture> pictures = new ArrayList<>();
            for (IBodyElement element : document.getBodyElements()) {
                collect(element, rows, tables, pictures);
            }
            var policy = document.getHeaderFooterPolicy();
            var basics = defaultsFrom(document);
            return new DocContent(rows, tables, pictures, pageOf(document),
                    edgeOf(policy == null ? null : policy.getDefaultHeader()),
                    edgeOf(policy == null ? null : policy.getDefaultFooter()),
                    titlePageOf(document),
                    edgeOf(policy == null ? null : policy.getFirstPageHeader()),
                    edgeOf(policy == null ? null : policy.getFirstPageFooter()),
                    basics);
        }
    }

    /**
     * 문서 기본 서식(글꼴·크기·간격·줄 격자).
     *
     * <p>내보낸 문서에 이것이 없으면 워드가 제 기본 글꼴로 그려 줄 높이가 달라진다
     * (2026-08-24 실측). 테마로 적힌 글꼴은 테마에서 이름을 찾아 준다.
     */
    private DocContent.DocDefaults defaultsFrom(XWPFDocument document) {
        try {
            var styles = document.getStyles();
            if (styles == null) {
                return null;
            }
            org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRPr run = null;
            org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSpacing spacing = null;
            try (var in = styles.getPackagePart().getInputStream()) {
                var parsed = org.openxmlformats.schemas.wordprocessingml.x2006.main
                        .StylesDocument.Factory.parse(in).getStyles();
                var defaults = parsed == null ? null : parsed.getDocDefaults();
                var runDefault = defaults == null ? null : defaults.getRPrDefault();
                run = runDefault == null ? null : runDefault.getRPr();
                var paragraphDefault = defaults == null ? null : defaults.getPPrDefault();
                spacing = paragraphDefault == null || paragraphDefault.getPPr() == null
                        ? null : paragraphDefault.getPPr().getSpacing();
            }
            // 기본 서식(Normal)이 적어 둔 글꼴이 먼저다. 문서 기본값만 보면 테마
            // 글꼴이 나와 실제와 다르다(2026-08-25 실측: Century Gothic 대신
            // Cambria 가 나와 줄이 덜 접혔다).
            var normal = defaultStyleRun(document);
            String font = themeFont(document, normal, false);
            if (font == null) {
                font = themeFont(document, run, false);
            }
            String eastAsia = themeFont(document, normal, true);
            if (eastAsia == null) {
                eastAsia = themeFont(document, run, true);
            }
            double size = run != null && run.sizeOfSzArray() > 0
                    ? new java.math.BigDecimal(run.getSzArray(0).getVal().toString())
                            .doubleValue() / 2
                    : DEFAULT_FONT_SIZE;
            // 간격도 Normal 이 먼저다. 문서 기본값만 보면 줄 높이가 달라져 쪽이
            // 늘어난다(2026-08-25 실측: 12.95pt 를 13.8pt 로 그려 24쪽이 25쪽).
            var normalSpacing = defaultStyleSpacing(document);
            Double lines = lineMultipleOf(normalSpacing);
            if (lines == null) {
                lines = lineMultipleOf(spacing);
            }
            Double below = spaceAfterOf(normalSpacing);
            if (below == null) {
                below = spaceAfterOf(spacing);
            }
            return new DocContent.DocDefaults(font, eastAsia, size, lines, below,
                    linePitchOf(document));
        } catch (Exception cannotRead) {
            // 기본 서식을 못 읽어도 문서는 연다.
            return null;
        }
    }

    /** 줄 높이 배수. 안 적혔으면 null 이다. */
    private Double lineMultipleOf(
            org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSpacing spacing) {
        return spacing == null || !spacing.isSetLine() ? null
                : new java.math.BigDecimal(spacing.getLine().toString()).doubleValue() / 240;
    }

    /** 문단 뒤 간격(pt). 안 적혔으면 null 이다. */
    private Double spaceAfterOf(
            org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSpacing spacing) {
        return spacing == null || !spacing.isSetAfter() ? null
                : new java.math.BigDecimal(spacing.getAfter().toString())
                        .doubleValue() / TWIP_PER_PT;
    }

    /** 기본 서식(Normal)의 문단 간격. 없으면 null 이다. */
    private org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSpacing
            defaultStyleSpacing(XWPFDocument document) {
        var styles = document.getStyles();
        if (styles == null) {
            return null;
        }
        String id = defaultStyleIdOf(styles);
        var style = id == null ? null : styles.getStyle(id);
        var paragraph = style == null ? null : style.getCTStyle().getPPr();
        return paragraph == null ? null : paragraph.getSpacing();
    }

    /** 기본 서식(Normal)의 글자 서식. 없으면 null 이다. */
    private org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRPr
            defaultStyleRun(XWPFDocument document) {
        var styles = document.getStyles();
        if (styles == null) {
            return null;
        }
        String id = defaultStyleIdOf(styles);
        var style = id == null ? null : styles.getStyle(id);
        return style == null ? null : style.getCTStyle().getRPr();
    }

    /** 줄 격자 간격(트윕). 안 적혀 있으면 0 이다. */
    private int linePitchOf(XWPFDocument document) {
        var body = document.getDocument().getBody();
        var section = body.isSetSectPr() ? body.getSectPr() : null;
        var grid = section == null || !section.isSetDocGrid() ? null : section.getDocGrid();
        return grid == null || grid.getLinePitch() == null ? 0
                : new java.math.BigDecimal(grid.getLinePitch().toString()).intValue();
    }

    /**
     * 문서 기본 글꼴 이름.
     *
     * <p>워드는 글꼴을 테마 이름(minorHAnsi)으로 적어 두는 일이 흔하다. 그 이름을
     * 테마에서 실제 글꼴로 풀어 준다 - 우리 파일에는 테마가 없기 때문이다.
     */
    private String themeFont(XWPFDocument document,
            org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRPr run,
            boolean eastAsia) {
        if (run == null || run.sizeOfRFontsArray() == 0) {
            return null;
        }
        var fonts = run.getRFontsArray(0);
        String written = eastAsia ? fonts.getEastAsia() : fonts.getAscii();
        if (written != null && !written.isBlank()) {
            return written;
        }
        String theme = eastAsia ? fonts.getEastAsiaTheme() == null ? null
                : fonts.getEastAsiaTheme().toString()
                : fonts.getAsciiTheme() == null ? null : fonts.getAsciiTheme().toString();
        if (theme == null) {
            return null;
        }
        try {
            var themes = document.getRelationParts().stream()
                    .map(part -> part.getDocumentPart())
                    .filter(part -> part instanceof org.apache.poi.xwpf.usermodel.XWPFTheme)
                    .map(part -> (org.apache.poi.xwpf.usermodel.XWPFTheme) part)
                    .findFirst()
                    .orElse(null);
            if (themes == null) {
                return null;
            }
            // 테마 파일을 직접 읽는다. POI 의 getMinorFont() 는 우리가 찾는 값과
            // 다른 것을 준다(2026-08-25 실측: Aptos 대신 Cambria 가 나왔다).
            try (var in = themes.getPackagePart().getInputStream()) {
                var parsed = org.openxmlformats.schemas.drawingml.x2006.main
                        .ThemeDocument.Factory.parse(in).getTheme();
                var scheme = parsed.getThemeElements().getFontScheme();
                var picked = theme.startsWith("major") ? scheme.getMajorFont()
                        : scheme.getMinorFont();
                var found = eastAsia ? picked.getEa() : picked.getLatin();
                String name = found == null ? null : found.getTypeface();
                return name == null || name.isBlank() ? null : name;
            }
        } catch (Exception cannotRead) {
            return null;
        }
    }

    /**
     * 쪽마다 되풀이되는 글(머릿글·꼬릿글).
     *
     * <p>워드는 쪽마다, 첫 쪽만, 홀짝으로 여러 벌을 둘 수 있다. 우리는 첫 벌만
     * 쓴다 - 여러 벌을 그리는 화면이 아직 없다.
     *
     * <p>없으면 아무것도 돌려주지 않는다.
     */
    private DocContent.DocEdge edgeOf(org.apache.poi.xwpf.usermodel.XWPFHeaderFooter part) {
        if (part == null) {
            return null;
        }
        List<DocParagraphDto> rows = new ArrayList<>();
        List<DocTableDto> tables = new ArrayList<>();
        // 머릿글 그림(회사 로고)도 담는다. 안 담으면 내려받은 문서에서 로고가
        // 통째로 빠진다(2026-08-25 지적).
        List<DocContent.DocPicture> pictures = new ArrayList<>();
        for (IBodyElement element : edgeElements(part)) {
            collect(element, rows, tables, pictures);
        }
        collectEdgeTablePictures(part, pictures);
        if (tables.isEmpty() && pictures.isEmpty()
                && rows.stream().allMatch(row -> row.text().isBlank())) {
            return null;
        }
        return new DocContent.DocEdge(rows, tables, pictures);
    }

    /**
     * 복합 필드 한 벌을 읽는 자.
     *
     * <p>워드는 필드를 run 여럿에 걸쳐 적는다 - begin, 지시문(instrText),
     * separate, 계산해 둔 값, end. 우리가 담는 것은 "계산해 둔 값" 의 자리와
     * 종류다. 화면이 그 자리에 쪽 번호를 다시 찍는다.
     */
    private static final class Field {

        private boolean inside;
        private boolean afterSeparate;
        private final StringBuilder instruction = new StringBuilder();
        private int from;

        /** run 하나를 읽는다. {@code at} 은 지금까지 담은 글자 수다. */
        void read(XWPFRun run, int at, ArrayNode runs) {
            var ctr = run.getCTR();
            for (int i = 0; i < ctr.sizeOfInstrTextArray(); i += 1) {
                if (inside && !afterSeparate) {
                    instruction.append(ctr.getInstrTextArray(i).getStringValue());
                }
            }
            for (int i = 0; i < ctr.sizeOfFldCharArray(); i += 1) {
                var type = ctr.getFldCharArray(i).getFldCharType();
                if (type == org.openxmlformats.schemas.wordprocessingml.x2006.main.STFldCharType.BEGIN) {
                    inside = true;
                    afterSeparate = false;
                    instruction.setLength(0);
                } else if (type == org.openxmlformats.schemas.wordprocessingml.x2006.main.STFldCharType.SEPARATE) {
                    afterSeparate = true;
                    from = at;
                } else if (type == org.openxmlformats.schemas.wordprocessingml.x2006.main.STFldCharType.END) {
                    close(at, runs);
                }
            }
        }

        private void close(int at, ArrayNode runs) {
            String kind = kindOf(instruction.toString());
            if (afterSeparate && at > from && kind != null) {
                ObjectNode entry = JSON.createObjectNode();
                entry.put("st", from);
                entry.put("ed", at);
                entry.put("fld", kind);
                runs.add(entry);
            }
            inside = false;
            afterSeparate = false;
            instruction.setLength(0);
        }

        /** 지시문의 첫 낱말. 우리가 다시 찍을 수 있는 것만 담는다. */
        private static String kindOf(String instruction) {
            String[] words = instruction.trim().split("\s+");
            String first = words.length == 0 ? "" : words[0].toUpperCase(java.util.Locale.ROOT);
            return "PAGE".equals(first) || "NUMPAGES".equals(first) ? first : null;
        }
    }

    /**
     * 머릿글·바닥글의 요소를 차례대로.
     *
     * <p>POI 의 {@code getBodyElements()} 는 콘텐츠 컨트롤(SDT)을 제 갈래로 주고
     * 그 안의 문단은 내주지 않는다. 워드는 쪽 번호 줄을 컨트롤로 감싸 두곤 하는데,
     * 그러면 그 줄이 통째로 빠진다(2026-10-16 실측: "Page 2 of 8" 이 사라졌다).
     * 그래서 XML 을 직접 훑어 컨트롤 안으로 들어간다.
     */
    private List<IBodyElement> edgeElements(
            org.apache.poi.xwpf.usermodel.XWPFHeaderFooter part) {
        List<IBodyElement> elements = new ArrayList<>();
        collectEdgeXml(part._getHdrFtr(), part, elements);
        return elements;
    }

    /** 표 안의 문단은 표가 맡는다 - 표로 들어가지 않는다. */
    private void collectEdgeXml(org.apache.xmlbeans.XmlObject node,
                                org.apache.poi.xwpf.usermodel.XWPFHeaderFooter part,
                                List<IBodyElement> elements) {
        try (var cursor = node.newCursor()) {
            if (!cursor.toFirstChild()) {
                return;
            }
            do {
                org.apache.xmlbeans.XmlObject child = cursor.getObject();
                if (child instanceof org.openxmlformats.schemas.wordprocessingml.x2006.main.CTP p) {
                    elements.add(new XWPFParagraph(p, part));
                } else if (child instanceof
                        org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTbl table) {
                    elements.add(new XWPFTable(table, part));
                } else if (child instanceof
                        org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSdtBlock sdt) {
                    collectEdgeXml(sdt.getSdtContent(), part, elements);
                }
            } while (cursor.toNextSibling());
        }
    }

    /** 머릿글 표 안의 그림. 표 칸에 로고를 넣어 두는 문서가 흔하다. */
    private void collectEdgeTablePictures(org.apache.poi.xwpf.usermodel.XWPFHeaderFooter part,
                                          List<DocContent.DocPicture> pictures) {
        for (XWPFTable table : part.getTables()) {
            for (XWPFTableRow row : table.getRows()) {
                for (XWPFTableCell cell : row.getTableCells()) {
                    for (XWPFParagraph paragraph : cell.getParagraphs()) {
                        collectPictures(paragraph, -1, pictures);
                    }
                }
            }
        }
    }

    /**
     * 첫 쪽에 다른 머릿글·꼬릿글을 쓰는가(titlePg).
     *
     * <p>이 문서는 첫 쪽에 아무것도 안 찍는다. 그것을 안 보면 첫 쪽에도 머릿글이
     * 찍혀 원본과 다르다(2026-08-13 지적).
     */
    private boolean titlePageOf(XWPFDocument document) {
        var body = document.getDocument().getBody();
        var section = body.isSetSectPr() ? body.getSectPr() : null;
        return section != null && section.isSetTitlePg();
    }

    /** 옛 이름. 문단만 쓰는 자리가 아직 있다. */
    public List<DocParagraphDto> fromDocx(InputStream in) throws IOException {
        return readDocx(in).paragraphs();
    }

    private void collect(IBodyElement element, List<DocParagraphDto> rows,
                         List<DocTableDto> tables, List<DocContent.DocPicture> pictures) {
        if (element instanceof XWPFParagraph paragraph) {
            // 앞 문단이 "같은 서식끼리 붙여 그리기" 이고 이 문단과 서식이 같으면
            // 앞 문단의 아래 간격을 지운다. 워드가 하는 일이다 - 서식이 다른
            // 이웃과는 간격을 둔다(2026-08-19 지적: 목록 묶음이 밀착해 보였다).
            if (!rows.isEmpty() && previousParagraph != null
                    && joinsNeighbours(previousParagraph)
                    && sameStyle(previousParagraph, paragraph)) {
                rows.set(rows.size() - 1, withoutSpaceBelow(rows.get(rows.size() - 1)));
            }
            rows.addAll(readParagraphs(paragraph, rows.size()));
            previousParagraph = paragraph;
            collectPictures(paragraph, rows.size() - 1, pictures);
            return;
        }
        if (element instanceof XWPFTable table) {
            previousParagraph = null;
            // 앞 문단 뒤에 놓인다. 문단이 하나도 없으면 문서 맨 앞이다.
            // 쪽 나눔이 걸려 있으면 이 표가 새 쪽을 연다 - 뒤 문단에게 넘기면
            // 목차 표만 표지 쪽에 남는다(2026-08-25 지적).
            DocTableDto grid = gridOf(table, rows.size() - 1);
            if (pendingPageBreak) {
                pendingPageBreak = false;
                grid = new DocTableDto(grid.afterParagraphNo(), grid.cells(),
                        grid.columnWidths(), grid.cellShades(), grid.columnSpans(),
                        grid.rowSpans(), grid.hasBorders(), grid.cellRuns(), grid.align(),
                        grid.rowHeights(), grid.cellAligns(), grid.cellSpaces(), true,
                        grid.borders());
            }
            tables.add(grid);
        }
    }

    /**
     * 종이 크기와 여백.
     *
     * <p>워드는 길이를 twip(1/20 pt)으로 적는다. 여백이 다르면 본문 폭이 달라져
     * 표와 그림이 원본과 다른 자리에 앉는다(2026-08-13 지적: 표 크기가 다르다).
     *
     * <p>적혀 있지 않으면 아무것도 돌려주지 않는다 - 화면이 쓰는 기본(A4)이 낫다.
     */
    private DocContent.DocPage pageOf(XWPFDocument document) {
        var body = document.getDocument().getBody();
        var section = body.isSetSectPr() ? body.getSectPr() : null;
        if (section == null) {
            return null;
        }
        var size = section.getPgSz();
        var margin = section.getPgMar();
        if (size == null || margin == null) {
            return null;
        }
        return new DocContent.DocPage(
                twipToPx(size.getW()), twipToPx(size.getH()),
                twipToPx(margin.getLeft()), twipToPx(margin.getRight()),
                twipToPx(margin.getTop()), twipToPx(margin.getBottom()),
                twipToPx(margin.getHeader()), twipToPx(margin.getFooter()));
    }

    /** twip 을 px 로. 못 읽는 값은 0 이다. */
    private double twipToPx(Object twip) {
        if (twip == null) {
            return 0;
        }
        return new java.math.BigDecimal(twip.toString()).doubleValue() / TWIP_PER_PX;
    }

    /** 떠 있는 그림 하나의 자리. */
    private record Anchored(boolean behind, double left, double top, int fromH, int fromV) {
    }

    /**
     * 워드가 자리를 재는 기준을 화면 것으로 바꾼다.
     *
     * <p>화면(Univer)은 가로를 0 쪽 · 1 단 · 2 글자 · 3 여백으로, 세로를
     * 0 쪽 · 1 문단 · 2 줄 · 3 여백으로 센다.
     */
    private int relativeH(String from) {
        if (from == null) {
            return 0;
        }
        return switch (from) {
            case "column" -> 1;
            case "character" -> 2;
            case "margin", "leftMargin", "rightMargin", "insideMargin", "outsideMargin" -> 3;
            default -> 0;
        };
    }

    private int relativeV(String from) {
        if (from == null) {
            return 1;
        }
        return switch (from) {
            case "page" -> 0;
            case "line" -> 2;
            case "margin", "topMargin", "bottomMargin", "insideMargin", "outsideMargin" -> 3;
            default -> 1;
        };
    }

    /**
     * 이 런에 든 그림이 떠 있는지, 어디에 떠 있는지.
     *
     * <p>런 안 그림 차례대로 담는다. 떠 있지 않은 그림 자리에는 null 을 둔다 -
     * {@code getEmbeddedPictures()} 가 주는 차례와 짝을 맞춰야 한다.
     */
    private List<Anchored> anchorsOf(XWPFRun run) {
        List<Anchored> anchors = new ArrayList<>();
        for (var drawing : run.getCTR().getDrawingList()) {
            for (var anchor : drawing.getAnchorList()) {
                if (!hasPicture(anchor.xmlText())) {
                    // 글상자·도형은 그림이 아니다. 자리도 차례도 세지 않는다.
                    continue;
                }
                var across = anchor.getPositionH();
                var down = anchor.getPositionV();
                double left = across == null ? 0 : across.getPosOffset() / EMU_PER_PX;
                double top = down == null ? 0 : down.getPosOffset() / EMU_PER_PX;
                anchors.add(new Anchored(anchor.getBehindDoc(), left, top,
                        relativeH(across == null || across.getRelativeFrom() == null ? null
                                : across.getRelativeFrom().toString()),
                        relativeV(down == null || down.getRelativeFrom() == null ? null
                                : down.getRelativeFrom().toString())));
            }
            for (int more = 0; more < drawing.sizeOfInlineArray(); more++) {
                anchors.add(null);
            }
        }
        return anchors;
    }

    /** 이 앵커가 그림을 담고 있는가. 글상자·도형에는 그림 알맹이가 없다. */
    private boolean hasPicture(String xml) {
        return xml.contains("<pic:pic") || xml.contains(":blip ");
    }

    /** 워드 길이 단위(EMU) 하나가 화면에서 차지하는 자리. 96dpi 다. */
    private static final double EMU_PER_PX = 9525;

    /**
     * 문단에 박힌 그림을 모은다.
     *
     * <p>그림이 빠지면 결재 문서의 도장·화면 갈무리가 통째로 사라진다. 크기는
     * 워드가 EMU(1/914400 인치)로 적으므로 96dpi 로 바꾼다.
     *
     * <p>글 흐름 밖에 떠 있는 그림(앵커)은 떠 있다는 표시와 자리를 함께 담는다.
     * 흐름에 넣으면 표지 배경 그림이 첫 쪽을 통째로 차지한다(2026-08-25 지적).
     */
    private void collectPictures(XWPFParagraph paragraph, int afterParagraphNo,
                                 List<DocContent.DocPicture> pictures) {
        for (XWPFRun run : paragraph.getRuns()) {
            List<Anchored> anchored = anchorsOf(run);
            int at = -1;
            for (var picture : run.getEmbeddedPictures()) {
                at += 1;
                byte[] bytes = picture.getPictureData() == null
                        ? null : picture.getPictureData().getData();
                if (bytes == null || bytes.length == 0) {
                    continue;
                }
                Anchored floating = at < anchored.size() ? anchored.get(at) : null;
                if (floating != null) {
                    pictures.add(new DocContent.DocPicture(afterParagraphNo, bytes,
                            picture.getWidth() * PX_PER_PT, picture.getDepth() * PX_PER_PT,
                            true, floating.behind(), floating.left(), floating.top(),
                            floating.fromH(), floating.fromV()));
                    continue;
                }
                pictures.add(new DocContent.DocPicture(afterParagraphNo, bytes,
                        picture.getWidth() * PX_PER_PT, picture.getDepth() * PX_PER_PT));
            }
        }
    }

    /**
     * 표를 고른 격자로 펼친다.
     *
     * <p>워드는 칸을 합치면 그 자리의 {@code tc} 를 아예 빼거나(가로) 이어짐으로
     * 표시한다(세로). 그대로 옮기면 행마다 칸 수가 달라 표가 어긋난다. 자리는 모두
     * 채우고 합치기는 숫자로 적는다 - 화면(Univer)이 쓰는 규칙과 같다.
     */
    private DocTableDto gridOf(XWPFTable table, int afterParagraphNo) {
        // 워드 표는 글자 크기를 칸이 아니라 표 스타일에만 적기도 한다. 그것을 안
        // 보면 표 글자가 문서 기본 크기로 나와 원본보다 크다(2026-08-13 지적).
        Inherited outer = documentDefault;
        documentDefault = styleOf(table, outer);
        try {
            return gridBody(table, afterParagraphNo);
        } finally {
            documentDefault = outer;
        }
    }

    /** 표 스타일에 적힌 글자 모양. 없으면 물려받은 것을 그대로 쓴다. */
    private Inherited styleOf(XWPFTable table, Inherited fallback) {
        String styleId = table.getStyleID();
        var styles = table.getBody() == null || table.getBody().getXWPFDocument() == null
                ? null : table.getBody().getXWPFDocument().getStyles();
        if (styleId == null || styles == null) {
            return fallback;
        }
        var style = styles.getStyle(styleId);
        var properties = style == null ? null : style.getCTStyle().getRPr();
        if (properties == null) {
            return fallback;
        }
        Double size = fallback.size();
        if (properties.sizeOfSzArray() > 0 && properties.getSzArray(0).getVal() != null) {
            size = new java.math.BigDecimal(properties.getSzArray(0).getVal().toString())
                    .doubleValue() / 2;
        }
        String font = fallback.font();
        if (properties.sizeOfRFontsArray() > 0
                && properties.getRFontsArray(0).getAscii() != null) {
            font = properties.getRFontsArray(0).getAscii();
        }
        return new Inherited(size, font, fallback.color());
    }

    private DocTableDto gridBody(XWPFTable table, int afterParagraphNo) {
        List<List<String>> cells = new ArrayList<>();
        List<List<String>> shades = new ArrayList<>();
        List<List<String>> runs = new ArrayList<>();
        List<List<Integer>> columnSpans = new ArrayList<>();
        List<List<Integer>> rowSpans = new ArrayList<>();
        List<List<Integer>> cellAligns = new ArrayList<>();
        List<List<Double>> cellSpaces = new ArrayList<>();

        for (XWPFTableRow row : table.getRows()) {
            List<String> line = new ArrayList<>();
            List<String> shade = new ArrayList<>();
            List<String> style = new ArrayList<>();
            List<Integer> across = new ArrayList<>();
            List<Integer> down = new ArrayList<>();
            List<Integer> aligns = new ArrayList<>();
            List<Double> spaces = new ArrayList<>();

            for (XWPFTableCell cell : row.getTableCells()) {
                int span = gridSpanOf(cell);
                var merge = verticalMergeOf(cell);
                boolean covered = merge == VerticalMerge.CONTINUE;

                CellContent content = covered ? CellContent.EMPTY : contentOf(cell);
                line.add(content.text());
                style.add(content.runs());
                shade.add(rgbOf(cell.getColor()));
                aligns.add(cellAlignOf(cell));
                spaces.add(covered ? 0 : cellSpaceOf(cell));
                across.add(covered ? 0 : span);
                down.add(covered ? 0 : 1);
                if (covered) {
                    // 위 칸이 이 자리를 먹었다. 그 칸의 줄 수를 늘린다.
                    growVertical(rowSpans, cells.size(), across.size() - 1);
                }
                // 가로로 합친 칸이 먹은 자리. 비워 두면 뒤 칸이 앞으로 밀린다.
                for (int more = 1; more < span; more++) {
                    line.add("");
                    style.add(null);
                    shade.add(null);
                    aligns.add(DocTableDto.ALIGN_LEFT);
                    spaces.add(0.0);
                    across.add(0);
                    down.add(covered ? 0 : 1);
                }
            }
            cells.add(line);
            shades.add(shade);
            runs.add(style);
            columnSpans.add(across);
            rowSpans.add(down);
            cellAligns.add(aligns);
            cellSpaces.add(spaces);
        }

        return new DocTableDto(afterParagraphNo, cells, columnWidthsOf(table), shades,
                columnSpans, rowSpans, hasBorders(table), runs,
                alignOf(table), rowHeightsOf(table), cellAligns, cellSpaces,
                false, bordersOf(table));
    }

    /**
     * 칸 글의 가로 맞춤.
     *
     * <p>머릿글은 로고와 글을 표 한 줄에 담고 글을 오른쪽에 세운다. 맞춤을 흘리면
     * 글이 가운데로 온다(2026-08-25 지적).
     */
    private int cellAlignOf(XWPFTableCell cell) {
        for (var paragraph : cell.getParagraphs()) {
            var align = paragraph.getAlignment();
            if (align == ParagraphAlignment.CENTER) {
                return DocTableDto.ALIGN_CENTER;
            }
            if (align == ParagraphAlignment.RIGHT) {
                return DocTableDto.ALIGN_RIGHT;
            }
        }
        return DocTableDto.ALIGN_LEFT;
    }

    /**
     * 표가 앉는 자리.
     *
     * <p>가운데 표를 왼쪽에 붙이면 원본과 딴판이다(2026-08-20 지적: 표지의 주황
     * 막대가 왼쪽으로 쏠렸다 - 그 막대가 폭 2인치짜리 가운데 표다).
     */
    private int alignOf(XWPFTable table) {
        var properties = table.getCTTbl().getTblPr();
        var jc = properties == null || !properties.isSetJc() ? null : properties.getJc();
        if (jc == null || jc.getVal() == null) {
            return DocTableDto.ALIGN_LEFT;
        }
        return switch (jc.getVal().toString()) {
            case "center" -> DocTableDto.ALIGN_CENTER;
            case "right", "end" -> DocTableDto.ALIGN_RIGHT;
            default -> DocTableDto.ALIGN_LEFT;
        };
    }

    /**
     * 못박은 줄 높이(px).
     *
     * <p>막대처럼 쓰는 표는 높이를 정확히 못박는다. 그것을 잃으면 4px 막대가 글
     * 한 줄 높이로 부푼다. "최소" 로 적힌 높이는 담지 않는다 - 글이 길면 늘어나야
     * 하는데 우리가 못박으면 글이 잘린다.
     */
    private List<Double> rowHeightsOf(XWPFTable table) {
        List<Double> heights = new ArrayList<>();
        for (XWPFTableRow row : table.getRows()) {
            var properties = row.getCtRow().getTrPr();
            var height = properties == null || properties.sizeOfTrHeightArray() == 0
                    ? null : properties.getTrHeightArray(0);
            boolean exact = height != null && height.getHRule() != null
                    && "exact".equals(height.getHRule().toString());
            heights.add(exact && height.getVal() != null
                    ? twipToPx(height.getVal()) : null);
        }
        return heights;
    }

    /** 칸 글자와 그 서식. 서식 자리는 칸 글자 안 자리다. */
    private record CellContent(String text, String runs) {

        static final CellContent EMPTY = new CellContent("", null);
    }

    /**
     * 칸 안 문단 사이 간격(px).
     *
     * <p>첫 문단이 적어 둔 값을 칸 전체의 값으로 본다. 칸 안에서 문단마다 다르게
     * 적는 원본은 드물고, 흘리면 박스가 낮아져 뒷글이 앞 쪽으로 올라온다.
     */
    private double cellSpaceOf(XWPFTableCell cell) {
        for (XWPFParagraph paragraph : cell.getParagraphs()) {
            Integer below = ownSpacingAfter(paragraph);
            if (below != null && below > 0) {
                return below / TWIP_PER_PT * PX_PER_PT;
            }
        }
        return 0;
    }

    /**
     * 칸 안 문단이 쓰는 자동 번호를 글자로.
     *
     * <p>같은 목록(numId)끼리 이어 센다. 글머리표(bullet)는 세지 않는다 - 번호가
     * 아니라 기호라 굳힐 것이 없다.
     */
    private String cellNumberOf(XWPFParagraph paragraph) {
        java.math.BigInteger numId = ownNumId(paragraph);
        if (numId == null) {
            numId = styleNumId(paragraph);
        }
        if (numId == null || numId.signum() == 0 || !numbered(paragraph)) {
            return null;
        }
        int next = cellNumbers.merge(numId, 1, Integer::sum);
        return next + ". ";
    }

    /** 칸 안 자동 번호를 목록마다 센다. 문서 하나를 읽는 동안만 쓴다. */
    private final java.util.Map<java.math.BigInteger, Integer> cellNumbers =
            new java.util.HashMap<>();

    /** 칸 안 문단을 가르는 글자. 화면도 이 글자로 줄을 나눈다. */
    private static final char CELL_LINE_BREAK = (char) 10;

    /**
     * 칸 안의 글자와 서식.
     *
     * <p>{@code cell.getText()} 를 쓰지 않는 이유: 서식 자리를 글자 자리와 맞추려면
     * 우리가 직접 이어 붙여야 한다. 표 머리가 굵은 원본이 흔한데 서식이 빠지면
     * 머리와 본문이 같아 보인다.
     */
    private CellContent contentOf(XWPFTableCell cell) {
        StringBuilder text = new StringBuilder();
        ArrayNode runs = JSON.createArrayNode();

        for (XWPFParagraph paragraph : cell.getParagraphs()) {
            // 칸 안 문단은 줄을 나눈다. 이어 붙이면 줄이 덜 접혀 표가 낮아지고
            // 뒷글이 앞 쪽으로 올라온다(2026-08-25 지적: 표가 다음 쪽으로 넘어간다).
            if (text.length() > 0) {
                text.append(CELL_LINE_BREAK);
            }
            // 칸 안 문단이 자동 번호를 쓰면 그 번호를 글자로 굳혀 담는다. 흘리면
            // 제목 앞 "1." 이 사라진다(2026-08-26 지적). 표 글은 화면에서도
            // 내려받을 때도 글자로만 오간다 - 목록 표시를 실을 자리가 없다.
            String number = cellNumberOf(paragraph);
            if (number != null) {
                text.append(number);
            }
            Inherited inherited = inheritedOf(paragraph);
            for (XWPFRun run : paragraph.getRuns()) {
                String piece = run.text();
                if (piece == null || piece.isEmpty()) {
                    continue;
                }
                int from = text.length();
                text.append(piece);

                ObjectNode style = styleOf(run, inherited);
                if (style.isEmpty()) {
                    continue;
                }
                ObjectNode entry = JSON.createObjectNode();
                entry.put("st", from);
                entry.put("ed", text.length());
                entry.set("ts", style);
                runs.add(entry);
            }
        }
        // 끝에 남은 빈 문단이 만든 줄 나눔은 지운다. 워드는 그 자리에 아무것도
        // 그리지 않는다.
        while (text.length() > 0 && text.charAt(text.length() - 1) == CELL_LINE_BREAK) {
            text.setLength(text.length() - 1);
        }
        return new CellContent(text.toString(), runs.isEmpty() ? null : runs.toString());
    }

    /**
     * 그은 변만 담는다.
     *
     * <p>네 변 가운데 아래만 긋는 제목 표가 흔하다. "선이 있다" 로만 담으면 격자로
     * 그려져 원본과 딴판이 된다(2026-08-26 지적: 목차·제목 표 테두리).
     */
    private String bordersOf(XWPFTable table) {
        var properties = table.getCTTbl().getTblPr();
        var borders = properties == null ? null : properties.getTblBorders();
        if (borders == null) {
            return null;
        }
        ObjectNode drawn = JSON.createObjectNode();
        putBorder(drawn, "top", borders.getTop());
        putBorder(drawn, "bottom", borders.getBottom());
        putBorder(drawn, "left", borders.getLeft());
        putBorder(drawn, "right", borders.getRight());
        putBorder(drawn, "insideH", borders.getInsideH());
        putBorder(drawn, "insideV", borders.getInsideV());
        return drawn.isEmpty() ? null : drawn.toString();
    }

    /** 워드가 적은 색을 여섯 자리 hex 로. 못 읽으면 null 이다. */
    private String hexOf(Object color) {
        if (color == null) {
            return null;
        }
        if (color instanceof byte[] bytes) {
            StringBuilder hex = new StringBuilder();
            for (byte one : bytes) {
                hex.append(String.format("%02x", one));
            }
            return hex.toString();
        }
        return color.toString();
    }

    /** 그은 변 하나. 안 그은 변은 담지 않는다. */
    private void putBorder(ObjectNode into, String side,
            org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBorder border) {
        if (isNone(border)) {
            return;
        }
        ObjectNode one = JSON.createObjectNode();
        // 워드는 굵기를 1/8 pt 로 적는다. 화면은 px 로 그린다.
        if (border.getSz() != null) {
            one.put("size", new java.math.BigDecimal(border.getSz().toString())
                    .doubleValue() / 8 * PX_PER_PT);
        }
        // 색은 바이트 배열로 온다. 그대로 문자로 만들면 "[B@4040acdd" 가 된다.
        String color = hexOf(border.getColor());
        if (color != null && !color.isBlank() && !"auto".equalsIgnoreCase(color)) {
            one.put("color", "#" + color.toLowerCase());
        }
        into.set(side, one);
    }

    /**
     * 선이 그어진 표인지.
     *
     * <p>줄 세우기 용도로 테두리를 지운 표가 흔하다. 그것을 격자로 그리면 원본과
     * 딴판이다. 표 테두리를 안 적은 문서는 선이 있는 것으로 본다 - 워드 기본이다.
     */
    private boolean hasBorders(XWPFTable table) {
        var properties = table.getCTTbl().getTblPr();
        if (properties == null || properties.getTblBorders() == null) {
            // 테두리도 표 서식도 안 적었으면 워드 기본 표 서식이고, 그것은 선이
            // 없다. "안 적혔으니 있다" 로 보면 색 막대에 검은 테두리가 생긴다
            // (2026-08-25 실측). 칸마다 그은 선이 있으면 선 있는 표다.
            boolean styled = properties != null && properties.getTblStyle() != null
                    && properties.getTblStyle().getVal() != null
                    && !properties.getTblStyle().getVal().isBlank();
            return styled || hasCellBorders(table);
        }
        var borders = properties.getTblBorders();
        return !(isNone(borders.getTop()) && isNone(borders.getBottom())
                && isNone(borders.getLeft()) && isNone(borders.getRight())
                && isNone(borders.getInsideH()) && isNone(borders.getInsideV()));
    }

    /** 칸마다 선을 그어 둔 표인가. */
    private boolean hasCellBorders(XWPFTable table) {
        for (var row : table.getRows()) {
            for (var cell : row.getTableCells()) {
                var properties = cell.getCTTc().getTcPr();
                if (properties != null && properties.getTcBorders() != null) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isNone(org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBorder border) {
        return border == null || border.getVal() == null
                || border.getVal() == org.openxmlformats.schemas.wordprocessingml
                        .x2006.main.STBorder.NONE
                || border.getVal() == org.openxmlformats.schemas.wordprocessingml
                        .x2006.main.STBorder.NIL;
    }

    /** 세로로 이어진 칸을 만나면 위쪽 주인 칸의 줄 수를 하나 늘린다. */
    private void growVertical(List<List<Integer>> rowSpans, int row, int column) {
        for (int above = row - 1; above >= 0; above--) {
            List<Integer> line = rowSpans.get(above);
            if (column >= line.size()) {
                return;
            }
            int span = line.get(column);
            if (span == 0) {
                // 이 자리도 가려졌다. 더 위에 주인이 있다.
                continue;
            }
            line.set(column, span + 1);
            return;
        }
    }

    private int gridSpanOf(XWPFTableCell cell) {
        var properties = cell.getCTTc().getTcPr();
        if (properties == null || properties.getGridSpan() == null
                || properties.getGridSpan().getVal() == null) {
            return 1;
        }
        return Math.max(1, new java.math.BigDecimal(
                properties.getGridSpan().getVal().toString()).intValue());
    }

    /** 세로 합치기 상태. */
    private enum VerticalMerge { NONE, RESTART, CONTINUE }

    /**
     * 세로 합치기를 읽는다.
     *
     * <p>워드는 이어지는 칸에 값을 적지 않기도 한다({@code <vMerge/>} 하나뿐이다).
     * 값이 없으면 이어짐으로 본다 - 이것이 워드 규칙이다.
     */
    private VerticalMerge verticalMergeOf(XWPFTableCell cell) {
        var properties = cell.getCTTc().getTcPr();
        if (properties == null || properties.getVMerge() == null) {
            return VerticalMerge.NONE;
        }
        var value = properties.getVMerge().getVal();
        if (value == null) {
            return VerticalMerge.CONTINUE;
        }
        return value == org.openxmlformats.schemas.wordprocessingml.x2006.main.STMerge.RESTART
                ? VerticalMerge.RESTART : VerticalMerge.CONTINUE;
    }

    /**
     * 표의 칸 너비(px).
     *
     * <p>워드는 너비를 twip(1/20 pt)로 적는다. 화면은 px 로 그리므로 96dpi 로 바꾼다.
     * 너비를 잃으면 모든 칸이 똑같이 나뉘어 원본과 딴판이 된다(2026-08-12 지적).
     */
    private List<Double> columnWidthsOf(XWPFTable table) {
        var grid = table.getCTTbl().getTblGrid();
        if (grid == null) {
            return List.of();
        }
        List<Double> widths = new ArrayList<>();
        for (var column : grid.getGridColList()) {
            Object width = column.getW();
            if (width == null) {
                return List.of();
            }
            widths.add(new java.math.BigDecimal(width.toString()).doubleValue() / TWIP_PER_PX);
        }
        return widths;
    }

    /**
     * 글자와 서식을 쓴다.
     *
     * <p>서식이 없는 자리도 런으로 만든다. 빼면 그 글자가 통째로 사라진다.
     */
    private void writeRuns(XWPFParagraph paragraph, DocParagraphDto row) {
        String text = row.text() == null ? "" : row.text();
        List<Span> spans = spansOf(text.length(), row.runs());

        for (Span span : spans) {
            XWPFRun run = paragraph.createRun();
            writeTabbedText(run, text.substring(span.from, span.to));
            run.setBold(span.bold);
            run.setItalic(span.italic);
            if (span.underline) {
                run.setUnderline(org.apache.poi.xwpf.usermodel.UnderlinePatterns.SINGLE);
            }
            run.setStrikeThrough(span.strike);
            // 크기·글꼴·색을 흘리면 원본이 어떤 글이었는지 알 수 없게 된다
            // (2026-08-24 지적).
            if (span.size > 0) {
                run.setFontSize(span.size);
            }
            if (span.font != null && !span.font.isBlank()) {
                run.setFontFamily(span.font);
            }
            if (span.color != null && span.color.startsWith("#")) {
                run.setColor(span.color.substring(1));
            }
        }
        if (spans.isEmpty()) {
            // 빈 문단도 자리를 지켜야 한다. 버리면 문단 번호가 밀려 뒤쪽 글이
            // 통째로 어긋난다.
            paragraph.createRun().setText("");
        }
    }

    /**
     * 글을 쓰되 탭은 탭으로 적는다.
     *
     * <p>탭을 글자로 적으면 워드가 탭 정지로 보내지 않는다. 목차 줄이 길어져 두
     * 줄이 되고 그 뒤 쪽이 통째로 밀린다(2026-08-24 지적).
     */
    private void writeTabbedText(XWPFRun run, String text) {
        String[] pieces = text.split("	", -1);
        for (int at = 0; at < pieces.length; at++) {
            if (at > 0) {
                run.addTab();
            }
            if (!pieces[at].isEmpty()) {
                run.setText(pieces[at], run.getCTR().sizeOfTArray());
            }
        }
    }

    /**
     * 문단 하나를 읽는다. 안에 줄바꿈이 있으면 여러 줄이 된다.
     *
     * <p>워드는 한 문단 안에서 {@code <w:br/>} 로 줄을 바꾼다. 그것을 흘리면 두
     * 줄이 한 줄로 붙는다(2026-08-20 지적: 표지의 두 줄이 붙어 나왔다). Univer
     * 본문에는 문단 안 줄바꿈 자리가 없으므로 - 줄바꿈 문자는 구역 끝으로 읽힌다 -
     * 문단을 나눈다. 나뉜 줄은 문단 서식을 그대로 물려받는다.
     */
    private List<DocParagraphDto> readParagraphs(XWPFParagraph paragraph, int firstNo) {
        List<DocParagraphDto> lines = new ArrayList<>();
        String paragraphStyle = styleOf(paragraph);
        // 문단 속성으로 적은 쪽 나눔(pageBreakBefore)도 본다. 워드는 이 방식을
        // 즐겨 쓰고, 우리도 내보낼 때 이렇게 적는다(2026-08-25).
        if (ownPageBreak(paragraph)) {
            paragraphStyle = withPageBreak(paragraphStyle);
        }
        // 앞 문단이 쪽 나눔만 들고 있었으면 이 문단이 새 쪽을 연다.
        String lineStyle = pendingPageBreak ? withPageBreak(paragraphStyle) : paragraphStyle;
        pendingPageBreak = false;
        StringBuilder text = new StringBuilder();
        ArrayNode runs = JSON.createArrayNode();
        Inherited inherited = inheritedOf(paragraph);

        // 쪽 번호(PAGE)·전체 쪽수(NUMPAGES) 같은 필드. 워드는 begin - 지시문 -
        // separate - 계산해 둔 값 - end 로 적는다. 그 값만 글자로 담으면 모든
        // 쪽에 같은 숫자가 찍히므로, 어디가 필드였는지 함께 적어 보낸다.
        Field field = new Field();

        for (XWPFRun run : paragraph.getRuns()) {
            field.read(run, text.length(), runs);
            // POI 는 줄바꿈을 글자로도 내준다. 그대로 담으면 Univer 가 구역 끝으로
            // 읽어 문서 얼개가 무너진다 - 자리는 아래에서 문단을 나눠 지킨다.
            String piece = run.text() == null ? null
                    : run.text().replaceAll("[\r\n]", "");
            if (piece != null && !piece.isEmpty()) {
                int from = text.length();
                text.append(piece);

                ObjectNode style = styleOf(run, inherited);
                if (!style.isEmpty()) {
                    ObjectNode entry = JSON.createObjectNode();
                    entry.put("st", from);
                    entry.put("ed", text.length());
                    entry.set("ts", style);
                    runs.add(entry);
                }
            }
            // 줄바꿈은 런의 글자 뒤에 온다. 워드가 내는 모양이 대개 그렇다 -
            // 줄바꿈만 든 런 하나가 두 글자 런 사이에 낀다.
            if (run.getCTR().sizeOfBrArray() > 0) {
                // 줄바꿈만 든 문단은 앞줄을 만들지 않는다. 만들면 쪽 나눔마다 빈
                // 줄이 하나씩 는다.
                if (!text.isEmpty() || !lines.isEmpty()) {
                    lines.add(new DocParagraphDto(firstNo + lines.size(), text.toString(),
                            lines.isEmpty() ? lineStyle : withLineBreak(lineStyle),
                            runs.isEmpty() ? null : runs.toString()));
                }
                text.setLength(0);
                runs = JSON.createArrayNode();
                // 쪽 나눔이면 다음 줄이 새 쪽을 연다. 흘리면 표지 다음 목차가
                // 표지 밑에 붙는다(2026-08-20 실측).
                lineStyle = pageBreaks(run) ? withPageBreak(paragraphStyle) : paragraphStyle;
            }
        }

        // 그림이 걸린 문단은 버리지 않는다. 표지는 글이 도형 안에 있어 문단 글자가
        // 비는데, 그 문단을 버리면 표지 쪽이 통째로 사라진다(2026-08-25 지적).
        boolean onlyPageBreak = text.isEmpty() && runs.isEmpty() && !hasDrawing(paragraph)
                && lineStyle != null && lineStyle.contains("pageBreakBefore");
        if (onlyPageBreak) {
            // 쪽 나눔만 든 문단이다. 줄은 만들지 않고 표시만 다음 문단에 넘긴다 -
            // 만들면 화면에 원본에 없는 빈 줄이 생긴다(2026-08-25 지적). 워드로
            // 내보낼 때는 그 자리를 다시 만들어 준다.
            pendingPageBreak = true;
            return lines;
        }
        lines.add(new DocParagraphDto(firstNo + lines.size(), text.toString(),
                lines.isEmpty() ? lineStyle : withLineBreak(lineStyle),
                runs.isEmpty() ? null : runs.toString()));
        // 글상자(도형) 안 글도 줄로 담는다. 표지 제목이 글상자에 든 문서가 있는데,
        // 흘리면 표지가 그림만 남는다(2026-08-25 지적).
        for (String line : boxedLines(paragraph)) {
            lines.add(new DocParagraphDto(firstNo + lines.size(), line,
                    lines.isEmpty() ? lineStyle : paragraphStyle, null));
        }
        // 구역 나눔으로 끝나는 문단 뒤는 새 쪽이다. 흘리면 표지와 목차가 한 쪽에
        // 겹친다(2026-08-25 지적: 첫 쪽 그림이 글을 덮었다).
        if (endsSection(paragraph)) {
            pendingPageBreak = true;
        }
        return lines;
    }

    /**
     * 글상자(도형) 안에 든 글줄.
     *
     * <p>워드는 도형 글을 {@code w:txbxContent} 안에 담는다. POI 는 그것을 문단
     * 글자로 내주지 않아 XML 을 바로 훑는다. 자리까지는 못 옮기고 줄로만 담는다 -
     * 글이 통째로 사라지는 것보다는 낫다.
     */
    private List<String> boxedLines(XWPFParagraph paragraph) {
        String xml = paragraph.getCTP().xmlText();
        if (!xml.contains("txbxContent")) {
            return List.of();
        }
        // 옛 형식(mc:Fallback, VML)은 건너뛴다. 워드도 새 형식만 그리는데, 둘 다
        // 읽으면 같은 글이 두 번 나온다(2026-08-26 지적: 표지 제목이 두 개).
        xml = xml.replaceAll("(?s)<mc:Fallback>.*?</mc:Fallback>", "");
        List<String> lines = new ArrayList<>();
        var boxes = java.util.regex.Pattern
                .compile("<w:txbxContent>(.*?)</w:txbxContent>", java.util.regex.Pattern.DOTALL)
                .matcher(xml);
        while (boxes.find()) {
            var inside = java.util.regex.Pattern
                    .compile("<w:p[ >].*?</w:p>", java.util.regex.Pattern.DOTALL)
                    .matcher(boxes.group(1));
            while (inside.find()) {
                var text = java.util.regex.Pattern
                        .compile("<w:t[^>]*>(.*?)</w:t>", java.util.regex.Pattern.DOTALL)
                        .matcher(inside.group());
                StringBuilder line = new StringBuilder();
                while (text.find()) {
                    line.append(unescaped(text.group(1)));
                }
                if (!line.toString().isBlank()) {
                    lines.add(line.toString());
                }
            }
        }
        return lines;
    }

    /** XML 에 적힌 글자를 되돌린다. */
    private String unescaped(String text) {
        return text.replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&apos;", "'")
                .replace("&amp;", "&");
    }

    /** 이 문단에 그림이 걸려 있는가. */
    private boolean hasDrawing(XWPFParagraph paragraph) {
        for (XWPFRun run : paragraph.getRuns()) {
            if (!run.getEmbeddedPictures().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** 이 문단이 구역을 닫는가. 워드는 구역 나눔을 문단 속성에 적는다. */
    private boolean endsSection(XWPFParagraph paragraph) {
        var properties = paragraph.getCTP().getPPr();
        return properties != null && properties.getSectPr() != null;
    }

    /**
     * "앞 줄에서 줄만 바꾼 줄" 표시를 얹는다.
     *
     * <p>한 문단 안 줄바꿈을 화면에서는 문단으로 쪼갠다 - Univer 본문에는 문단 안
     * 줄바꿈 자리가 없다. 내보낼 때 되돌리지 않으면 문단이 늘고 문단 뒤 간격이 한
     * 번 더 붙어 쪽이 밀린다(2026-08-24 지적).
     */
    private String withLineBreak(String paragraphStyle) {
        ObjectNode style = paragraphStyle == null ? JSON.createObjectNode()
                : (ObjectNode) JSON.readTree(paragraphStyle);
        // 쪽 나눔은 첫 줄만 연다. 쪼갠 줄이 새 쪽을 열면 안 된다.
        style.remove("pageBreakBefore");
        style.put("lineBreakBefore", 1);
        return style.toString();
    }

    /** 앞 줄에 줄만 바꿔 이어지는 줄인가. */
    private boolean joinsPreviousLine(String style) {
        return read(style).path("lineBreakBefore").asInt(0) == 1;
    }

    /** 이 문단이 새 쪽에서 시작하는가. 문단 서식에 적어 둔 표시를 본다. */
    private boolean opensNewPage(String style) {
        if (style == null || !style.contains("pageBreakBefore")) {
            return false;
        }
        var node = JSON.readTree(style).get("pageBreakBefore");
        return node != null && node.asInt() == 1;
    }

    /** 이 런에 쪽 나눔이 있는가. {@code <w:br w:type="page"/>} 다. */
    /**
     * 붙이기 규칙을 켰는가. 문단이 안 적었으면 쓰는 서식을 본다.
     *
     * @param next true 면 "다음 문단과 붙이기", false 면 "이 문단을 쪼개지 않기"
     */
    private boolean keeps(XWPFParagraph paragraph, boolean next) {
        var properties = paragraph.getCTP().getPPr();
        var own = properties == null ? null
                : next ? properties.getKeepNext() : properties.getKeepLines();
        if (own != null) {
            return own.getVal() == null || Boolean.parseBoolean(own.getVal().toString())
                    || "on".equals(own.getVal().toString())
                    || "1".equals(own.getVal().toString());
        }
        return styleKeeps(paragraph, next);
    }

    /** 서식 사슬에서 붙이기 규칙을 찾는다. */
    private boolean styleKeeps(XWPFParagraph paragraph, boolean next) {
        if (paragraph.getDocument() == null || paragraph.getDocument().getStyles() == null) {
            return false;
        }
        var styles = paragraph.getDocument().getStyles();
        String styleId = paragraph.getStyleID() == null
                ? defaultStyleIdOf(styles) : paragraph.getStyleID();
        for (int step = 0; styleId != null && step < 10; step++) {
            var style = styles.getStyle(styleId);
            if (style == null) {
                return false;
            }
            var properties = style.getCTStyle().getPPr();
            var found = properties == null ? null
                    : next ? properties.getKeepNext() : properties.getKeepLines();
            if (found != null) {
                return found.getVal() == null
                        || !"0".equals(found.getVal().toString())
                        && !"off".equals(found.getVal().toString());
            }
            var basedOn = style.getCTStyle().getBasedOn();
            styleId = basedOn == null ? null : basedOn.getVal();
        }
        return false;
    }

    /** 문단이 제 속성으로 "새 쪽에서 시작" 을 적었는가. */
    private boolean ownPageBreak(XWPFParagraph paragraph) {
        var properties = paragraph.getCTP().getPPr();
        return properties != null && properties.isSetPageBreakBefore();
    }

    private boolean pageBreaks(XWPFRun run) {
        for (var br : run.getCTR().getBrList()) {
            if (br.getType() != null && "page".equals(br.getType().toString())) {
                return true;
            }
        }
        return false;
    }

    /** 문단 서식에 "새 쪽에서 시작" 을 얹는다. 서식이 없으면 그것만 적는다. */
    private String withPageBreak(String paragraphStyle) {
        ObjectNode style = paragraphStyle == null ? JSON.createObjectNode()
                : (ObjectNode) JSON.readTree(paragraphStyle);
        style.put("pageBreakBefore", 1);
        return style.toString();
    }

    /** 문단에 적힌 값이 먼저다. 없으면 서식에 적힌 값을 쓴다. */
    private Object pick(Object own, Object fromStyle) {
        return own != null ? own : fromStyle;
    }

    /** 오른쪽 탭 정지 하나. 자리는 px, 채움은 워드가 적은 이름 그대로다. */
    private record TabStop(double at, String leader) {
    }

    /**
     * 문단의 오른쪽 탭 정지.
     *
     * <p>문단에 적힌 것이 먼저고, 없으면 쓰는 서식에서 찾는다(물려받는 서식까지).
     * 워드 목차가 그렇게 짜여 있다 - toc 1 은 스타일에, toc 2 는 문단에 적는다.
     */
    private TabStop rightTabOf(XWPFParagraph paragraph) {
        var own = paragraph.getCTP().getPPr() == null
                ? null : paragraph.getCTP().getPPr().getTabs();
        TabStop found = rightTabIn(own);
        if (found != null) {
            return found;
        }
        if (paragraph.getDocument() == null || paragraph.getDocument().getStyles() == null) {
            return null;
        }
        var styles = paragraph.getDocument().getStyles();
        // 서식을 안 적은 문단은 기본 서식(Normal)을 쓴다. 그것을 안 보면 문서
        // 기본값으로 떨어져 간격이 커진다 - 24쪽이 28쪽이 됐다(2026-08-24 지적).
        String styleId = paragraph.getStyleID() == null
                ? defaultStyleIdOf(styles) : paragraph.getStyleID();
        for (int step = 0; styleId != null && step < 10; step++) {
            var style = styles.getStyle(styleId);
            if (style == null) {
                return null;
            }
            var properties = style.getCTStyle().getPPr();
            found = rightTabIn(properties == null ? null : properties.getTabs());
            if (found != null) {
                return found;
            }
            var basedOn = style.getCTStyle().getBasedOn();
            styleId = basedOn == null ? null : basedOn.getVal();
        }
        return null;
    }

    private TabStop rightTabIn(
            org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTabs tabs) {
        if (tabs == null) {
            return null;
        }
        for (var tab : tabs.getTabList()) {
            if (tab.getVal() != org.openxmlformats.schemas.wordprocessingml
                    .x2006.main.STTabJc.RIGHT || tab.getPos() == null) {
                continue;
            }
            double at = new java.math.BigDecimal(tab.getPos().toString())
                    .doubleValue() / TWIP_PER_PX;
            String leader = tab.getLeader() == null ? null : tab.getLeader().toString();
            return new TabStop(at, "none".equals(leader) ? null : leader);
        }
        return null;
    }

    /** 문단이 쓰는 서식의 들여쓰기. 물려받는 서식까지 거슬러 올라간다. */
    private org.openxmlformats.schemas.wordprocessingml.x2006.main.CTInd
            styleIndent(XWPFParagraph paragraph) {
        if (paragraph.getDocument() == null || paragraph.getDocument().getStyles() == null) {
            return null;
        }
        var styles = paragraph.getDocument().getStyles();
        String styleId = paragraph.getStyleID();
        for (int step = 0; styleId != null && step < 10; step++) {
            var style = styles.getStyle(styleId);
            if (style == null) {
                return null;
            }
            var properties = style.getCTStyle().getPPr();
            if (properties != null && properties.getInd() != null) {
                return properties.getInd();
            }
            var basedOn = style.getCTStyle().getBasedOn();
            styleId = basedOn == null ? null : basedOn.getVal();
        }
        return null;
    }

    /** 들여쓰기 한 자리를 px 로 적는다. 없거나 0 이면 적지 않는다. */
    private void putIndent(ObjectNode style, String name, Object twip) {
        if (twip == null) {
            return;
        }
        double px = new java.math.BigDecimal(twip.toString()).doubleValue() / TWIP_PER_PX;
        if (px <= 0) {
            return;
        }
        style.set(name, JSON.createObjectNode().put("v", px));
    }

    /**
     * 이 문서의 기본 글자 모양.
     *
     * <p>워드는 문서 전체 기본을 styles.xml 의 {@code docDefaults} 에만 적는다.
     * 그것을 안 읽으면 표 안 글자가 화면 기본 크기로 나와 원본보다 크다
     * (2026-08-13 지적: 글자 크기가 많이 차이난다).
     *
     * <p>한 번에 문서 하나만 읽으므로 읽는 동안만 들고 있는다.
     */
    private Inherited documentDefault = Inherited.NONE;

    /**
     * 바로 앞 문단. 같은 서식끼리 붙여 그릴지(contextualSpacing) 판단하려면
     * 앞뒤를 봐야 한다. 표를 지나면 이음이 끊기므로 비운다.
     */
    private XWPFParagraph previousParagraph;

    /**
     * 앞 문단이 쪽 나눔만 들고 있었는가.
     *
     * <p>워드는 쪽 나눔만 든 문단을 흔히 쓴다. 그 문단으로 빈 줄을 만들면 그 줄이
     * 새 쪽 첫 줄로 올라가 원본에 없는 빈 자리가 생긴다(2026-08-21 지적: 목차 위에
     * 없던 줄이 들어갔다). 줄은 만들지 않고 표시만 다음 문단에 넘긴다.
     */
    private boolean pendingPageBreak;

    /**
     * 문서 기본 문단 간격(docDefaults).
     *
     * <p>워드는 문단 간격을 문단마다 안 적고 문서 기본에만 적는다. 그것을 안 보면
     * 빈 줄이 원본보다 낮아 쪽 나눔이 어긋난다 - 다음 쪽 표가 위에 안 붙는다
     * (2026-08-13 지적).
     */
    private Spacing defaultSpacing = Spacing.NONE;

    /** 문단 기본 서식 이름. 한 문서를 읽는 동안 한 번만 찾는다. */
    private String defaultStyleId;

    /**
     * 문단 간격 한 벌(pt, 줄 수).
     *
     * @param below 문단 뒤 간격(pt). 없으면 null.
     * @param lines 줄 간격(줄 수). 없으면 null.
     */
    private record Spacing(Double below, Double lines) {
        static final Spacing NONE = new Spacing(null, null);
    }

    /** styles.xml 의 문서 기본 글자 모양. */
    private Inherited defaultsOf(XWPFDocument document) {
        var styles = document.getStyles();
        if (styles == null) {
            return Inherited.NONE;
        }
        var run = styles.getDefaultRunStyle();
        if (run == null) {
            return Inherited.NONE;
        }
        return new Inherited(run.getFontSizeAsDouble(), null, null);
    }

    /** 글자 한 토막의 서식. 문단에도 표 칸에도 같은 셈을 쓴다. */
    private ObjectNode styleOf(XWPFRun run, Inherited inherited) {
        ObjectNode style = JSON.createObjectNode();
        if (run.isBold()) style.put("bl", 1);
        if (run.isItalic()) style.put("it", 1);
        if (run.getUnderline() != org.apache.poi.xwpf.usermodel.UnderlinePatterns.NONE) {
            style.set("ul", JSON.createObjectNode().put("s", 1));
        }
        if (run.isStrikeThrough()) {
            style.set("st", JSON.createObjectNode().put("s", 1));
        }
        applyLook(style, run, inherited);
        return style;
    }

    /**
     * 글자 크기·글꼴·색을 담는다.
     *
     * <p>이것들이 빠지면 제목이 본문과 같은 크기·검정으로 나와 원본과 딴판이다
     * (2026-08-12 지적: 워드 파일을 열었더니 차이가 많다).
     *
     * <p>런에 적힌 것이 먼저고, 없으면 문단 스타일(Heading 1 따위)에 적힌 것을 쓴다.
     * 워드 제목은 크기를 런에 적지 않고 스타일에만 적는 일이 흔하다.
     */
    private void applyLook(ObjectNode style, XWPFRun run, Inherited inherited) {
        Double size = run.getFontSizeAsDouble();
        if (size == null) {
            size = inherited.size();
        }
        if (size != null && size > 0) {
            style.put("fs", size);
        }

        String font = run.getFontFamily();
        if (font == null || font.isBlank()) {
            font = inherited.font();
        }
        if (font != null && !font.isBlank()) {
            style.put("ff", font);
        }

        String color = rgbOf(run.getColor());
        if (color == null) {
            color = rgbOf(inherited.color());
        }
        if (color != null) {
            style.set("cl", JSON.createObjectNode().put("rgb", color));
        }
    }

    /**
     * 워드 색({@code FF0000})을 화면 색({@code #ff0000})으로.
     *
     * <p>{@code auto} 는 색이 아니라 "알아서" 라는 뜻이다. 그대로 옮기면 검정이
     * 강제로 박혀 어두운 화면에서 글이 안 보인다.
     */
    private String rgbOf(String color) {
        if (color == null || color.isBlank() || "auto".equalsIgnoreCase(color)) {
            return null;
        }
        return "#" + color.toLowerCase(java.util.Locale.ROOT);
    }

    /** 문단 스타일에서 물려받는 글자 모양. */
    private record Inherited(Double size, String font, String color) {

        static final Inherited NONE = new Inherited(null, null, null);
    }

    /**
     * 문단 스타일(styles.xml)에 적힌 글자 모양을 읽는다.
     *
     * <p>물려받기(basedOn)는 한 단계만 따라간다. 끝까지 따라가려면 워드의 스타일
     * 셈을 통째로 옮겨야 하는데, 그 셈이 틀리면 반만 옮긴 서식이 되어 안 옮긴
     * 것보다 더 어긋나 보인다.
     */
    private Inherited inheritedOf(XWPFParagraph paragraph) {
        String styleId = paragraph.getStyleID();
        if (styleId == null || paragraph.getDocument() == null
                || paragraph.getDocument().getStyles() == null) {
            return documentDefault;
        }
        var style = paragraph.getDocument().getStyles().getStyle(styleId);
        if (style == null) {
            return documentDefault;
        }
        var properties = style.getCTStyle().getRPr();
        if (properties == null) {
            return documentDefault;
        }

        Double size = null;
        if (properties.sizeOfSzArray() > 0 && properties.getSzArray(0).getVal() != null) {
            // 워드는 크기를 반 포인트로 적는다.
            size = new java.math.BigDecimal(properties.getSzArray(0).getVal().toString())
                    .doubleValue() / 2;
        }
        String font = null;
        if (properties.sizeOfRFontsArray() > 0) {
            font = properties.getRFontsArray(0).getAscii();
        }
        String color = null;
        if (properties.sizeOfColorArray() > 0) {
            color = properties.getColorArray(0).getVal() instanceof byte[] bytes
                    ? org.apache.commons.codec.binary.Hex.encodeHexString(bytes)
                    : String.valueOf(properties.getColorArray(0).getVal());
        }
        return new Inherited(size != null ? size : documentDefault.size(),
                font != null ? font : documentDefault.font(),
                color != null ? color : documentDefault.color());
    }

    /** 문서 기본 문단 간격. 적혀 있지 않으면 아무것도 없다. */
    private Spacing spacingOf(XWPFDocument document) {
        var styles = document.getStyles();
        if (styles == null) {
            return Spacing.NONE;
        }
        // POI 는 문서 기본 문단 서식을 반쪽만 열어 준다(뒤 간격만). 줄 간격까지
        // 보려면 styles.xml 을 직접 읽어야 한다.
        org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSpacing spacing;
        try (var in = styles.getPackagePart().getInputStream()) {
            var parsed = org.openxmlformats.schemas.wordprocessingml.x2006.main
                    .StylesDocument.Factory.parse(in).getStyles();
            var defaults = parsed == null ? null : parsed.getDocDefaults();
            var paragraph = defaults == null ? null : defaults.getPPrDefault();
            spacing = paragraph == null || paragraph.getPPr() == null
                    ? null : paragraph.getPPr().getSpacing();
        } catch (Exception cannotRead) {
            // 기본 간격을 못 읽어도 문서는 연다. 줄 높이만 우리 기본이 된다.
            return Spacing.NONE;
        }
        if (spacing == null) {
            return Spacing.NONE;
        }
        Double below = spacing.getAfter() == null ? null
                : new java.math.BigDecimal(spacing.getAfter().toString())
                        .doubleValue() / TWIP_PER_PT;
        // 워드는 줄 간격을 240 이 한 줄인 값으로 적는다. 259 가 1.08줄이다.
        Double lines = spacing.getLine() == null
                || spacing.getLineRule() == org.openxmlformats.schemas.wordprocessingml
                        .x2006.main.STLineSpacingRule.EXACT
                ? null
                : new java.math.BigDecimal(spacing.getLine().toString())
                        .doubleValue() / 240;
        return new Spacing(below, lines);
    }

    /**
     * 문단 서식을 우리 모양으로. 왼쪽 맞춤은 기본이라 적지 않는다.
     *
     * <p>문단 사이 간격도 담는다. 빠지면 문단이 다닥다닥 붙어 원본보다 짧아 보이고
     * 쪽 수도 달라진다.
     */
    private String styleOf(XWPFParagraph paragraph) {
        Integer align = switch (paragraph.getAlignment()) {
            case CENTER -> ALIGN_CENTER;
            case RIGHT -> ALIGN_RIGHT;
            case BOTH -> ALIGN_JUSTIFY;
            default -> null;
        };
        ObjectNode paragraphStyle = JSON.createObjectNode();
        if (align != null) {
            paragraphStyle.put("horizontalAlign", align);
        }
        boolean joined = joinsNeighbours(paragraph)
                && previousParagraph != null && sameStyle(previousParagraph, paragraph);
        Double styleAbove = styleSpacingBefore(paragraph);
        if (!joined && paragraph.getSpacingBefore() > 0) {
            paragraphStyle.set("spaceAbove", JSON.createObjectNode()
                    .put("v", paragraph.getSpacingBefore() / TWIP_PER_PT * PX_PER_PT));
        } else if (!joined && styleAbove != null) {
            // 문단이 제 몸에 안 적었으면 쓰는 서식이 정답이다. 워드 제목 스타일은
            // 앞 간격을 크게 잡는데(Heading 1 은 18pt), 흘리면 제목이 앞 글에
            // 붙고 쪽이 원본보다 덜 찬다(2026-08-21 지적).
            paragraphStyle.set("spaceAbove", JSON.createObjectNode()
                    .put("v", styleAbove * PX_PER_PT));
        }
        if (joined) {
            // 워드는 같은 서식 문단끼리 붙여 그린다(contextualSpacing). 간격을 넣으면
            // 목록 하나마다 한 줄씩 벌어져 뒤쪽 글이 다음 쪽으로 밀린다.
            paragraphStyle.remove("spaceAbove");
        }
        Double styleBelow = styleSpacingAfter(paragraph);
        // POI 는 문단이 제 몸에 안 적어도 문서 기본값을 준다. 그것을 문단 값으로
        // 믿으면 기본 서식(Normal)의 좁은 간격을 덮어써 쪽이 늘어난다
        // (2026-08-24 지적: 24쪽이 28쪽이 됐다).
        Integer ownBelow = ownSpacingAfter(paragraph);
        if (ownBelow != null && ownBelow > 0) {
            paragraphStyle.set("spaceBelow", JSON.createObjectNode()
                    .put("v", ownBelow / TWIP_PER_PT * PX_PER_PT));
        } else if (styleBelow != null) {
            // 문단이 제 몸에 안 적었으면 쓰는 서식이 정답이다. 목차 스타일은 뒤
            // 간격을 문서 기본보다 좁게 잡는데, 그것을 흘리면 줄마다 벌어져 한
            // 쪽에 들어가던 목차가 두 쪽이 된다(2026-08-21 지적).
            paragraphStyle.set("spaceBelow", JSON.createObjectNode()
                    .put("v", styleBelow * PX_PER_PT));
        } else if (ownBelow == null && defaultSpacing.below() != null) {
            // 어디에도 안 적혔다. 문서 기본을 그대로 쓴다.
            paragraphStyle.set("spaceBelow", JSON.createObjectNode()
                    .put("v", defaultSpacing.below() * PX_PER_PT));
        }
        // 들여쓰기. 번호 매긴 목록과 인용은 이것으로 층을 나타낸다 - 빠지면 모두
        // 왼쪽에 붙어 층이 사라진다. 워드는 twip 으로 적고 화면은 px 로 그린다.
        var indent = paragraph.getCTP().getPPr() == null
                ? null : paragraph.getCTP().getPPr().getInd();
        // 문단이 제 몸에 안 적었으면 쓰는 서식에 적힌 것이다. 목차 아래 단계
        // (toc 2)가 그렇게 짜여 있다 - 흘리면 1.1 이 1 과 같은 자리에 붙어 층이
        // 사라진다(2026-08-21 지적).
        var styleIndent = styleIndent(paragraph);
        if (indent != null || styleIndent != null) {
            putIndent(paragraphStyle, "indentStart",
                    pick(indent == null ? null : indent.getLeft(),
                            styleIndent == null ? null : styleIndent.getLeft()));
            putIndent(paragraphStyle, "indentEnd",
                    pick(indent == null ? null : indent.getRight(),
                            styleIndent == null ? null : styleIndent.getRight()));
            putIndent(paragraphStyle, "indentFirstLine",
                    pick(indent == null ? null : indent.getFirstLine(),
                            styleIndent == null ? null : styleIndent.getFirstLine()));
            putIndent(paragraphStyle, "hanging",
                    pick(indent == null ? null : indent.getHanging(),
                            styleIndent == null ? null : styleIndent.getHanging()));
        }
        // 줄 간격은 줄 수로 적는다. 워드 기본은 1.08 줄이라 1.0 으로 그리면 쪽마다
        // 몇 줄씩 더 들어가 원본과 쪽 나눔이 달라진다.
        double lines = paragraph.getSpacingBetween();
        Double fromStyle = styleLineSpacing(paragraph);
        if (fromStyle != null) {
            // 문단이 제 몸에 안 적었으면 쓰는 서식이 정답이다. POI 는 그 자리를
            // 안 보고 문서 기본을 준다 - 목록이 한 줄씩 높아졌다(2026-08-19 지적).
            var own = paragraph.getCTP().getPPr() == null
                    ? null : paragraph.getCTP().getPPr().getSpacing();
            if (own == null || !own.isSetLine()) {
                lines = fromStyle;
            }
        }
        if (lines <= 0 && defaultSpacing.lines() != null) {
            lines = defaultSpacing.lines();
        }
        if (lines > 0 && paragraph.getSpacingLineRule()
                != org.apache.poi.xwpf.usermodel.LineSpacingRule.EXACT) {
            paragraphStyle.put("lineSpacing",
                    lines * fontSizeOf(paragraph) * PX_PER_PT * FONT_LINE_HEIGHT);
            // 배수도 그대로 담는다. 픽셀 높이만 담으면 내보낼 때 글자 크기로
            // 되나눠야 하는데, 제목처럼 크기가 다르면 배수가 틀어진다
            // (2026-08-25 실측: 20pt 제목 줄 간격이 0.5 배가 됐다).
            paragraphStyle.put("lineMultiple", Math.round(lines * 1000) / 1000.0);
            // 줄 높이를 어떻게 셀지도 함께 적는다. 규칙이 없으면 화면이 줄 간격을
            // 어떻게 읽어야 할지 몰라 문서를 통째로 못 그린다(2026-08-12 실측).
            paragraphStyle.put("spacingRule", SPACING_EXACT);
        }
        // 제목 서식은 "다음 문단과 붙이기" 를 갖는다. 흘리면 제목이 쪽 끝에 홀로
        // 남고 뒤 내용만 넘어가 원본보다 쪽이 덜 든다(2026-08-25 실측).
        if (keeps(paragraph, true)) {
            paragraphStyle.put("keepNext", 1);
        }
        if (keeps(paragraph, false)) {
            paragraphStyle.put("keepLines", 1);
        }
        ObjectNode bullet = bulletOf(paragraph);
        var stop = rightTabOf(paragraph);
        if (paragraphStyle.isEmpty() && bullet == null && stop == null) {
            return null;
        }
        ObjectNode style = JSON.createObjectNode();
        if (stop != null) {
            // 목차는 제목과 쪽 번호 사이를 점선으로 잇고 번호를 오른쪽 끝에 세운다.
            // 화면이 그리려면 탭이 멈추는 자리를 알아야 한다. Univer 는 탭 정지를
            // 모르므로(고정 폭 한 가지뿐이다) 화면이 이 값을 보고 손수 채운다.
            style.put("tabRight", stop.at());
            if (stop.leader() != null) {
                style.put("tabLeader", stop.leader());
            }
        }
        if (!paragraphStyle.isEmpty()) {
            style.set("paragraphStyle", paragraphStyle);
        }
        if (bullet != null) {
            style.set("bullet", bullet);
        }
        return style.toString();
    }

    /** 문단 글자 크기(pt). 첫 조각을 따르고, 없으면 문서 기본이다. */
    private double fontSizeOf(XWPFParagraph paragraph) {
        // 가장 큰 조각을 따른다. 줄 높이를 "정확히" 로 주므로 작은 쪽에 맞추면
        // 큰 글자가 잘린다.
        double largest = 0;
        for (var run : paragraph.getRuns()) {
            Double size = run.getFontSizeAsDouble();
            if (size != null && size > largest) {
                largest = size;
            }
        }
        if (largest > 0) {
            return largest;
        }
        // 런에 안 적혔으면 문단이 쓰는 서식에 적힌 크기다. 그것마저 없으면 문서
        // 기본이다. 이 자리를 건너뛰면 9pt 짜리 목차 줄이 11pt 높이로 그려진다
        // (2026-08-21 지적).
        Double fromStyle = inheritedOf(paragraph).size();
        if (fromStyle != null) {
            return fromStyle;
        }
        return documentDefault.size() != null ? documentDefault.size() : DEFAULT_FONT_SIZE;
    }

    /** 두 문단이 같은 서식을 쓰는가. 둘 다 이름이 없으면(기본 서식) 같은 것이다. */
    private boolean sameStyle(XWPFParagraph one, XWPFParagraph other) {
        return java.util.Objects.equals(one.getStyleID(), other.getStyleID());
    }

    /** 아래 간격만 뗀 문단. 뒤에 같은 서식이 이어질 때 쓴다. */
    private DocParagraphDto withoutSpaceBelow(DocParagraphDto row) {
        if (row.style() == null || !row.style().contains("spaceBelow")) {
            return row;
        }
        JsonNode parsed = read(row.style());
        JsonNode paragraphStyle = parsed.path("paragraphStyle");
        if (!paragraphStyle.isObject()) {
            return row;
        }
        ((ObjectNode) paragraphStyle).remove("spaceBelow");
        return new DocParagraphDto(row.paragraphNo(), row.text(), parsed.toString(), row.runs());
    }

    /** 문단이 쓰는 서식에 적힌 줄 간격. 없으면 null. */
    /**
     * 문단이 쓰는 서식에 적힌 뒤 간격(pt).
     *
     * <p>물려받는 서식(basedOn)까지 거슬러 올라간다. 워드 목차 스타일이 그렇게
     * 짜여 있다 - toc 1 은 Normal 을 물려받고 뒤 간격만 다시 적는다.
     */
    private Double styleSpacingAfter(XWPFParagraph paragraph) {
        var value = styleValue(paragraph,
                spacing -> spacing.isSetAfter() ? spacing.getAfter() : null);
        return value == null ? null : value.doubleValue() / TWIP_PER_PT;
    }

    /** 문단이 제 몸에 적은 목록 번호. 안 적었으면 null 이다. */
    private java.math.BigInteger ownNumId(XWPFParagraph paragraph) {
        var properties = paragraph.getCTP().getPPr();
        var numbering = properties == null ? null : properties.getNumPr();
        if (numbering == null || numbering.getNumId() == null
                || numbering.getNumId().getVal() == null) {
            return null;
        }
        return new java.math.BigDecimal(
                numbering.getNumId().getVal().toString()).toBigInteger();
    }

    /** 문단이 쓰는 서식에 적힌 목록 번호. 물려받는 서식까지 거슬러 올라간다. */
    private java.math.BigInteger styleNumId(XWPFParagraph paragraph) {
        if (paragraph.getDocument() == null || paragraph.getDocument().getStyles() == null) {
            return null;
        }
        var styles = paragraph.getDocument().getStyles();
        String styleId = paragraph.getStyleID();
        for (int step = 0; styleId != null && step < 10; step++) {
            var style = styles.getStyle(styleId);
            if (style == null) {
                return null;
            }
            var properties = style.getCTStyle().getPPr();
            var numbering = properties == null ? null : properties.getNumPr();
            if (numbering != null && numbering.getNumId() != null) {
                return new java.math.BigDecimal(
                        numbering.getNumId().getVal().toString()).toBigInteger();
            }
            var basedOn = style.getCTStyle().getBasedOn();
            styleId = basedOn == null ? null : basedOn.getVal();
        }
        return null;
    }

    /** 문단이 쓰는 서식에 적힌 앞 간격(pt). 물려받는 서식까지 거슬러 올라간다. */
    private Double styleSpacingBefore(XWPFParagraph paragraph) {
        var value = styleValue(paragraph,
                spacing -> spacing.isSetBefore() ? spacing.getBefore() : null);
        return value == null ? null : value.doubleValue() / TWIP_PER_PT;
    }

    /** 문단이 쓰는 서식의 간격 설정. 물려받는 서식까지 거슬러 올라간다. */
    private org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSpacing
            styleSpacing(XWPFParagraph paragraph) {
        if (paragraph.getDocument() == null || paragraph.getDocument().getStyles() == null) {
            return null;
        }
        var styles = paragraph.getDocument().getStyles();
        String styleId = paragraph.getStyleID();
        // 거슬러 오르다 서식이 저를 가리키면 끝없이 돈다. 열 번이면 넉넉하다.
        for (int step = 0; styleId != null && step < 10; step++) {
            var style = styles.getStyle(styleId);
            if (style == null) {
                return null;
            }
            var properties = style.getCTStyle().getPPr();
            if (properties != null && properties.getSpacing() != null) {
                return properties.getSpacing();
            }
            var basedOn = style.getCTStyle().getBasedOn();
            styleId = basedOn == null ? null : basedOn.getVal();
        }
        return null;
    }

    /** 문단이 제 몸에 적은 뒤 간격(트윕). 안 적었으면 null 이다. */
    private Integer ownSpacingAfter(XWPFParagraph paragraph) {
        var properties = paragraph.getCTP().getPPr();
        var spacing = properties == null ? null : properties.getSpacing();
        if (spacing == null || !spacing.isSetAfter()) {
            return null;
        }
        return new java.math.BigDecimal(spacing.getAfter().toString()).intValue();
    }

    /** 문단 기본 서식의 이름. 워드는 {@code w:default="1"} 로 적어 둔다. */
    private String defaultStyleIdOf(org.apache.poi.xwpf.usermodel.XWPFStyles styles) {
        if (defaultStyleId != null) {
            return defaultStyleId.isBlank() ? null : defaultStyleId;
        }
        defaultStyleId = "";
        try (var in = styles.getPackagePart().getInputStream()) {
            var parsed = org.openxmlformats.schemas.wordprocessingml.x2006.main
                    .StylesDocument.Factory.parse(in).getStyles();
            for (var one : parsed == null ? new org.openxmlformats.schemas
                    .wordprocessingml.x2006.main.CTStyle[0] : parsed.getStyleArray()) {
                if (one.isSetDefault() && Boolean.TRUE.equals(one.getDefault())
                        && one.getType() == org.openxmlformats.schemas.wordprocessingml
                                .x2006.main.STStyleType.PARAGRAPH) {
                    defaultStyleId = one.getStyleId();
                    break;
                }
            }
        } catch (Exception cannotRead) {
            // 기본 서식을 못 읽어도 문서는 연다. 문서 기본값으로 떨어진다.
            defaultStyleId = "";
        }
        return defaultStyleId.isBlank() ? null : defaultStyleId;
    }

    private Double styleLineSpacing(XWPFParagraph paragraph) {
        // XmlBeans 는 안 적힌 속성에 null 이 아니라 0 을 준다. 적혀 있는지 먼저
        // 본다 - 0 을 값으로 믿어 줄 간격이 문서 기본값으로 떨어졌다(2026-08-24 실측).
        var value = styleValue(paragraph, spacing -> !spacing.isSetLine()
                || spacing.getLineRule() == org.openxmlformats.schemas.wordprocessingml
                        .x2006.main.STLineSpacingRule.EXACT ? null : spacing.getLine());
        // 워드는 줄 간격을 240 이 한 줄인 값으로 적는다.
        return value == null ? null : value.doubleValue() / 240;
    }

    /**
     * 서식 사슬에서 이 값을 적어 둔 첫 서식의 값.
     *
     * <p>값마다 따로 거슬러 오른다. "간격을 적은 첫 서식" 에서 멈추면, 뒤 간격만
     * 적은 서식(목차)이 줄 간격까지 가로막아 문서 기본값으로 떨어진다 - 온 문서가
     * 6% 성겨져 24쪽이 28쪽이 됐다(2026-08-24 지적).
     */
    private java.math.BigDecimal styleValue(XWPFParagraph paragraph,
            java.util.function.Function<org.openxmlformats.schemas.wordprocessingml
                    .x2006.main.CTSpacing, Object> pick) {
        if (paragraph.getDocument() == null || paragraph.getDocument().getStyles() == null) {
            return null;
        }
        var styles = paragraph.getDocument().getStyles();
        // 서식을 안 적은 문단은 기본 서식(Normal)을 쓴다.
        String styleId = paragraph.getStyleID() == null
                ? defaultStyleIdOf(styles) : paragraph.getStyleID();
        // 거슬러 오르다 서식이 저를 가리키면 끝없이 돈다. 열 번이면 넉넉하다.
        for (int step = 0; styleId != null && step < 10; step++) {
            var style = styles.getStyle(styleId);
            if (style == null) {
                return null;
            }
            var properties = style.getCTStyle().getPPr();
            var spacing = properties == null ? null : properties.getSpacing();
            Object value = spacing == null ? null : pick.apply(spacing);
            if (value != null) {
                return new java.math.BigDecimal(value.toString());
            }
            var basedOn = style.getCTStyle().getBasedOn();
            String next = basedOn == null ? null : basedOn.getVal();
            // 기본 서식까지 올라왔으면 더 갈 곳이 없다.
            styleId = next == null && !styleId.equals(defaultStyleIdOf(styles))
                    ? defaultStyleIdOf(styles) : next;
        }
        return null;
    }

    /**
     * 같은 서식 문단끼리 붙여 그리는지(contextualSpacing).
     *
     * <p>문단에 적혀 있거나 문단이 쓰는 스타일에 적혀 있으면 그렇다. 워드는 이때
     * 앞뒤 문단이 같은 스타일이면 간격을 지운다 - 목록이 대표적이다.
     */
    private boolean joinsNeighbours(XWPFParagraph paragraph) {
        var properties = paragraph.getCTP().getPPr();
        if (properties != null && properties.isSetContextualSpacing()) {
            return true;
        }
        String styleId = paragraph.getStyleID();
        if (styleId == null || paragraph.getDocument() == null
                || paragraph.getDocument().getStyles() == null) {
            return false;
        }
        var style = paragraph.getDocument().getStyles().getStyle(styleId);
        return style != null && style.getCTStyle().getPPr() != null
                && style.getCTStyle().getPPr().isSetContextualSpacing();
    }

    /**
     * 글머리표·번호 매기기를 우리 모양으로.
     *
     * <p>표시가 빠지면 목록이 그냥 줄로 늘어서 층이 사라지고, 들여쓰기가 없어
     * 원본보다 넓게 퍼진다(2026-08-19 지적: 3쪽이 원본과 많이 달랐다).
     *
     * <p>화면은 미리 정해 둔 목록 모양(BULLET_LIST·ORDER_LIST)을 안다. 워드의
     * 목록 번호(numId)를 그대로 이름으로 삼아 같은 목록끼리 이어지게 한다.
     */
    private ObjectNode bulletOf(XWPFParagraph paragraph) {
        // 문단이 제 몸에 적은 값이 먼저다. 0 이면 "목록에서 뺐다" 는 뜻이므로 거기서
        // 끝이다 - 서식을 마저 보면 뺀 문단에 글머리표가 되살아나 빈 줄에 점
        // 하나가 덩그러니 남는다(2026-08-21 지적). POI 의 getNumID() 는 0 과
        // 안 적은 것을 똑같이 null 로 주므로 직접 본다.
        java.math.BigInteger numId = ownNumId(paragraph);
        if (numId == null) {
            // 워드 목록 스타일(List Bullet)은 목록 번호를 스타일에 적는다. 문단만
            // 보면 글머리표가 통째로 사라진다(2026-08-21 지적).
            numId = styleNumId(paragraph);
        }
        if (numId == null || numId.signum() == 0) {
            return null;
        }
        java.math.BigInteger level = paragraph.getNumIlvl();
        ObjectNode bullet = JSON.createObjectNode();
        bullet.put("listType", numbered(paragraph) ? "ORDER_LIST" : "BULLET_LIST");
        bullet.put("listId", "wo-list-" + numId);
        bullet.put("nestingLevel", level == null ? 0 : level.intValue());
        return bullet;
    }

    /** 번호 매기기인가. 목록 규칙을 못 읽으면 글머리표로 본다. */
    private boolean numbered(XWPFParagraph paragraph) {
        try {
            String format = paragraph.getNumFmt();
            return format != null && !"bullet".equalsIgnoreCase(format)
                    && !"none".equalsIgnoreCase(format);
        } catch (RuntimeException error) {
            return false;
        }
    }

    /**
     * 줄 간격과 문단 앞뒤 간격.
     *
     * <p>화면은 픽셀로 셈하고 워드는 트윕으로 적는다. 흘리면 성긴 글이 내려받을 때
     * 빽빽해진다(2026-08-24 지적).
     */
    private void applySpacing(XWPFParagraph paragraph, DocParagraphDto row) {
        String style = row.style();
        JsonNode paragraphStyle = read(style).path("paragraphStyle");
        double above = paragraphStyle.path("spaceAbove").path("v").asDouble(-1);
        if (above >= 0) {
            paragraph.setSpacingBefore(twipOf(above));
        }
        double below = paragraphStyle.path("spaceBelow").path("v").asDouble(-1);
        if (below >= 0) {
            paragraph.setSpacingAfter(twipOf(below));
        }
        double multiple = paragraphStyle.path("lineMultiple").asDouble(0);
        if (multiple > 0) {
            paragraph.setSpacingBetween(multiple,
                    org.apache.poi.xwpf.usermodel.LineSpacingRule.AUTO);
            return;
        }
        double lines = paragraphStyle.path("lineSpacing").asDouble(0);
        if (lines > 0 && paragraphStyle.path("spacingRule").asInt(0) == SPACING_EXACT) {
            // 화면이 든 값은 "픽셀로 못박은 줄 높이" 다 - Univer 가 배수를 모르기
            // 때문에 우리가 셈해 둔 근사치다. 워드에 그대로 못박으면 글꼴 실제
            // 높이와 어긋나 쪽 수가 늘어난다(2026-08-24 지적). 배수로 되돌린다.
            paragraph.setSpacingBetween(multipleOf(lines, row.runs()),
                    org.apache.poi.xwpf.usermodel.LineSpacingRule.AUTO);
        }
    }

    /** 문단 들여쓰기. 왼쪽·첫 줄·내어쓰기를 옮긴다. */
    private void applyIndent(XWPFParagraph paragraph, String style) {
        JsonNode paragraphStyle = read(style).path("paragraphStyle");
        double start = paragraphStyle.path("indentStart").path("v").asDouble(-1);
        if (start >= 0) {
            paragraph.setIndentationLeft(twipOf(start));
        }
        double first = paragraphStyle.path("indentFirstLine").path("v").asDouble(-1);
        if (first >= 0) {
            paragraph.setIndentationFirstLine(twipOf(first));
        }
        double hanging = paragraphStyle.path("hanging").path("v").asDouble(-1);
        if (hanging >= 0) {
            paragraph.setIndentationHanging(twipOf(hanging));
        }
    }

    /**
     * 글머리 기호·번호.
     *
     * <p>워드는 목록을 번호 매김 파트(numbering.xml)에 적고 문단은 그 번호만 가리킨다.
     * 흘리면 목록이 맨 글줄이 된다(2026-08-24 지적).
     */
    private void applyBullet(XWPFDocument document, XWPFParagraph paragraph, String style,
                             java.util.Map<String, java.math.BigInteger> lists) {
        JsonNode bullet = read(style).path("bullet");
        String listId = bullet.path("listId").asString(null);
        if (listId == null || listId.isBlank()) {
            return;
        }
        boolean numbered = "ORDER_LIST".equals(bullet.path("listType").asString(""));
        java.math.BigInteger numId = lists.computeIfAbsent(listId,
                name -> newList(document, numbered));
        paragraph.setNumID(numId);
        int level = bullet.path("nestingLevel").asInt(0);
        if (level > 0) {
            paragraph.setNumILvl(java.math.BigInteger.valueOf(level));
        }
    }

    /** 목록 하나를 번호 매김 파트에 만든다. 아홉 단까지 둔다 - 워드 기본과 같다. */
    private java.math.BigInteger newList(XWPFDocument document, boolean numbered) {
        var numbering = document.createNumbering();
        var abstractNum = org.openxmlformats.schemas.wordprocessingml.x2006.main
                .CTAbstractNum.Factory.newInstance();
        abstractNum.setAbstractNumId(java.math.BigInteger.valueOf(NEXT_LIST.getAndIncrement()));
        for (int level = 0; level < LIST_LEVELS; level++) {
            var one = abstractNum.addNewLvl();
            one.setIlvl(java.math.BigInteger.valueOf(level));
            one.addNewNumFmt().setVal(numbered
                    ? org.openxmlformats.schemas.wordprocessingml.x2006.main.STNumberFormat.DECIMAL
                    : org.openxmlformats.schemas.wordprocessingml.x2006.main.STNumberFormat.BULLET);
            one.addNewLvlText().setVal(numbered ? "%" + (level + 1) + "." : "•");
            one.addNewStart().setVal(java.math.BigInteger.ONE);
            var indent = one.addNewPPr().addNewInd();
            indent.setLeft(java.math.BigInteger.valueOf(720L * (level + 1)));
            indent.setHanging(java.math.BigInteger.valueOf(360));
        }
        var made = new org.apache.poi.xwpf.usermodel.XWPFAbstractNum(abstractNum, numbering);
        return numbering.addNum(numbering.addAbstractNum(made));
    }

    /**
     * 목차의 오른쪽 탭과 점선.
     *
     * <p>화면은 탭 정지를 모르므로 점을 손수 채워 두지만, 워드로 내보낼 때는 탭을
     * 그대로 살려야 쪽 번호가 오른쪽 끝에 선다.
     */
    private void applyTab(XWPFParagraph paragraph, String style) {
        JsonNode written = read(style);
        double at = written.path("tabRight").asDouble(0);
        if (at <= 0) {
            return;
        }
        var properties = paragraph.getCTP().isSetPPr()
                ? paragraph.getCTP().getPPr() : paragraph.getCTP().addNewPPr();
        var tab = (properties.isSetTabs() ? properties.getTabs() : properties.addNewTabs())
                .addNewTab();
        tab.setVal(org.openxmlformats.schemas.wordprocessingml.x2006.main.STTabJc.RIGHT);
        tab.setPos(java.math.BigInteger.valueOf(twipOf(at)));
        String leader = written.path("tabLeader").asString(null);
        if ("dot".equals(leader)) {
            tab.setLeader(
                    org.openxmlformats.schemas.wordprocessingml.x2006.main.STTabTlc.DOT);
        }
    }

    /**
     * 못박아 둔 픽셀 높이를 줄 간격 배수로.
     *
     * <p>읽을 때 {@code 배수 x 글자 크기 x 1.1} 로 셈했으니 거꾸로 나눈다. 글자
     * 크기를 모르면 문서 기본으로 본다.
     */
    private double multipleOf(double px, String runs) {
        double size = firstFontSize(runs);
        double oneLine = size * PX_PER_PT * FONT_LINE_HEIGHT;
        if (oneLine <= 0) {
            return 1;
        }
        double multiple = px / oneLine;
        // 너무 촘촘하거나 너무 성긴 값은 셈이 틀어진 것이다. 워드가 쓰는 범위로 둔다.
        return Math.max(0.5, Math.min(multiple, 5));
    }

    /** 그 문단 첫 토막의 글자 크기(pt). 안 적혀 있으면 문서 기본이다. */
    private double firstFontSize(String runs) {
        JsonNode parsed = read(runs);
        if (parsed.isArray()) {
            for (JsonNode one : parsed) {
                double size = one.path("ts").path("fs").asDouble(0);
                if (size > 0) {
                    return size;
                }
            }
        }
        return DEFAULT_FONT_SIZE;
    }

    /** 붙이기 규칙. 제목이 쪽 끝에 홀로 남지 않게 한다. */
    private void applyKeep(XWPFParagraph paragraph, String style) {
        JsonNode paragraphStyle = read(style).path("paragraphStyle");
        if (paragraphStyle.path("keepNext").asInt(0) == 1) {
            paragraph.setKeepNext(true);
        }
        if (paragraphStyle.path("keepLines").asInt(0) == 1) {
            var properties = paragraph.getCTP().isSetPPr()
                    ? paragraph.getCTP().getPPr() : paragraph.getCTP().addNewPPr();
            if (!properties.isSetKeepLines()) {
                properties.addNewKeepLines();
            }
        }
    }

    /**
     * 문단 표시(¶)의 글자 크기.
     *
     * <p>워드는 문단 표시의 크기까지 보고 줄 높이를 잡는다. 9pt 짜리 목차 줄에
     * 문단 표시만 문서 기본 11pt 로 남으면 줄마다 높아져 쪽이 밀린다
     * (2026-08-24 실측: 목차 35줄에서 141pt 가 벌어졌다).
     */
    private void applyMarkSize(XWPFParagraph paragraph, String runs) {
        double size = firstFontSize(runs);
        if (size <= 0) {
            return;
        }
        var properties = paragraph.getCTP().isSetPPr()
                ? paragraph.getCTP().getPPr() : paragraph.getCTP().addNewPPr();
        var mark = properties.isSetRPr() ? properties.getRPr() : properties.addNewRPr();
        // 워드는 글자 크기를 반 포인트로 적는다.
        var half = java.math.BigInteger.valueOf(Math.round(size * 2));
        if (mark.sizeOfSzArray() > 0) {
            mark.getSzArray(0).setVal(half);
        } else {
            mark.addNewSz().setVal(half);
        }
    }

    /** 픽셀을 트윕으로. 워드가 쓰는 단위다. */
    private int twipOf(double px) {
        return (int) Math.round(px * TWIP_PER_PX);
    }

    private void applyAlignment(XWPFParagraph paragraph, String style) {
        JsonNode align = read(style).path("paragraphStyle").path("horizontalAlign");
        if (align.isMissingNode() || !align.isInt()) {
            return;
        }
        switch (align.asInt(0)) {
            case ALIGN_CENTER -> paragraph.setAlignment(ParagraphAlignment.CENTER);
            case ALIGN_RIGHT -> paragraph.setAlignment(ParagraphAlignment.RIGHT);
            case ALIGN_JUSTIFY -> paragraph.setAlignment(ParagraphAlignment.BOTH);
            case ALIGN_LEFT -> paragraph.setAlignment(ParagraphAlignment.LEFT);
            default -> { }
        }
    }

    /** 글자 한 토막과 그 서식. */
    /**
     * 서식이 같은 글 토막 하나.
     *
     * @param size 글자 크기(pt). 0 이면 안 적힌 것이다.
     * @param font 글꼴 이름. 없으면 null.
     * @param color 글자 색({@code #rrggbb}). 없으면 null.
     */
    private record Span(int from, int to, boolean bold, boolean italic,
                        boolean underline, boolean strike, double size, String font,
                        String color) {

        Span(int from, int to, boolean bold, boolean italic, boolean underline,
             boolean strike) {
            this(from, to, bold, italic, underline, strike, 0, null, null);
        }
    }

    /**
     * 서식이 바뀌는 자리마다 글을 토막 낸다.
     *
     * <p>{@code asInt(0)} 으로 기본값을 준다. Jackson 3 은 없는 값에 {@code asInt()}
     * 를 부르면 던진다 - 서식 하나가 빠졌다고 파일 전체를 잃으면 안 된다.
     *
     * <p>서식이 걸리지 않은 사이 구간도 토막으로 만든다. 빼면 그 글자가 파일에
     * 들어가지 않는다.
     */
    private List<Span> spansOf(int length, String runs) {
        List<Span> spans = new ArrayList<>();
        if (length == 0) {
            return spans;
        }
        JsonNode parsed = read(runs);

        int at = 0;
        for (JsonNode run : parsed.isArray() ? parsed : JSON.createArrayNode()) {
            int from = Math.max(0, run.path("st").asInt(0));
            int to = Math.min(length, run.path("ed").asInt(0));
            if (to <= from) {
                continue;
            }
            if (from > at) {
                spans.add(new Span(at, from, false, false, false, false));
            }
            JsonNode ts = run.path("ts");
            spans.add(new Span(from, to,
                    ts.path("bl").asInt(0) == 1,
                    ts.path("it").asInt(0) == 1,
                    ts.path("ul").path("s").asInt(0) == 1,
                    ts.path("st").path("s").asInt(0) == 1,
                    ts.path("fs").asDouble(0),
                    ts.path("ff").asString(null),
                    ts.path("cl").path("rgb").asString(null)));
            at = to;
        }
        if (at < length) {
            spans.add(new Span(at, length, false, false, false, false));
        }
        return spans;
    }

    /** 못 읽는 JSON 은 없는 것으로 본다. 서식 하나 때문에 파일 전체를 잃지 않는다. */
    private JsonNode read(String json) {
        if (json == null || json.isBlank()) {
            return JSON.createObjectNode();
        }
        try {
            return JSON.readTree(json);
        } catch (Exception e) {
            return JSON.createObjectNode();
        }
    }
}
