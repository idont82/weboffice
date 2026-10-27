package weboffice.doc;

import java.util.List;

import org.springframework.stereotype.Service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import kr.dogfoot.hwpxlib.object.HWPXFile;
import kr.dogfoot.hwpxlib.object.content.section_xml.SectionXMLFile;
import kr.dogfoot.hwpxlib.object.content.section_xml.ParaListCore;
import kr.dogfoot.hwpxlib.object.content.section_xml.paragraph.Para;
import kr.dogfoot.hwpxlib.object.content.section_xml.enumtype.HorzAlign;
import kr.dogfoot.hwpxlib.object.content.section_xml.paragraph.object.Picture;
import kr.dogfoot.hwpxlib.object.content.section_xml.paragraph.object.Table;
import kr.dogfoot.hwpxlib.object.content.section_xml.paragraph.object.table.Tc;
import kr.dogfoot.hwpxlib.object.content.section_xml.paragraph.object.table.Tr;
import kr.dogfoot.hwpxlib.tool.blankfilemaker.BlankFileMaker;
import kr.dogfoot.hwpxlib.writer.HWPXWriter;

/**
 * 한글 문서(hwpx) 쓰기.
 *
 * <p>읽기는 {@link HwpIoService} 가 맡는다. 쓰기를 거기 붙이지 않는 까닭: 그
 * 클래스가 이미 읽기 둘(hwp·hwpx)로 1,300줄이고, 쓰기까지 넣으면 한눈에 안 들어온다.
 *
 * <p>바닥부터 XML 을 짜지 않는다. {@link BlankFileMaker} 가 글꼴·글자속성·문단속성·
 * 테두리를 갖춘 빈 파일을 만들어 주므로, 거기에 담긴 것을 옮겨 적는다.
 */
@Service
public class HwpxWriteService {

    private static final org.slf4j.Logger logger =
            org.slf4j.LoggerFactory.getLogger(HwpxWriteService.class);

    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** 글 한 벌을 hwpx 바이트로. */
    public byte[] toHwpx(DocContent content) {
        try {
            HWPXFile file = BlankFileMaker.make();
            writeBody(file, content);
            return HWPXWriter.toBytes(file);
        } catch (Exception cannotWrite) {
            throw new DocWriteException("한글 문서(hwpx)로 쓰지 못했다.", cannotWrite);
        }
    }

    /**
     * 본문을 적는다.
     *
     * <p>문단이 하나도 없어도 빈 문단 하나는 남긴다. 한글은 문단 없는 구역을
     * 손상으로 보고 파일을 거절한다.
     */
    private void writeBody(HWPXFile file, DocContent content) {
        SectionXMLFile section = file.sectionXMLFileList().get(0);
        section.removeAllParas();
        HwpxRefs refs = new HwpxRefs(file);
        refs.defaults(content.defaults());

        writeTables(section, refs, content.tables(), -1);
        writePictures(file, section, refs, content.pictures(), -1);
        for (int no = 0; no < content.paragraphs().size(); no++) {
            writeParagraph(section.addNewPara(), refs, content.paragraphs().get(no));
            writeTables(section, refs, content.tables(), no);
            writePictures(file, section, refs, content.pictures(), no);
        }
        // 마지막 문단보다 뒤에 놓인 것도 쓴다. 안 쓰면 문서 끝의 표가 통째로
        // 빠진다 - 워드 내보내기가 2026-08-25 에 겪은 것과 같다.
        int last = content.paragraphs().size() - 1;
        writeRest(file, section, refs, content, last);
        if (section.countOfPara() == 0) {
            blank(section, refs);
        }
        writeSection(file, section, refs, content);
    }

    /**
     * 문단 하나를 적는다.
     *
     * <p>글자 서식이 없으면 글 전체를 기본 서식 덩이 하나로 담는다. 있으면 적힌
     * 토막마다 덩이를 나눈다 - 토막 사이에 빈 자리가 있으면 그것도 기본 서식으로
     * 담아야 글자가 사라지지 않는다.
     */
    private void writeParagraph(Para para, HwpxRefs refs, DocParagraphDto row) {
        String text = row.text() == null ? "" : row.text();
        JsonNode paragraphStyle = parse(row.style()).path("paragraphStyle");
        para.paraPrIDRef(refs.paraPr(paragraphStyle));
        para.styleIDRef("0");
        if (paragraphStyle.path("pageBreakBefore").asInt(0) == 1) {
            para.pageBreak(true);
        }

        writeRuns(para, refs, text, parseRuns(row.runs()));
    }

