package weboffice.doc;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import kr.dogfoot.hwplib.object.HWPFile;
import kr.dogfoot.hwplib.object.bodytext.ParagraphListInterface;
import kr.dogfoot.hwplib.object.bodytext.Section;
import kr.dogfoot.hwplib.object.bodytext.control.Control;
import kr.dogfoot.hwplib.object.bodytext.control.ControlTable;
import kr.dogfoot.hwplib.object.bodytext.control.table.Cell;
import kr.dogfoot.hwplib.object.bodytext.control.table.Row;
import kr.dogfoot.hwplib.object.bodytext.paragraph.Paragraph;
import kr.dogfoot.hwplib.object.bodytext.paragraph.charshape.CharPositionShapeIdPair;
import kr.dogfoot.hwplib.object.bodytext.paragraph.text.HWPChar;
import kr.dogfoot.hwplib.object.bodytext.paragraph.text.HWPCharType;
import kr.dogfoot.hwplib.object.docinfo.CharShape;
import kr.dogfoot.hwplib.object.docinfo.DocInfo;
import kr.dogfoot.hwplib.object.docinfo.charshape.UnderLineSort;
import kr.dogfoot.hwplib.reader.HWPReader;
import kr.dogfoot.hwpxlib.object.HWPXFile;
import kr.dogfoot.hwpxlib.object.content.header_xml.enumtype.LineType2;
import kr.dogfoot.hwpxlib.object.content.header_xml.enumtype.UnderlineType;
import kr.dogfoot.hwpxlib.object.content.header_xml.references.CharPr;
import kr.dogfoot.hwpxlib.object.content.header_xml.references.ParaPr;
import kr.dogfoot.hwpxlib.object.content.section_xml.ParaListCore;
import kr.dogfoot.hwpxlib.object.content.section_xml.SectionXMLFile;
import kr.dogfoot.hwpxlib.object.content.section_xml.paragraph.Para;
import kr.dogfoot.hwpxlib.object.content.section_xml.paragraph.Run;
import kr.dogfoot.hwpxlib.object.content.section_xml.paragraph.RunItem;
import kr.dogfoot.hwpxlib.object.content.section_xml.paragraph.T;
import kr.dogfoot.hwpxlib.object.content.section_xml.paragraph.object.Table;
import kr.dogfoot.hwpxlib.object.content.section_xml.paragraph.object.table.Tr;
import kr.dogfoot.hwpxlib.object.content.section_xml.paragraph.TItem;
import kr.dogfoot.hwpxlib.object.content.section_xml.paragraph.t.NormalText;
import kr.dogfoot.hwpxlib.reader.HWPXReader;

import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 한글 문서 읽기.
 *
 * <p>읽기만 한다. 내보내기는 docx 다 - 한글 형식으로 되돌려 주지 않는다. 되돌려
 * 주려면 표·글상자·그림까지 다 옮길 수 있어야 하는데, 반만 옮긴 파일을 한글 이름으로
 * 내려주면 받은 사람은 원본이 깨진 줄 안다.
 *
 * <p>같은 한글 문서라도 hwp(2진)와 hwpx(OWPML 압축)는 파일 형식이 아주 다르다.
 * 읽는 길이 둘인 이유가 이것이다.
 *
 * <p>담는 것은 글자와 굵게·기울임·밑줄·취소선, 문단 정렬이다. 표·그림·글상자는
 * 버린다 - 화면이 아직 그것을 그리지 못한다.
 */
@Service
public class HwpIoService {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** 한글이 쓰는 길이 단위. 1/7200 인치라 96dpi 화면에서는 75 가 1px 이다. */
    private static final double HWPUNIT_PER_PX = 7200d / 96d;

    /** 한글은 글자 크기를 1/100 pt 로 적는다. */
    private static final double HWP_SIZE_PER_PT = 100d;

    /** Univer 의 가로 맞춤 값. docx 쪽과 같은 값을 쓴다. */
    private static final int ALIGN_CENTER = 2;
    private static final int ALIGN_RIGHT = 3;
    private static final int ALIGN_JUSTIFY = 4;

    /** 옛 이름. 문단만 쓰는 자리가 아직 있다. */
    public List<DocParagraphDto> fromHwp(byte[] bytes) {
        return readHwp(bytes).paragraphs();
    }

    /** 옛 이름. 문단만 쓰는 자리가 아직 있다. */
    public List<DocParagraphDto> fromHwpx(byte[] bytes) {
        return readHwpx(bytes).paragraphs();
    }

    /** hwp(2진) 읽기. 표는 표대로 담는다. */
    public DocContent readHwp(byte[] bytes) {
        HWPFile file;
        try {
            file = HWPReader.fromInputStream(new ByteArrayInputStream(bytes));
        } catch (Exception e) {
            throw new DocImportException("한글 문서(hwp)로 읽지 못했다.", e);
        }

        List<DocParagraphDto> rows = new ArrayList<>();
        List<DocTableDto> tables = new ArrayList<>();
        List<DocContent.DocPicture> pictures = new ArrayList<>();
        for (Section section : file.getBodyText().getSectionList()) {
            collectHwp(file.getDocInfo(), section, rows, tables, pictures, file);
        }
        return new DocContent(rows, tables, pictures, pageOf(file));
    }

