package weboffice.doc;

import java.io.ByteArrayOutputStream;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.function.Consumer;

import kr.dogfoot.hwplib.object.HWPFile;
import kr.dogfoot.hwplib.object.bodytext.Section;
import kr.dogfoot.hwplib.object.bodytext.control.ControlTable;
import kr.dogfoot.hwplib.object.bodytext.control.ControlType;
import kr.dogfoot.hwplib.object.bodytext.control.table.Cell;
import kr.dogfoot.hwplib.object.bodytext.control.table.Row;
import kr.dogfoot.hwplib.object.bodytext.paragraph.Paragraph;
import kr.dogfoot.hwplib.object.bodytext.paragraph.text.HWPCharType;
import kr.dogfoot.hwplib.object.docinfo.BorderFill;
import kr.dogfoot.hwplib.object.docinfo.borderfill.BorderThickness;
import kr.dogfoot.hwplib.object.docinfo.borderfill.BorderType;
import kr.dogfoot.hwplib.object.docinfo.borderfill.EachBorder;
import kr.dogfoot.hwplib.object.docinfo.borderfill.fillinfo.PatternType;
import kr.dogfoot.hwplib.object.docinfo.CharShape;
import kr.dogfoot.hwplib.object.docinfo.ParaShape;
import kr.dogfoot.hwplib.object.docinfo.parashape.Alignment;
import kr.dogfoot.hwplib.object.etc.Color4Byte;
import kr.dogfoot.hwplib.writer.HWPWriter;
import kr.dogfoot.hwpxlib.object.HWPXFile;
import kr.dogfoot.hwpxlib.object.content.header_xml.enumtype.HorizontalAlign2;
import kr.dogfoot.hwpxlib.object.content.header_xml.enumtype.LineType2;
import kr.dogfoot.hwpxlib.object.content.header_xml.enumtype.UnderlineType;
import kr.dogfoot.hwpxlib.object.content.header_xml.references.CharPr;
import kr.dogfoot.hwpxlib.object.content.header_xml.references.ParaPr;
import kr.dogfoot.hwpxlib.object.content.section_xml.paragraph.Para;
import kr.dogfoot.hwpxlib.object.content.section_xml.paragraph.Run;
import kr.dogfoot.hwpxlib.object.content.section_xml.paragraph.object.Table;
import kr.dogfoot.hwpxlib.object.content.section_xml.paragraph.object.table.Tc;
import kr.dogfoot.hwpxlib.object.content.section_xml.paragraph.object.table.Tr;
import kr.dogfoot.hwpxlib.writer.HWPXWriter;

/**
 * 시험용 한글 문서 만들기.
 *
 * <p>한글이 깔린 자리에서만 도는 시험은 쓸모가 없고, 남의 문서를 저장소에 넣을 수도
 * 없다. 그래서 시험 파일은 그때그때 만든다.
 */
final class HwpTestFiles {

    /** 첫 문단을 이미 쓴 구역. 빈 파일의 첫 문단은 구역 정의를 들고 있어 지울 수 없다. */
    private final Set<Section> 쓴_구역 = Collections.newSetFromMap(new IdentityHashMap<>());

    byte[] hwp(Consumer<HWPFile> build) throws Exception {
        HWPFile file = kr.dogfoot.hwplib.tool.blankfilemaker.BlankFileMaker.make();
        build.accept(file);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        HWPWriter.toStream(file, out);
        return out.toByteArray();
    }