    /**
     * 글 한 줄을 서식 덩이로 나눠 적는다.
     *
     * <p>덩이 사이에 빈 자리가 있으면 그것도 기본 서식으로 담는다 - 적힌 덩이만
     * 담으면 사이 글자가 사라진다.
     */
    private void writeRuns(Para para, HwpxRefs refs, String text, List<JsonNode> runs) {
        if (runs.isEmpty()) {
            writeRun(para, refs.defaultCharPr(), text);
            return;
        }
        int at = 0;
        for (JsonNode run : runs) {
            int from = Math.max(at, Math.min(run.path("st").asInt(0), text.length()));
            int to = Math.max(from, Math.min(run.path("ed").asInt(0), text.length()));
            if (from > at) {
                writeRun(para, refs.defaultCharPr(), text.substring(at, from));
            }
            if (to > from) {
                writeRun(para, refs.charPr(run.path("ts")), text.substring(from, to));
            }
            at = to;
        }
        if (at < text.length()) {
            writeRun(para, refs.defaultCharPr(), text.substring(at));
        }
    }

    /** 마지막 문단보다 뒤에 놓인 표와 그림. 자리를 못 찾으면 끝에 붙인다. */
    private void writeRest(HWPXFile file, SectionXMLFile section, HwpxRefs refs,
                           DocContent content, int last) {
        for (DocTableDto table : content.tables()) {
            if (table.afterParagraphNo() > last) {
                writeTables(section, refs, List.of(table), table.afterParagraphNo());
            }
        }
        for (DocContent.DocPicture picture : content.pictures()) {
            if (picture.afterParagraphNo() > last) {
                writePictures(file, section, refs, List.of(picture),
                        picture.afterParagraphNo());
            }
        }
    }

    /**
     * 이 문단 뒤에 놓인 표들.
     *
     * <p>표는 제 문단 하나를 차지한다. 글이 든 문단에 끼워 넣으면 읽는 쪽이 그 글을
     * 표 앞 글로 보지 못한다.
     */
    private void writeTables(ParaListCore list, HwpxRefs refs,
                             List<DocTableDto> tables, int afterParagraphNo) {
        for (DocTableDto table : tables) {
            if (table.afterParagraphNo() != afterParagraphNo) {
                continue;
            }
            Para para = list.addNewPara();
            para.paraPrIDRef(refs.defaultParaPr());
            para.styleIDRef("0");
            var run = para.addNewRun();
            run.charPrIDRef(refs.defaultCharPr());
            writeTable(run.addNewTable(), refs, table);
        }
    }

    /**
     * 구역 설정과 쪽마다 되풀이되는 글.
     *
     * <p>한글은 이것을 첫 문단의 첫 덩이에 매단다. 그래서 본문을 다 적은 뒤에
     * 앞에 끼워 넣는다 - 먼저 적으면 빈 문단 하나가 더 생겨 문단 번호가 밀린다.
     * 종이 크기와 여백이 빠지면 받는 쪽이 한글 기본 종이로 열어 표가 넘친다.
     *
     * <p>첫 쪽만 다른 머리글은 싣지 않는다. 한글 머리글이 아는 갈래는 모든 쪽·짝수
     * 쪽·홀수 쪽 셋뿐이라 "첫 쪽" 을 적을 자리가 없다.
     */
    private void writeSection(HWPXFile file, SectionXMLFile section, HwpxRefs refs,
                              DocContent content) {
        Para first = section.getPara(0);
        var run = new kr.dogfoot.hwpxlib.object.content.section_xml.paragraph.Run();
        run.charPrIDRef(refs.defaultCharPr());
        // 글 덩이보다 앞에 둔다. 한글은 구역 설정을 첫 문단의 첫 덩이에서 찾는다.
        first.insertRun(run, 0);

        run.createSecPr();
        writePage(run.secPr(), content.page(), content.defaults());
        writeEdges(file, run, refs, content);
    }