    /**
     * 종이 크기와 여백(px).
     *
     * <p>담지 않으면 내려받은 파일이 워드 기본 종이로 열려 표가 넘친다
     * (2026-08-26 지적: 한글 신청서를 내려받으면 많이 깨진다).
     */
    private DocContent.DocPage pageOf(HWPFile file) {
        for (Section section : file.getBodyText().getSectionList()) {
            for (Paragraph paragraph : section.getParagraphs()) {
                if (paragraph.getControlList() == null) {
                    continue;
                }
                for (Control control : paragraph.getControlList()) {
                    if (!(control instanceof kr.dogfoot.hwplib.object.bodytext.control
                            .ControlSectionDefine define)) {
                        continue;
                    }
                    var paper = define.getPageDef();
                    if (paper == null || paper.getPaperWidth() <= 0) {
                        continue;
                    }
                    return new DocContent.DocPage(
                            paper.getPaperWidth() / HWPUNIT_PER_PX,
                            paper.getPaperHeight() / HWPUNIT_PER_PX,
                            paper.getLeftMargin() / HWPUNIT_PER_PX,
                            paper.getRightMargin() / HWPUNIT_PER_PX,
                            paper.getTopMargin() / HWPUNIT_PER_PX,
                            paper.getBottomMargin() / HWPUNIT_PER_PX,
                            paper.getHeaderMargin() / HWPUNIT_PER_PX,
                            paper.getFooterMargin() / HWPUNIT_PER_PX);
                }
            }
        }
        return null;
    }

    /**
     * 문단을 차례로 담는다. 표를 만나면 칸 안으로 들어간다.
     *
     * <p>관공서 서식은 글이 거의 다 표 안에 있다. 표를 건너뛰면 문서가 통째로 비어
     * 버린다 - 실물 신청서에서 문단 셋이 모두 빈 채로 나왔다. 칸 자리는 잃고 글만
     * 남는다. 다 옮기는 것보다 다 잃지 않는 것이 낫다.
     */
    private void collectHwp(DocInfo docInfo, ParagraphListInterface list,
                            List<DocParagraphDto> rows, List<DocTableDto> tables) {
        collectHwp(docInfo, list, rows, tables, new ArrayList<>(), null);
    }

    private void collectHwp(DocInfo docInfo, ParagraphListInterface list,
                            List<DocParagraphDto> rows, List<DocTableDto> tables,
                            List<DocContent.DocPicture> pictures, HWPFile file) {
        for (Paragraph paragraph : list.getParagraphs()) {
            rows.add(readHwpParagraph(docInfo, paragraph, rows.size()));
            if (paragraph.getControlList() == null) {
                continue;
            }
            for (Control control : paragraph.getControlList()) {
                if (control instanceof ControlTable table) {
                    tables.add(hwpGrid(docInfo, table, rows.size() - 1));
                    continue;
                }
                if (file != null
                        && control instanceof kr.dogfoot.hwplib.object.bodytext.control.gso
                            .ControlPicture picture) {
                    collectHwpPicture(file, picture, rows.size() - 1, pictures);
                }
            }
        }
    }

    /**
     * 그림 하나를 모은다.
     *
     * <p>한글은 그림 알맹이를 따로 두고(BinData) 그림 조각이 번호로 가리킨다. 번호를
     * 따라가 알맹이를 꺼낸다 - 관공서 서식에는 직인·안내 그림이 흔해서 빠지면 서식이
     * 반쪽이 된다.
     */
    private void collectHwpPicture(HWPFile file,
            kr.dogfoot.hwplib.object.bodytext.control.gso.ControlPicture picture,
            int afterParagraphNo, List<DocContent.DocPicture> pictures) {
        var info = picture.getShapeComponentPicture() == null
                ? null : picture.getShapeComponentPicture().getPictureInfo();
        if (info == null) {
            return;
        }
        byte[] bytes = hwpBinaryData(file, info.getBinItemID());
        if (bytes == null || bytes.length == 0) {
            return;
        }
        pictures.add(new DocContent.DocPicture(afterParagraphNo, bytes,
                picture.getHeader().getWidth() / HWPUNIT_PER_PX,
                picture.getHeader().getHeight() / HWPUNIT_PER_PX));
    }

    /**
     * 번호가 가리키는 그림 알맹이.
     *
     * <p>번호는 1 부터다. 담아 둔 차례와 같은 자리를 쓴다 - 이름(BIN0001.png)으로
     * 찾으면 파일마다 이름 규칙이 달라 못 찾는 일이 생긴다.
     */
    private byte[] hwpBinaryData(HWPFile file, int binItemId) {
        var embedded = file.getBinData().getEmbeddedBinaryDataList();
        int at = binItemId - 1;
        if (at < 0 || at >= embedded.size()) {
            return null;
        }
        return embedded.get(at).getData();
    }