    Paragraph 문단(HWPFile file, String text) {
        Section section = file.getBodyText().getSectionList().get(0);
        Paragraph paragraph = 빈_문단(section);
        try {
            paragraph.getText().addString(text);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        if (paragraph.getCharShape().getPositonShapeIdPairList().isEmpty()) {
            paragraph.getCharShape().addParaCharShape(0, 0);
            paragraph.getHeader().setCharShapeCount(1);
        }
        return paragraph;
    }

    private Paragraph 빈_문단(Section section) {
        // 첫 글은 이미 있는 문단에 쓴다 - 새로 만들면 앞에 빈 문단이 하나 남는다.
        if (쓴_구역.add(section)) {
            return section.getParagraph(0);
        }
        Paragraph paragraph = section.addNewParagraph();
        paragraph.createText();
        paragraph.createCharShape();
        paragraph.createLineSeg();
        return paragraph;
    }

    /** 우리 글자 자리(n 번째 글자)를 hwp 의 자리로 옮긴다. 확장 제어문자가 8 을 차지한다. */
    long 글자자리(Paragraph paragraph, int index) {
        long position = 0;
        int seen = 0;
        for (var c : paragraph.getText().getCharList()) {
            if (c.getType() == HWPCharType.Normal) {
                if (seen == index) {
                    return position;
                }
                seen++;
            }
            position += c.getCharSize();
        }
        return position;
    }

    int 굵은_글자모양(HWPFile file) {
        CharShape bold = file.getDocInfo().getCharShapeList().get(0).clone();
        bold.getProperty().setBold(true);
        file.getDocInfo().getCharShapeList().add(bold);
        file.getDocInfo().getIDMappings()
                .setCharShapeCount(file.getDocInfo().getCharShapeList().size());
        return file.getDocInfo().getCharShapeList().size() - 1;
    }

    int 가운데_문단모양(HWPFile file) {
        ParaShape center = file.getDocInfo().getParaShapeList().get(0).clone();
        center.getProperty1().setAlignment(Alignment.Center);
        file.getDocInfo().getParaShapeList().add(center);
        file.getDocInfo().getIDMappings()
                .setParaShapeCount(file.getDocInfo().getParaShapeList().size());
        return file.getDocInfo().getParaShapeList().size() - 1;
    }

    /**
     * 첫 줄을 들여 쓴 문단모양. 한글은 길이를 1/7200 인치로 적는다.
     *
     * <p>관공서 서식은 가운데 맞춤 대신 들여쓰기로 제목을 가운데쯤에 놓는다.
     * 장기요양인정 신청서의 제목 넉 줄이 그렇다(들여 18816 = 250.9px).
     */
    int 들여쓴_문단모양(HWPFile file, int hwpUnit) {
        ParaShape indented = file.getDocInfo().getParaShapeList().get(0).clone();
        indented.setIndent(hwpUnit);
        file.getDocInfo().getParaShapeList().add(indented);
        file.getDocInfo().getIDMappings()
                .setParaShapeCount(file.getDocInfo().getParaShapeList().size());
        return file.getDocInfo().getParaShapeList().size() - 1;
    }

    /** 위아래 사이를 띄운 문단모양. 한글은 간격을 1/100 pt 로 적는다. */
    int 사이가_뜬_문단모양(HWPFile file) {
        ParaShape spaced = file.getDocInfo().getParaShapeList().get(0).clone();
        spaced.setTopParaSpace(600);
        spaced.setBottomParaSpace(300);
        file.getDocInfo().getParaShapeList().add(spaced);
        file.getDocInfo().getIDMappings()
                .setParaShapeCount(file.getDocInfo().getParaShapeList().size());
        return file.getDocInfo().getParaShapeList().size() - 1;
    }

    /** 왼쪽으로 들여 쓴 문단모양. 한글은 길이를 1/7200 인치로 적는다. */
    int 들여쓴_문단모양(HWPFile file) {
        ParaShape indented = file.getDocInfo().getParaShapeList().get(0).clone();
        indented.setLeftMargin(7200);
        indented.setIndent(3600);
        file.getDocInfo().getParaShapeList().add(indented);
        file.getDocInfo().getIDMappings()
                .setParaShapeCount(file.getDocInfo().getParaShapeList().size());
        return file.getDocInfo().getParaShapeList().size() - 1;
    }

    /** 크기(pt)와 색을 준 글자모양. */
    int 큰_빨간_글자모양(HWPFile file, int pt) {
        CharShape shape = file.getDocInfo().getCharShapeList().get(0).clone();
        // hwp 는 글자 크기를 1/100 pt 로 적는다.
        shape.setBaseSize(pt * 100);
        shape.getCharColor().setR((short) 255);
        shape.getCharColor().setG((short) 0);
        shape.getCharColor().setB((short) 0);
        file.getDocInfo().getCharShapeList().add(shape);
        file.getDocInfo().getIDMappings()
                .setCharShapeCount(file.getDocInfo().getCharShapeList().size());
        return file.getDocInfo().getCharShapeList().size() - 1;
    }

    /** 크기(pt)와 색을 준 글자모양. */
    String 큰_빨간_글자모양(HWPXFile file, int pt) {
        CharPr shape = file.headerXMLFile().refList().charProperties().get(0).clone();
        shape.id("102");
        shape.height(pt * 100);
        shape.textColor("#FF0000");
        file.headerXMLFile().refList().charProperties().add(shape);
        return shape.id();
    }

    /** 한 줄짜리 표. 칸마다 글이 한 문단씩 들어간다. */
    /**
     * 바탕색을 칠한 칸 모양을 하나 만들고 번호를 준다.
     *
     * <p>한글은 칸의 바탕색과 네 변을 <b>칸 모양(BorderFill) 하나</b>에 같이 담고,
     * 칸은 번호로 그것을 가리킨다. 번호는 1부터다 - 0 은 "가리키지 않음" 이다.
     */
    int 바탕색_칸모양(HWPFile file, int r, int g, int b) {
        BorderFill fill = file.getDocInfo().addNewBorderFill();
        // 갓 만든 칸모양은 네 변이 비어 있다. 비운 채로 저장하면 writer 가 변의
        // 종류를 읽다 터진다 - 색만 쓰고 싶어도 변은 채워 둬야 한다.
        for (EachBorder border : new EachBorder[] {fill.getLeftBorder(),
                fill.getRightBorder(), fill.getTopBorder(), fill.getBottomBorder(),
                fill.getDiagonalBorder()}) {
            border.setType(BorderType.None);
            border.setThickness(BorderThickness.MM0_1);
        }
        fill.getFillInfo().getType().setPatternFill(true);
        fill.getFillInfo().createPatternFill();
        // 무늬 없이 색만 칠한다. 무늬 종류도 비워 두면 writer 가 터진다.
        fill.getFillInfo().getPatternFill().setPatternType(PatternType.None);
        Color4Byte back = fill.getFillInfo().getPatternFill().getBackColor();
        back.setR((short) r);
        back.setG((short) g);
        back.setB((short) b);
        return file.getDocInfo().getBorderFillList().size();
    }

    /**
     * 아래 변만 그은 칸모양을 만들고 번호를 준다.
     *
     * <p>관공서 서식은 네 변을 다 긋지 않는다 - 가로줄만 긋고 세로선은 레이블
     * 옆에만 두는 식이다. 그래서 "아래만" 이 시험할 값이다.
     */
    int 아래선_칸모양(HWPFile file, int r, int g, int b, BorderThickness thickness) {
        BorderFill fill = file.getDocInfo().addNewBorderFill();
        for (EachBorder border : new EachBorder[] {fill.getLeftBorder(),
                fill.getRightBorder(), fill.getTopBorder(), fill.getDiagonalBorder()}) {
            border.setType(BorderType.None);
            border.setThickness(BorderThickness.MM0_1);
        }
        EachBorder bottom = fill.getBottomBorder();
        bottom.setType(BorderType.Solid);
        bottom.setThickness(thickness);
        bottom.getColor().setR((short) r);
        bottom.getColor().setG((short) g);
        bottom.getColor().setB((short) b);
        return file.getDocInfo().getBorderFillList().size();
    }

    ControlTable 표(HWPFile file, String... cells) {
        Section section = file.getBodyText().getSectionList().get(0);
        Paragraph holder = 빈_문단(section);
        try {
            holder.getText().addExtendCharForTable();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        holder.getCharShape().addParaCharShape(0, 0);
        holder.getHeader().setCharShapeCount(1);

        ControlTable table = (ControlTable) holder.addNewControl(ControlType.Table);
        table.getTable().setRowCount(1);
        table.getTable().setColumnCount(cells.length);
        table.getTable().getCellCountOfRowList().add(cells.length);

        Row row = table.addNewRow();
        for (String text : cells) {
            Cell cell = row.addNewCell();
            cell.getListHeader().setParaCount(1);
            Paragraph paragraph = cell.getParagraphList().addNewParagraph();
            paragraph.createText();
            paragraph.createCharShape();
            paragraph.createLineSeg();
            try {
                paragraph.getText().addString(text);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            paragraph.getCharShape().addParaCharShape(0, 0);
            paragraph.getHeader().setCharShapeCount(1);
        }
        return table;
    }

    /** 표에 줄 하나를 더한다. 칸 수가 다른 줄을 만들 때 쓴다. */
    void 줄(ControlTable table, String... cells) {
        table.getTable().setRowCount(table.getTable().getRowCount() + 1);
        table.getTable().getCellCountOfRowList().add(cells.length);
        Row row = table.addNewRow();
        for (String text : cells) {
            Cell cell = row.addNewCell();
            cell.getListHeader().setParaCount(1);
            Paragraph paragraph = cell.getParagraphList().addNewParagraph();
            paragraph.createText();
            paragraph.createCharShape();
            paragraph.createLineSeg();
            try {
                paragraph.getText().addString(text);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            paragraph.getCharShape().addParaCharShape(0, 0);
            paragraph.getHeader().setCharShapeCount(1);
        }
    }

    /**
     * 그림 하나가 든 hwp. 알맹이는 BinData 에 두고 그림 조각이 그것을 가리킨다.
     *
     * @param pt 그림 크기(1/7200 인치). 7200 이 1 인치다.
     */
    void 그림(HWPFile file, byte[] bytes, long size) {
        file.getBinData().addNewEmbeddedBinaryData("BIN0001.png", bytes,
                kr.dogfoot.hwplib.object.docinfo.bindata.BinDataCompress.ByStorageDefault);
        var binData = file.getDocInfo().addNewBinData();
        binData.getProperty().setType(
                kr.dogfoot.hwplib.object.docinfo.bindata.BinDataType.Embedding);
        binData.setBinDataID(1);
        binData.setExtensionForEmbedding("png");
        file.getDocInfo().getIDMappings()
                .setBinDataCount(file.getDocInfo().getBinDataList().size());

        Section section = file.getBodyText().getSectionList().get(0);
        Paragraph holder = 빈_문단(section);
        try {
            holder.getText().addExtendCharForGSO();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        holder.getCharShape().addParaCharShape(0, 0);
        holder.getHeader().setCharShapeCount(1);

        var picture = (kr.dogfoot.hwplib.object.bodytext.control.gso.ControlPicture)
                holder.addNewGsoControl(
                        kr.dogfoot.hwplib.object.bodytext.control.gso.GsoControlType.Picture);
        picture.getHeader().setWidth(size);
        picture.getHeader().setHeight(size / 2);
        picture.getShapeComponentPicture().getPictureInfo().setBinItemID(1);
    }

    byte[] hwpx(Consumer<HWPXFile> build) throws Exception {
        HWPXFile file = kr.dogfoot.hwpxlib.tool.blankfilemaker.BlankFileMaker.make();
        file.sectionXMLFileList().get(0).removeAllParas();
        build.accept(file);
        return HWPXWriter.toBytes(file);
    }

    Para 문단(HWPXFile file, String text) {
        Para para = file.sectionXMLFileList().get(0).addNewPara();
        para.paraPrIDRef(file.headerXMLFile().refList().paraProperties().get(0).id());
        글토막(para, text);
        return para;
    }

    Run 글토막(Para para, String text) {
        Run run = para.addNewRun();
        run.charPrIDRef("0");
        run.addNewT().addText(text);
        return run;
    }

    String 굵은_글자모양(HWPXFile file) {
        CharPr bold = file.headerXMLFile().refList().charProperties().get(0).clone();
        bold.id("100");
        bold.createBold();
        file.headerXMLFile().refList().charProperties().add(bold);
        return bold.id();
    }

    /** 밑줄·취소선 자리에 "없음" 이 값으로 들어 있는 글자모양. 실물 문서가 이렇다. */
    String 줄_없는_글자모양(HWPXFile file) {
        CharPr plain = file.headerXMLFile().refList().charProperties().get(0).clone();
        plain.id("101");
        plain.createUnderline();
        plain.underline().type(UnderlineType.NONE);
        plain.createStrikeout();
        plain.strikeout().shape(LineType2.NONE);
        file.headerXMLFile().refList().charProperties().add(plain);
        return plain.id();
    }

    /** 위아래 사이를 띄운 문단모양. 한글은 간격을 1/100 pt 로 적는다. */
    String 사이가_뜬_문단모양(HWPXFile file) {
        ParaPr spaced = file.headerXMLFile().refList().paraProperties().get(0).clone();
        spaced.id("101");
        if (spaced.margin() == null) {
            spaced.createMargin();
        }
        spaced.margin().createPrev();
        spaced.margin().prev().value(600);
        spaced.margin().createNext();
        spaced.margin().next().value(300);
        file.headerXMLFile().refList().paraProperties().add(spaced);
        return spaced.id();
    }

    /** 한 줄짜리 표. 칸마다 글이 한 문단씩 들어간다. */
    Table 표(HWPXFile file, String... cells) {
        Para holder = file.sectionXMLFileList().get(0).addNewPara();
        holder.paraPrIDRef(file.headerXMLFile().refList().paraProperties().get(0).id());
        Table table = holder.addNewRun().addNewTable();
        table.rowCnt((short) 1);
        table.colCnt((short) cells.length);

        Tr tr = table.addNewTr();
        for (String text : cells) {
            Tc tc = tr.addNewTc();
            tc.createSubList();
            Para para = tc.subList().addNewPara();
            para.paraPrIDRef(file.headerXMLFile().refList().paraProperties().get(0).id());
            글토막(para, text);
        }
        return table;
    }

    /**
     * 바탕색을 칠한 칸모양을 하나 더 두고 그 아이디를 준다.
     *
     * <p>hwpx 는 번호가 아니라 문자열 아이디로 가리키고, 색도 {@code #rrggbb}
     * 문자열이다. hwp(2진)와 담는 방식이 다르니 길을 따로 둔다.
     */
    String 바탕색_칸모양(HWPXFile file, String rgb) {
        kr.dogfoot.hwpxlib.object.content.header_xml.references.BorderFill fill =
                file.headerXMLFile().refList().borderFills().get(0).clone();
        fill.id("100");
        if (fill.fillBrush() == null) {
            fill.createFillBrush();
        }
        if (fill.fillBrush().winBrush() == null) {
            fill.fillBrush().createWinBrush();
        }
        fill.fillBrush().winBrush().faceColor(rgb);
        file.headerXMLFile().refList().borderFills().add(fill);
        return fill.id();
    }

    String 가운데_문단모양(HWPXFile file) {
        ParaPr center = file.headerXMLFile().refList().paraProperties().get(0).clone();
        center.id("100");
        if (center.align() == null) {
            center.createAlign();
        }
        center.align().horizontal(HorizontalAlign2.CENTER);
        file.headerXMLFile().refList().paraProperties().add(center);
        return center.id();
    }
}