    /** 종이 크기와 네 여백. 머리글·바닥글 여백도 여기 든다. */
    private void writePage(kr.dogfoot.hwpxlib.object.content.section_xml.paragraph.secpr.SecPr
                           secPr, DocContent.DocPage page, DocContent.DocDefaults defaults) {
        if (defaults != null && defaults.linePitch() > 0) {
            secPr.createGrid();
            // 담긴 값은 트윕이다. 한글은 HWPUNIT 으로 적는다 - 1트윕이 5다.
            secPr.grid().lineGrid(defaults.linePitch() * 5);
        }
        if (page == null) {
            return;
        }
        secPr.createPagePr();
        var pagePr = secPr.pagePr();
        pagePr.width((int) HwpxUnit.px(page.width()));
        pagePr.height((int) HwpxUnit.px(page.height()));
        pagePr.createMargin();
        pagePr.margin().left((int) HwpxUnit.px(page.marginLeft()));
        pagePr.margin().right((int) HwpxUnit.px(page.marginRight()));
        pagePr.margin().top((int) HwpxUnit.px(page.marginTop()));
        pagePr.margin().bottom((int) HwpxUnit.px(page.marginBottom()));
        pagePr.margin().header((int) HwpxUnit.px(page.marginHeader()));
        pagePr.margin().footer((int) HwpxUnit.px(page.marginFooter()));
    }

    /** 머리글과 바닥글. 안에 든 글·표·그림은 본문과 같은 방식으로 적는다. */
    private void writeEdges(HWPXFile file,
                            kr.dogfoot.hwpxlib.object.content.section_xml.paragraph.Run run,
                            HwpxRefs refs, DocContent content) {
        if (content.header() == null && content.footer() == null) {
            return;
        }
        var ctrl = run.addNewCtrl();
        if (content.header() != null) {
            var header = ctrl.addNewHeader();
            header.applyPageType(kr.dogfoot.hwpxlib.object.content.section_xml.enumtype
                    .ApplyPageType.BOTH);
            header.createSubList();
            writeEdge(file, header.subList(), refs, content.header());
        }
        if (content.footer() != null) {
            var footer = ctrl.addNewFooter();
            footer.applyPageType(kr.dogfoot.hwpxlib.object.content.section_xml.enumtype
                    .ApplyPageType.BOTH);
            footer.createSubList();
            writeEdge(file, footer.subList(), refs, content.footer());
        }
    }

    private void writeEdge(HWPXFile file, ParaListCore list, HwpxRefs refs,
                           DocContent.DocEdge edge) {
        writeTables(list, refs, edge.tables(), -1);
        writePictures(file, list, refs, edge.pictures(), -1);
        for (int no = 0; no < edge.paragraphs().size(); no++) {
            writeParagraph(list.addNewPara(), refs, edge.paragraphs().get(no));
            writeTables(list, refs, edge.tables(), no);
            writePictures(file, list, refs, edge.pictures(), no);
        }
        if (list.countOfPara() == 0) {
            blank(list, refs);
        }
    }

    /**
     * 글 없는 문단 하나.
     *
     * <p>서식 번호를 안 붙이면 한글이 문서를 거절한다 - 빈 문단이라고 그냥 더하면
     * {@code paraPrIDRef} 가 없는 채로 나간다.
     */
    private void blank(ParaListCore list, HwpxRefs refs) {
        Para para = list.addNewPara();
        para.paraPrIDRef(refs.defaultParaPr());
        para.styleIDRef("0");
        var run = para.addNewRun();
        run.charPrIDRef(refs.defaultCharPr());
        run.addNewT().addText("");
    }