    /**
     * hwp 표를 고른 격자로 펼친다.
     *
     * <p>한글은 칸을 합치면 가려진 자리를 아예 적지 않는다. 그대로 옮기면 행마다
     * 칸 수가 달라 표가 어긋난다. 칸이 제 자리(칸 번호)를 알고 있으므로 그 자리에
     * 놓고 빈 자리는 가려진 칸(0)으로 채운다 - 화면이 쓰는 규칙과 같다.
     */
    private DocTableDto hwpGrid(DocInfo docInfo, ControlTable table, int afterParagraphNo) {
        List<List<Placed>> grid = new ArrayList<>();
        for (Row row : table.getRowList()) {
            List<Placed> line = new ArrayList<>();
            for (Cell cell : row.getCellList()) {
                CellContent content = hwpCellContent(docInfo, cell);
                line.add(new Placed(content.text(), content.runs(),
                        cell.getListHeader().getColIndex(),
                        Math.max(1, cell.getListHeader().getColSpan()),
                        Math.max(1, cell.getListHeader().getRowSpan())));
            }
            grid.add(line);
        }
        return spread(grid, afterParagraphNo, hwpColumnWidths(table),
                hwpRowHeights(table), hwpCellMargin(table));
    }

    /** 자리를 아는 칸 하나. */
    private record Placed(String text, String runs, int columnHint, int columnSpan,
                          int rowSpan) {
    }

    /**
     * 칸을 제 자리에 놓고 빈 자리를 가려진 칸(0)으로 채운다.
     *
     * <p>칸 번호를 적지 않은 파일이 있다 - 그때는 차례대로 놓는다. 번호를 그대로
     * 믿으면 모두 첫 자리에 겹쳐 앞 칸이 사라진다(2026-08-12 실측).
     */
    private DocTableDto spread(List<List<Placed>> grid, int afterParagraphNo,
                               List<Double> columnWidths, List<Double> rowHeights,
                               DocTableDto.CellMargin cellMargin) {
        int columnCount = 1;
        for (List<Placed> line : grid) {
            int width = 0;
            for (Placed cell : line) {
                width = Math.max(width, cell.columnHint()) + cell.columnSpan();
            }
            columnCount = Math.max(columnCount, width);
        }

        List<List<String>> cells = new ArrayList<>();
        List<List<String>> runs = new ArrayList<>();
        List<List<Integer>> columnSpans = new ArrayList<>();
        List<List<Integer>> rowSpans = new ArrayList<>();

        for (List<Placed> line : grid) {
            List<String> texts = new ArrayList<>(java.util.Collections.nCopies(columnCount, ""));
            List<String> style = new ArrayList<>(java.util.Collections.nCopies(columnCount,
                    (String) null));
            List<Integer> across = new ArrayList<>(java.util.Collections.nCopies(columnCount, 0));
            List<Integer> down = new ArrayList<>(java.util.Collections.nCopies(columnCount, 0));

            int at = 0;
            for (Placed cell : line) {
                at = Math.min(columnCount - 1, Math.max(at, cell.columnHint()));
                texts.set(at, cell.text());
                style.set(at, cell.runs());
                across.set(at, cell.columnSpan());
                down.set(at, cell.rowSpan());
                at += cell.columnSpan();
            }
            cells.add(texts);
            runs.add(style);
            columnSpans.add(across);
            rowSpans.add(down);
        }

        // 한글이 적어 둔 줄 높이는 그 줄이 실제로 차지한 높이다. 못박아야 원본과
        // 같은 쪽에 앉는다 - "적어도" 로 두면 워드가 글에 맞춰 늘려 세 쪽짜리
        // 서식이 아홉 쪽이 됐다(2026-08-26 실측).
        return new DocTableDto(afterParagraphNo, cells, columnWidths, List.of(),
                columnSpans, rowSpans, true, runs,
                DocTableDto.ALIGN_LEFT, rowHeights, List.of(), List.of(), false, null,
                true, cellMargin);
    }

    /**
     * 표의 칸 너비(px). 첫 줄의 칸을 잰다.
     *
     * <p>너비를 잃으면 모든 칸이 똑같이 나뉜다 - 관공서 서식처럼 첫 칸이 좁고 뒤가
     * 넓은 표가 원본과 딴판이 된다(2026-08-12 지적).
     */
    /**
     * 칸 안쪽 여백(px).
     *
     * <p>첫 칸이 적어 둔 값을 표 전체의 값으로 본다. 화면 기본(좌우 10px)은 한글
     * 문서보다 넉넉해 표가 원본보다 높아진다(2026-08-26 지적).
     */
    private DocTableDto.CellMargin hwpCellMargin(ControlTable table) {
        for (Row row : table.getRowList()) {
            for (Cell cell : row.getCellList()) {
                var header = cell.getListHeader();
                if (header.getLeftMargin() <= 0 && header.getTopMargin() <= 0) {
                    continue;
                }
                return new DocTableDto.CellMargin(
                        header.getLeftMargin() / HWPUNIT_PER_PX,
                        header.getRightMargin() / HWPUNIT_PER_PX,
                        header.getTopMargin() / HWPUNIT_PER_PX,
                        header.getBottomMargin() / HWPUNIT_PER_PX);
            }
        }
        return null;
    }

    /** 칸이 차지하는 열 수. 안 적힌 파일은 한 칸으로 본다. */
    private int spanOf(kr.dogfoot.hwplib.object.bodytext.control.table
            .ListHeaderForCell header) {
        return Math.max(1, header.getColSpan());
    }

    /**
     * 표의 줄 높이(px).
     *
     * <p>흘리면 표가 글에 맞춰 늘어나 세 쪽짜리 서식이 여섯 쪽이 된다
     * (2026-08-26 지적). 한 줄에서 가장 낮은 칸을 그 줄 높이로 본다 - 여러 줄을
     * 걸친 칸(rowSpan)은 그 줄만의 높이가 아니다.
     */
    private List<Double> hwpRowHeights(ControlTable table) {
        List<Double> heights = new ArrayList<>();
        for (Row row : table.getRowList()) {
            double lowest = 0;
            for (Cell cell : row.getCellList()) {
                var header = cell.getListHeader();
                if (header.getRowSpan() > 1 || header.getHeight() <= 0) {
                    continue;
                }
                double high = header.getHeight() / HWPUNIT_PER_PX;
                lowest = lowest == 0 ? high : Math.min(lowest, high);
            }
            heights.add(lowest > 0 ? lowest : null);
        }
        return heights.stream().anyMatch(java.util.Objects::nonNull) ? heights : List.of();
    }

    private List<Double> hwpColumnWidths(ControlTable table) {
        // 열 자리(colIndex)와 차지하는 칸 수(colSpan)로 열마다 폭을 모은다. 한 줄만
        // 보면 제목처럼 합친 칸 때문에 너비가 한둘만 잡혀 표가 뭉갠다
        // (2026-08-26 지적: 아홉 칸 표가 한 칸이 됐다).
        // 칸 번호를 안 적은 파일이 있다 - 그때는 차례대로 놓는다(spread 와 같은 셈).
        int columns = 0;
        for (Row row : table.getRowList()) {
            int at = 0;
            for (Cell cell : row.getCellList()) {
                var header = cell.getListHeader();
                at = Math.max(at, header.getColIndex());
                columns = Math.max(columns, at + spanOf(header));
                at += spanOf(header);
            }
        }
        if (columns <= 0) {
            return List.of();
        }
        Double[] widths = new Double[columns];
        for (Row row : table.getRowList()) {
            int at = 0;
            for (Cell cell : row.getCellList()) {
                var header = cell.getListHeader();
                at = Math.max(at, header.getColIndex());
                if (spanOf(header) == 1 && at < columns
                        && header.getWidth() > 0 && widths[at] == null) {
                    widths[at] = header.getWidth() / HWPUNIT_PER_PX;
                }
                at += spanOf(header);
            }
        }
        // 단독으로 선 칸이 없는 열은 나머지 폭을 고르게 나눈다.
        double known = 0;
        int missing = 0;
        for (Double one : widths) {
            if (one == null) {
                missing += 1;
            } else {
                known += one;
            }
        }
        if (missing == columns) {
            return List.of();
        }
        double each = missing == 0 ? 0 : Math.max(1, known / (columns - missing));
        List<Double> made = new ArrayList<>();
        for (Double one : widths) {
            made.add(one == null ? each : one);
        }
        return made;
    }

    /** 칸 글자와 그 서식. 서식 자리는 칸 글자 안 자리다. */
    private record CellContent(String text, String runs) {
    }

    /**
     * 칸 안의 글자와 서식.
     *
     * <p>표 머리가 굵은 서식이 흔하다. 서식이 빠지면 머리와 본문이 같아 보인다.
     * 칸 안 문단이 여럿이면 글은 이어 붙이고 서식 자리도 그만큼 민다.
     */
    private CellContent hwpCellContent(DocInfo docInfo, Cell cell) {
        List<DocParagraphDto> inner = new ArrayList<>();
        List<DocTableDto> nested = new ArrayList<>();
        collectHwp(docInfo, cell.getParagraphList(), inner, nested);

        StringBuilder text = new StringBuilder();
        ArrayNode runs = JSON.createArrayNode();
        for (DocParagraphDto row : inner) {
            int from = text.length();
            if (from > 0) {
                text.append('\n');
                from = text.length();
            }
            text.append(row.text());
            shiftRuns(runs, row.runs(), from);
        }
        return new CellContent(text.toString(), runs.isEmpty() ? null : runs.toString());
    }

    /** 칸 안의 글자와 서식. hwp 쪽과 같은 뜻이다. */
    private CellContent hwpxCellContent(HWPXFile file,
            kr.dogfoot.hwpxlib.object.content.section_xml.paragraph.object.table.Tc cell) {
        if (cell.subList() == null) {
            return new CellContent("", null);
        }
        List<DocParagraphDto> inner = new ArrayList<>();
        List<DocTableDto> nested = new ArrayList<>();
        collectHwpx(file, cell.subList(), inner, nested);

        StringBuilder text = new StringBuilder();
        ArrayNode runs = JSON.createArrayNode();
        for (DocParagraphDto row : inner) {
            int from = text.length();
            if (from > 0) {
                text.append('\n');
                from = text.length();
            }
            text.append(row.text());
            shiftRuns(runs, row.runs(), from);
        }
        return new CellContent(text.toString(), runs.isEmpty() ? null : runs.toString());
    }