    /**
     * 이 문단 뒤에 놓인 그림들.
     *
     * <p>알맹이는 꾸러미 안 {@code BinData/} 에 넣고 목록(content.hpf)에 올린다.
     * 본문은 그 번호만 가리킨다 - 글자 서식과 같은 방식이다.
     *
     * <p>무엇인지 모를 바이트는 건너뛴다. 종류를 못 적으면 한글이 꾸러미를 거절해
     * 문서 전체를 못 열게 된다 - 그림 하나를 잃는 쪽이 낫다.
     */
    private void writePictures(HWPXFile file, ParaListCore list, HwpxRefs refs,
                               List<DocContent.DocPicture> pictures, int afterParagraphNo) {
        for (DocContent.DocPicture picture : pictures) {
            if (picture.afterParagraphNo() != afterParagraphNo) {
                continue;
            }
            String kind = kindOf(picture.bytes());
            if (kind == null) {
                logger.warn("무엇인지 모를 그림이라 싣지 않는다: {} 바이트",
                        picture.bytes() == null ? 0 : picture.bytes().length);
                continue;
            }
            Para para = list.addNewPara();
            para.paraPrIDRef(refs.defaultParaPr());
            para.styleIDRef("0");
            var run = para.addNewRun();
            run.charPrIDRef(refs.defaultCharPr());
            writePicture(run.addNewPicture(), attach(file, picture.bytes(), kind), picture);
        }
    }

    /** 그림 하나. 크기와 자리를 적고 알맹이 번호를 건다. */
    private void writePicture(Picture made, String binaryId, DocContent.DocPicture picture) {
        made.createImg();
        made.img().binaryItemIDRef(binaryId);
        made.createSZ();
        made.sz().width(HwpxUnit.px(picture.width()));
        made.sz().height(HwpxUnit.px(picture.height()));
        made.createImgRect();

        made.createPos();
        made.pos().treatAsChar(!picture.floating());
        if (!picture.floating()) {
            return;
        }
        // 떠 있는 그림은 쪽에서부터 잰다. 표지 배경이 그렇다 - 흐름에 넣으면
        // 첫 쪽을 통째로 차지한다.
        made.pos().horzOffset(HwpxUnit.px(picture.left()));
        made.pos().vertOffset(HwpxUnit.px(picture.top()));
        made.textWrap(picture.behind()
                ? kr.dogfoot.hwpxlib.object.content.section_xml.enumtype
                        .TextWrapMethod.BEHIND_TEXT
                : kr.dogfoot.hwpxlib.object.content.section_xml.enumtype
                        .TextWrapMethod.SQUARE);
    }

    /** 알맹이를 꾸러미에 넣고 그 번호를 돌려준다. */
    private String attach(HWPXFile file, byte[] bytes, String kind) {
        var manifest = file.contentHPFFile().manifest();
        String id = "wo-image" + (manifest.count() + 1);
        var item = manifest.addNew();
        item.id(id);
        item.href("BinData/" + id + "." + kind);
        item.mediaType("image/" + ("jpg".equals(kind) ? "jpeg" : kind));
        item.embedded(true);
        item.createAttachedFile();
        item.attachedFile().data(bytes);
        return id;
    }