    /** 문단 서식을 칸 글자 자리로 민다. 못 읽는 것은 버린다. */
    private void shiftRuns(ArrayNode runs, String json, int from) {
        if (json == null || json.isBlank()) {
            return;
        }
        try {
            for (var run : JSON.readTree(json)) {
                ObjectNode moved = ((ObjectNode) run).deepCopy();
                moved.put("st", run.path("st").asInt(0) + from);
                moved.put("ed", run.path("ed").asInt(0) + from);
                runs.add(moved);
            }
        } catch (Exception e) {
            // 서식 하나 때문에 칸 글을 잃지 않는다.
        }
    }

    /** 칸 안 글. 여러 문단이면 줄바꿈으로 잇는다. */
    private String cellText(DocInfo docInfo, Cell cell) {
        List<DocParagraphDto> inner = new ArrayList<>();
        List<DocTableDto> nested = new ArrayList<>();
        collectHwp(docInfo, cell.getParagraphList(), inner, nested);
        return inner.stream().map(DocParagraphDto::text)
                .reduce((a, b) -> a + "\n" + b).orElse("");
    }

    /**
     * hwpx(OWPML) 읽기.
     *
     * <p>hwpxlib 은 파일 경로만 받는다. 올라온 것은 바이트라 임시 파일을 거친다 -
     * 읽고 나면 지운다. 문서보안을 푼 알맹이가 디스크에 남으면 안 된다.
     */
    public DocContent readHwpx(byte[] bytes) {
        Path temp = null;
        try {
            temp = Files.createTempFile("kweboffice-", ".hwpx");
            Files.write(temp, bytes);
            HWPXFile file = HWPXReader.fromFile(temp.toFile());
            return readHwpx(file);
        } catch (DocImportException e) {
            throw e;
        } catch (Exception e) {
            throw new DocImportException("한글 문서(hwpx)로 읽지 못했다.", e);
        } finally {
            deleteQuietly(temp);
        }
    }

    private DocContent readHwpx(HWPXFile file) {
        List<DocParagraphDto> rows = new ArrayList<>();
        List<DocTableDto> tables = new ArrayList<>();
        for (SectionXMLFile section : file.sectionXMLFileList().items()) {
            collectHwpx(file, section, rows, tables);
        }
        return new DocContent(rows, tables);
    }

    /** hwp 쪽과 같다. 표를 만나면 칸 안으로 들어간다. */
    private void collectHwpx(HWPXFile file, ParaListCore list, List<DocParagraphDto> rows,
                             List<DocTableDto> tables) {
        for (Para para : list.paras()) {
            rows.add(readHwpxParagraph(file, para, rows.size()));
            for (Run run : para.runs()) {
                for (int i = 0; i < run.countOfRunItem(); i++) {
                    if (!(run.getRunItem(i) instanceof Table table)) {
                        continue;
                    }
                    tables.add(hwpxGrid(file, table, rows.size() - 1));
                }
            }
        }
    }

    /** hwpx 표를 고른 격자로 펼친다. hwp 쪽과 같은 뜻이다. */
    private DocTableDto hwpxGrid(HWPXFile file, Table table, int afterParagraphNo) {
        List<List<Placed>> grid = new ArrayList<>();
        for (int r = 0; r < table.countOfTr(); r++) {
            Tr tr = table.getTr(r);
            List<Placed> line = new ArrayList<>();
            for (int c = 0; c < tr.countOfTc(); c++) {
                var tc = tr.getTc(c);
                CellContent content = hwpxCellContent(file, tc);
                line.add(new Placed(content.text(), content.runs(), columnAt(tc),
                        colSpanOf(tc), rowSpanOf(tc)));
            }
            grid.add(line);
        }
        return spread(grid, afterParagraphNo, hwpxColumnWidths(table),
                hwpxRowHeights(table), null);
    }

    /** 칸이 제 자리를 모르면 0 이다. 놓을 때 차례대로 밀린다. */
    private int columnAt(kr.dogfoot.hwpxlib.object.content.section_xml.paragraph.object.table.Tc tc) {
        if (tc.cellAddr() == null || tc.cellAddr().colAddr() == null) {
            return 0;
        }
        return tc.cellAddr().colAddr();
    }

    private int colSpanOf(kr.dogfoot.hwpxlib.object.content.section_xml.paragraph.object.table.Tc tc) {
        if (tc.cellSpan() == null || tc.cellSpan().colSpan() == null) {
            return 1;
        }
        return Math.max(1, tc.cellSpan().colSpan());
    }

    private int rowSpanOf(kr.dogfoot.hwpxlib.object.content.section_xml.paragraph.object.table.Tc tc) {
        if (tc.cellSpan() == null || tc.cellSpan().rowSpan() == null) {
            return 1;
        }
        return Math.max(1, tc.cellSpan().rowSpan());
    }

    /** 표의 칸 너비(px). hwp 쪽과 같은 뜻이다. */
    /** 표의 줄 높이(px). hwpx 도 칸마다 높이를 적는다. */
    private List<Double> hwpxRowHeights(Table table) {
        List<Double> heights = new ArrayList<>();
        for (int r = 0; r < table.countOfTr(); r++) {
            Tr row = table.getTr(r);
            double lowest = 0;
            for (int c = 0; c < row.countOfTc(); c++) {
                var cell = row.getTc(c);
                var span = cell.cellSpan();
                if (span != null && span.rowSpan() != null && span.rowSpan() > 1) {
                    continue;
                }
                var size = cell.cellSz();
                if (size == null || size.height() == null || size.height() <= 0) {
                    continue;
                }
                double high = size.height() / HWPUNIT_PER_PX;
                lowest = lowest == 0 ? high : Math.min(lowest, high);
            }
            heights.add(lowest > 0 ? lowest : null);
        }
        return heights.stream().anyMatch(java.util.Objects::nonNull) ? heights : List.of();
    }

    private List<Double> hwpxColumnWidths(Table table) {
        if (table.countOfTr() == 0) {
            return List.of();
        }
        Tr first = table.getTr(0);
        List<Double> widths = new ArrayList<>();
        for (int c = 0; c < first.countOfTc(); c++) {
            var size = first.getTc(c).cellSz();
            if (size == null || size.width() == null || size.width() <= 0) {
                return List.of();
            }
            widths.add(size.width() / HWPUNIT_PER_PX);
        }
        return widths;
    }

    /** 칸 안 글. 여러 문단이면 줄바꿈으로 잇는다. */
    private String cellText(HWPXFile file, kr.dogfoot.hwpxlib.object.content.section_xml.paragraph.object.table.Tc cell) {
        if (cell.subList() == null) {
            return "";
        }
        List<DocParagraphDto> inner = new ArrayList<>();
        List<DocTableDto> nested = new ArrayList<>();
        collectHwpx(file, cell.subList(), inner, nested);
        return inner.stream().map(DocParagraphDto::text)
                .reduce((a, b) -> a + "\n" + b).orElse("");
    }

    // --- hwp ---

    private DocParagraphDto readHwpParagraph(DocInfo docInfo, Paragraph paragraph, int paragraphNo) {
        StringBuilder text = new StringBuilder();
        // hwp 의 자리는 글자 하나가 1 이지만 확장 제어문자는 8 을 차지한다. 서식이
        // 가리키는 자리를 우리 글자 자리로 옮기려면 그 차이를 세어 두어야 한다.
        List<long[]> marks = new ArrayList<>();
        long position = 0;
        // 빈 문단에는 글 기록 자체가 없다. 그래도 문단은 문단이다 - 버리면 뒤쪽 글이
        // 통째로 밀린다.
        for (HWPChar c : paragraph.getText() == null
                ? List.<HWPChar>of() : paragraph.getText().getCharList()) {
            marks.add(new long[] {position, text.length()});
            if (c.getType() == HWPCharType.Normal) {
                text.append((char) c.getCode());
            }
            position += c.getCharSize();
        }
        marks.add(new long[] {position, text.length()});

        return new DocParagraphDto(paragraphNo, text.toString(),
                hwpStyle(docInfo, paragraph),
                hwpRuns(docInfo, paragraph, marks, text.length()));
    }

    private String hwpRuns(DocInfo docInfo, Paragraph paragraph, List<long[]> marks, int length) {
        if (paragraph.getCharShape() == null || length == 0) {
            return null;
        }
        List<CharPositionShapeIdPair> pairs = paragraph.getCharShape().getPositonShapeIdPairList();
        ArrayNode runs = JSON.createArrayNode();

        for (int i = 0; i < pairs.size(); i++) {
            int from = textIndexAt(marks, pairs.get(i).getPosition());
            int to = i + 1 < pairs.size()
                    ? textIndexAt(marks, pairs.get(i + 1).getPosition())
                    : length;
            if (to <= from) {
                continue;
            }
            ObjectNode style = hwpCharStyle(docInfo, (int) pairs.get(i).getShapeId());
            if (style.isEmpty()) {
                continue;
            }
            ObjectNode entry = JSON.createObjectNode();
            entry.put("st", from);
            entry.put("ed", to);
            entry.set("ts", style);
            runs.add(entry);
        }
        return runs.isEmpty() ? null : runs.toString();
    }

    private ObjectNode hwpCharStyle(DocInfo docInfo, int shapeId) {
        ObjectNode style = JSON.createObjectNode();
        List<CharShape> shapes = docInfo.getCharShapeList();
        if (shapeId < 0 || shapeId >= shapes.size()) {
            return style;
        }
        var property = shapes.get(shapeId).getProperty();
        if (property.isBold()) {
            style.put("bl", 1);
        }
        if (property.isItalic()) {
            style.put("it", 1);
        }
        if (property.getUnderLineSort() != UnderLineSort.None) {
            style.set("ul", JSON.createObjectNode().put("s", 1));
        }
        if (property.isStrikeLine()) {
            style.set("st", JSON.createObjectNode().put("s", 1));
        }

        CharShape shape = shapes.get(shapeId);
        if (shape.getBaseSize() > 0) {
            style.put("fs", shape.getBaseSize() / HWP_SIZE_PER_PT);
        }
        String color = rgbOf(shape.getCharColor());
        if (color != null) {
            style.set("cl", JSON.createObjectNode().put("rgb", color));
        }
        return style;
    }