    /**
     * 머리 몇 바이트로 그림 종류를 가린다.
     *
     * <p>파일 이름이 없다 - 알맹이만 담아 두기 때문이다. 모르면 {@code null} 이다.
     */
    private String kindOf(byte[] bytes) {
        if (bytes == null || bytes.length < 4) {
            return null;
        }
        if ((bytes[0] & 0xff) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G') {
            return "png";
        }
        if ((bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xd8) {
            return "jpg";
        }
        if (bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F') {
            return "gif";
        }
        if (bytes[0] == 'B' && bytes[1] == 'M') {
            return "bmp";
        }
        return null;
    }

    /** 표 하나. 칸은 줄마다 왼쪽에서 오른쪽으로 적는다. */
    private void writeTable(Table made, HwpxRefs refs, DocTableDto table) {
        int rows = table.cells().size();
        int columns = rows == 0 ? 0 : table.cells().get(0).size();
        made.rowCnt((short) rows);
        made.colCnt((short) columns);
        made.borderFillIDRef(refs.borderFill(table.borders(), null));
        made.createPos();
        made.pos().treatAsChar(true);
        made.pos().horzAlign(switch (table.align()) {
            case DocTableDto.ALIGN_CENTER -> HorzAlign.CENTER;
            case DocTableDto.ALIGN_RIGHT -> HorzAlign.RIGHT;
            default -> HorzAlign.LEFT;
        });
        if (table.opensNewPage()) {
            made.pageBreak(kr.dogfoot.hwpxlib.object.content.section_xml.enumtype
                    .TablePageBreak.TABLE);
        }

        for (int r = 0; r < rows; r++) {
            Tr tr = made.addNewTr();
            List<String> line = table.cells().get(r);
            for (int c = 0; c < line.size(); c++) {
                // 합치기에 가려진 칸은 0 이다. 한글은 그 칸을 아예 안 적는다 -
                // 적으면 한 줄의 칸 수가 표가 말한 열 수와 어긋나 손상으로 본다.
                if (at(table.columnSpans(), r, c, 1) == 0
                        || at(table.rowSpans(), r, c, 1) == 0) {
                    continue;
                }
                writeCell(tr.addNewTc(), refs, table, r, c);
            }
        }
    }

    /** 칸 하나. 자리와 크기, 칸모양, 그리고 칸 안 문단들. */
    private void writeCell(Tc tc, HwpxRefs refs, DocTableDto table, int r, int c) {
        tc.createCellAddr();
        tc.cellAddr().colAddr((short) c);
        tc.cellAddr().rowAddr((short) r);
        tc.createCellSpan();
        tc.cellSpan().colSpan(at(table.columnSpans(), r, c, 1).shortValue());
        tc.cellSpan().rowSpan(at(table.rowSpans(), r, c, 1).shortValue());
        Double across = width(table, c);
        Double down = height(table, r);
        if (across != null || down != null) {
            // 반만 맞는 너비를 적으면 앞 칸만 넓어지고 뒤 칸이 찌그러진다. 모르는
            // 자리는 비워 두어 한글이 제 셈으로 채우게 한다.
            tc.createCellSz();
            tc.cellSz().width(across == null ? null : HwpxUnit.px(across));
            tc.cellSz().height(down == null ? null : HwpxUnit.px(down));
        }
        margin(tc, table);
        tc.borderFillIDRef(refs.borderFill(
                cellSides(table, r, c), text(table.cellShades(), r, c)));

        tc.createSubList();
        writeCellText(tc, refs, table, r, c);
    }

    /**
     * 칸 안 글.
     *
     * <p>칸 글자는 줄바꿈으로 이어 붙인 한 덩이다. 줄마다 문단 하나로 가른다.
     * 글자 서식 자리는 이어 붙인 글 기준이므로 줄 앞머리만큼 당겨 쓴다.
     *
     * <p>글자가 없어도 문단 하나는 남긴다. 한글은 문단 없는 칸을 거절한다.
     */
    private void writeCellText(Tc tc, HwpxRefs refs, DocTableDto table, int r, int c) {
        String whole = text(table.cells(), r, c);
        List<JsonNode> runs = parseRuns(text(table.cellRuns(), r, c));
        JsonNode styles = parse(text(table.cellStyles(), r, c));
        Integer align = at(table.cellAligns(), r, c, 0);

        String[] lines = (whole == null ? "" : whole).split("\n", -1);
        int from = 0;
        for (int i = 0; i < lines.length; i++) {
            Para para = tc.subList().addNewPara();
            para.paraPrIDRef(refs.paraPr(lineStyle(styles, i, align)));
            para.styleIDRef("0");
            writeRuns(para, refs, lines[i], shifted(runs, from, lines[i].length()));
            from += lines[i].length() + 1;
        }
    }

    /**
     * 그 줄의 문단 서식.
     *
     * <p>칸마다의 맞춤은 줄마다의 서식이 없을 때만 쓴다 - 적힌 것이 더 자세하다.
     */
    private JsonNode lineStyle(JsonNode styles, int line, Integer align) {
        JsonNode one = styles.isArray() ? styles.path(line) : styles;
        JsonNode paragraphStyle = one.path("paragraphStyle");
        if (!paragraphStyle.isMissingNode() && !paragraphStyle.isEmpty()) {
            return paragraphStyle;
        }
        if (align == null || align <= 1) {
            return JSON.createObjectNode();
        }
        return JSON.createObjectNode().put("horizontalAlign", align);
    }

    /** 이어 붙인 글 기준의 서식 자리를 한 줄 기준으로 당긴다. */
    private List<JsonNode> shifted(List<JsonNode> runs, int from, int length) {
        List<JsonNode> moved = new java.util.ArrayList<>();
        for (JsonNode run : runs) {
            int st = Math.max(run.path("st").asInt(0) - from, 0);
            int ed = Math.min(run.path("ed").asInt(0) - from, length);
            if (ed <= st) {
                continue;
            }
            var one = JSON.createObjectNode();
            one.put("st", st);
            one.put("ed", ed);
            one.set("ts", run.path("ts"));
            moved.add(one);
        }
        return moved;
    }

    /** 칸이 제 변을 알면 그것이 먼저다. 표 하나로는 한글 서식이 담기지 않는다. */
    private String cellSides(DocTableDto table, int r, int c) {
        if (!table.hasBorders()) {
            return null;
        }
        String own = text(table.cellBorders(), r, c);
        return own != null ? own : table.borders();
    }

    private void margin(Tc tc, DocTableDto table) {
        if (table.cellMargin() == null) {
            return;
        }
        tc.createCellMargin();
        tc.cellMargin().set(HwpxUnit.px(table.cellMargin().left()),
                HwpxUnit.px(table.cellMargin().right()),
                HwpxUnit.px(table.cellMargin().top()),
                HwpxUnit.px(table.cellMargin().bottom()));
    }

    /**
     * 열 너비. 모르면 {@code null} 이다.
     *
     * <p>{@code Double} 로 받는다. 삼항에서 {@code int} 와 섞으면 자바가 숫자로 맞추며
     * 상자를 까는데, 줄 높이를 못박지 않은 표는 그 자리가 null 이라 터진다.
     */
    private Double width(DocTableDto table, int c) {
        List<Double> widths = table.columnWidths();
        if (widths == null || widths.size() != columnCount(table) || c >= widths.size()) {
            return null;
        }
        return widths.get(c);
    }

    /** 줄 높이. 못박지 않은 줄은 {@code null} 이다. */
    private Double height(DocTableDto table, int r) {
        List<Double> heights = table.rowHeights();
        return heights == null || r >= heights.size() ? null : heights.get(r);
    }

    /** 칸이 가장 많은 줄의 칸 수. 열 너비 수가 이것과 맞아야 쓸 수 있다. */
    private int columnCount(DocTableDto table) {
        int most = 0;
        for (List<String> line : table.cells()) {
            most = Math.max(most, line == null ? 0 : line.size());
        }
        return most;
    }

    private <T> T at(List<List<T>> lines, int r, int c, T fallback) {
        T one = text(lines, r, c);
        return one == null ? fallback : one;
    }

    private <T> T text(List<List<T>> lines, int r, int c) {
        if (lines == null || r >= lines.size()) {
            return null;
        }
        List<T> line = lines.get(r);
        return line == null || c >= line.size() ? null : line.get(c);
    }

    private void writeRun(Para para, String charPrId, String text) {
        var run = para.addNewRun();
        run.charPrIDRef(charPrId);
        run.addNewT().addText(text);
    }

    /** 담아 둔 서식 한 덩이. 읽지 못하면 없는 것으로 친다 - 글자가 먼저다. */
    private JsonNode parse(String json) {
        if (json == null || json.isBlank()) {
            return JSON.createObjectNode();
        }
        try {
            return JSON.readTree(json);
        } catch (RuntimeException cannotRead) {
            return JSON.createObjectNode();
        }
    }

    /** 담아 둔 글자 서식. 읽지 못하면 없는 것으로 친다 - 글자가 먼저다. */
    private List<JsonNode> parseRuns(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            JsonNode read = JSON.readTree(json);
            if (!read.isArray()) {
                return List.of();
            }
            List<JsonNode> runs = new java.util.ArrayList<>();
            read.forEach(runs::add);
            return runs;
        } catch (RuntimeException cannotRead) {
            return List.of();
        }
    }
}