    /**
     * 한글 색을 화면 색으로.
     *
     * <p>검정은 적지 않는다. 한글은 색을 안 쓴 글자도 검정으로 적어 두는데, 그것을
     * 그대로 옮기면 어두운 화면에서 글이 안 보인다.
     */
    private String rgbOf(kr.dogfoot.hwplib.object.etc.Color4Byte color) {
        if (color == null || (color.getR() == 0 && color.getG() == 0 && color.getB() == 0)) {
            return null;
        }
        return String.format("#%02x%02x%02x", color.getR(), color.getG(), color.getB());
    }

    /**
     * 문단 서식. 맞춤과 문단 사이 간격을 담는다.
     *
     * <p>간격이 빠지면 문단이 다닥다닥 붙어 원본보다 짧아 보이고 쪽 수도 달라진다.
     * 한글은 간격을 1/100 pt 로 적는다.
     */
    private String hwpStyle(DocInfo docInfo, Paragraph paragraph) {
        int shapeId = paragraph.getHeader().getParaShapeId();
        if (shapeId < 0 || shapeId >= docInfo.getParaShapeList().size()) {
            return null;
        }
        var shape = docInfo.getParaShapeList().get(shapeId);
        Integer align = switch (shape.getProperty1().getAlignment()) {
            case Center -> ALIGN_CENTER;
            case Right -> ALIGN_RIGHT;
            case Justify -> ALIGN_JUSTIFY;
            default -> null;
        };

        ObjectNode paragraphStyle = JSON.createObjectNode();
        if (align != null) {
            paragraphStyle.put("horizontalAlign", align);
        }
        if (shape.getTopParaSpace() > 0) {
            paragraphStyle.set("spaceAbove", JSON.createObjectNode()
                    .put("v", shape.getTopParaSpace() / HWP_SIZE_PER_PT));
        }
        if (shape.getBottomParaSpace() > 0) {
            paragraphStyle.set("spaceBelow", JSON.createObjectNode()
                    .put("v", shape.getBottomParaSpace() / HWP_SIZE_PER_PT));
        }
        // 들여쓰기. 번호 매긴 목록과 인용은 이것으로 층을 나타낸다.
        putIndent(paragraphStyle, "indentStart", shape.getLeftMargin());
        putIndent(paragraphStyle, "indentEnd", shape.getRightMargin());
        putIndent(paragraphStyle, "indentFirstLine", shape.getIndent());
        if (paragraphStyle.isEmpty()) {
            return null;
        }
        ObjectNode style = JSON.createObjectNode();
        style.set("paragraphStyle", paragraphStyle);
        return style.toString();
    }

    /** 들여쓰기 한 자리를 px 로 적는다. 없거나 0 이면 적지 않는다. */
    private void putIndent(ObjectNode style, String name, long hwpUnit) {
        if (hwpUnit <= 0) {
            return;
        }
        style.set(name, JSON.createObjectNode().put("v", hwpUnit / HWPUNIT_PER_PX));
    }

    /** hwp 자리를 우리 글자 자리로. 자리가 글자 가운데 떨어지면 그 글자 앞으로 본다. */
    private int textIndexAt(List<long[]> marks, long position) {
        int index = 0;
        for (long[] mark : marks) {
            if (mark[0] > position) {
                break;
            }
            index = (int) mark[1];
        }
        return index;
    }

    // --- hwpx ---

    private DocParagraphDto readHwpxParagraph(HWPXFile file, Para para, int paragraphNo) {
        StringBuilder text = new StringBuilder();
        ArrayNode runs = JSON.createArrayNode();

        for (Run run : para.runs()) {
            int from = text.length();
            appendHwpxText(run, text);
            if (text.length() == from) {
                continue;
            }
            ObjectNode style = hwpxCharStyle(file, run.charPrIDRef());
            if (style.isEmpty()) {
                continue;
            }
            ObjectNode entry = JSON.createObjectNode();
            entry.put("st", from);
            entry.put("ed", text.length());
            entry.set("ts", style);
            runs.add(entry);
        }

        return new DocParagraphDto(paragraphNo, text.toString(),
                hwpxStyle(file, para.paraPrIDRef()),
                runs.isEmpty() ? null : runs.toString());
    }

    private void appendHwpxText(Run run, StringBuilder text) {
        for (int i = 0; i < run.countOfRunItem(); i++) {
            RunItem item = run.getRunItem(i);
            if (!(item instanceof T t)) {
                // 표·그림·글상자다. 아직 화면이 그리지 못한다.
                continue;
            }
            // 글자만 든 토막은 조각 목록이 비고 글이 통째로 들어 있다. 조각만 훑으면
            // 보통 문서의 글이 전부 사라진다.
            if (t.isOnlyText()) {
                if (t.onlyText() != null) {
                    text.append(t.onlyText());
                }
                continue;
            }
            for (int j = 0; j < t.countOfItems(); j++) {
                TItem piece = t.getItem(j);
                if (piece instanceof NormalText normal && normal.text() != null) {
                    text.append(normal.text());
                }
            }
        }
    }

    private ObjectNode hwpxCharStyle(HWPXFile file, String charPrId) {
        ObjectNode style = JSON.createObjectNode();
        CharPr charPr = findCharPr(file, charPrId);
        if (charPr == null) {
            return style;
        }
        if (charPr.bold() != null) {
            style.put("bl", 1);
        }
        if (charPr.italic() != null) {
            style.put("it", 1);
        }
        // 실물 문서의 글자모양에는 "밑줄 없음"·"취소선 없음" 이 값으로 들어 있다.
        // 있고 없고만 보면 문서 전체에 줄이 그어진다 - 실제로 겪었다.
        if (charPr.underline() != null && charPr.underline().type() != null
                && charPr.underline().type() != UnderlineType.NONE) {
            style.set("ul", JSON.createObjectNode().put("s", 1));
        }
        if (charPr.strikeout() != null && charPr.strikeout().shape() != null
                && charPr.strikeout().shape() != LineType2.NONE) {
            style.set("st", JSON.createObjectNode().put("s", 1));
        }
        if (charPr.height() != null && charPr.height() > 0) {
            style.put("fs", charPr.height() / HWP_SIZE_PER_PT);
        }
        String color = rgbOf(charPr.textColor());
        if (color != null) {
            style.set("cl", JSON.createObjectNode().put("rgb", color));
        }
        return style;
    }

    /** hwpx 색({@code #FF0000})을 화면 색으로. 검정은 hwp 쪽과 같은 뜻으로 적지 않는다. */
    private String rgbOf(String color) {
        if (color == null || color.isBlank() || !color.startsWith("#")) {
            return null;
        }
        String lower = color.toLowerCase(java.util.Locale.ROOT);
        return "#000000".equals(lower) ? null : lower;
    }

    /** hwp 쪽과 같다. 맞춤과 문단 사이 간격을 담는다. */
    private String hwpxStyle(HWPXFile file, String paraPrId) {
        ParaPr paraPr = findParaPr(file, paraPrId);
        if (paraPr == null) {
            return null;
        }
        Integer align = paraPr.align() == null || paraPr.align().horizontal() == null
                ? null
                : switch (paraPr.align().horizontal()) {
                    case CENTER -> ALIGN_CENTER;
                    case RIGHT -> ALIGN_RIGHT;
                    case JUSTIFY -> ALIGN_JUSTIFY;
                    default -> null;
                };

        ObjectNode paragraphStyle = JSON.createObjectNode();
        if (align != null) {
            paragraphStyle.put("horizontalAlign", align);
        }
        var margin = paraPr.margin();
        if (margin != null && margin.left() != null && margin.left().value() != null) {
            putIndent(paragraphStyle, "indentStart", margin.left().value());
        }
        if (margin != null && margin.right() != null && margin.right().value() != null) {
            putIndent(paragraphStyle, "indentEnd", margin.right().value());
        }
        if (margin != null && margin.intent() != null && margin.intent().value() != null) {
            putIndent(paragraphStyle, "indentFirstLine", margin.intent().value());
        }
        if (margin != null && margin.prev() != null && margin.prev().value() != null
                && margin.prev().value() > 0) {
            paragraphStyle.set("spaceAbove", JSON.createObjectNode()
                    .put("v", margin.prev().value() / HWP_SIZE_PER_PT));
        }
        if (margin != null && margin.next() != null && margin.next().value() != null
                && margin.next().value() > 0) {
            paragraphStyle.set("spaceBelow", JSON.createObjectNode()
                    .put("v", margin.next().value() / HWP_SIZE_PER_PT));
        }
        if (paragraphStyle.isEmpty()) {
            return null;
        }
        ObjectNode style = JSON.createObjectNode();
        style.set("paragraphStyle", paragraphStyle);
        return style.toString();
    }

    private CharPr findCharPr(HWPXFile file, String id) {
        if (id == null) {
            return null;
        }
        for (CharPr charPr : file.headerXMLFile().refList().charProperties().items()) {
            if (id.equals(charPr.id())) {
                return charPr;
            }
        }
        return null;
    }

    private ParaPr findParaPr(HWPXFile file, String id) {
        if (id == null) {
            return null;
        }
        for (ParaPr paraPr : file.headerXMLFile().refList().paraProperties().items()) {
            if (id.equals(paraPr.id())) {
                return paraPr;
            }
        }
        return null;
    }

    // --- 함께 쓰는 것 ---

    private String alignStyle(int align) {
        ObjectNode style = JSON.createObjectNode();
        style.set("paragraphStyle", JSON.createObjectNode().put("horizontalAlign", align));
        return style.toString();
    }

    private void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (Exception e) {
            // 지우지 못해도 읽기는 끝났다. 임시 폴더 청소는 운영체제 몫이다.
        }
    }
}
