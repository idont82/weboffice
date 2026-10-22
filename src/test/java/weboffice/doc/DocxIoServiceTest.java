package weboffice.doc;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * docx 와 문단 사이 옮기기.
 *
 * <p>화면(Univer)이 쓰는 문단 모양을 그대로 담고 뺀다. 서버가 서식을 해석하지 않는
 * 것이 원칙이지만 docx 만은 예외다 - 워드 파일 형식이 우리 모양을 모른다.
 *
 * <p>왕복이 기준이다. 내보낸 것을 다시 읽어 같은 글과 같은 서식이 나와야 한다.
 */
class DocxIoServiceTest {

    private final DocxIoService service = new DocxIoService();

    @Test
    void 글을_담고_뺀다() throws Exception {
        List<DocParagraphDto> written = 왕복(List.of(
                new DocParagraphDto(0, "첫째 문단", null, null),
                new DocParagraphDto(1, "둘째 문단", null, null)));

        assertThat(written).extracting(DocParagraphDto::text)
                .containsExactly("첫째 문단", "둘째 문단");
        assertThat(written).extracting(DocParagraphDto::paragraphNo)
                .containsExactly(0, 1);
    }

    @Test
    void 굵게와_기울임이_살아남는다() throws Exception {
        List<DocParagraphDto> written = 왕복(List.of(
                new DocParagraphDto(0, "굵게보통",
                        null, "[{\"st\":0,\"ed\":2,\"ts\":{\"bl\":1}}]")));

        assertThat(written).hasSize(1);
        assertThat(written.get(0).text()).isEqualTo("굵게보통");
        assertThat(written.get(0).runs())
                .as("앞 두 글자만 굵어야 한다").contains("\"bl\":1");
    }

    @Test
    void 문단_정렬이_살아남는다() throws Exception {
        List<DocParagraphDto> written = 왕복(List.of(
                new DocParagraphDto(0, "가운데",
                        "{\"paragraphStyle\":{\"horizontalAlign\":2}}", null)));

        assertThat(written.get(0).style()).contains("\"horizontalAlign\":2");
    }

    @Test
    void 빈_문단도_자리를_지킨다() throws Exception {
        // 빈 줄을 버리면 문단 번호가 밀려 뒤쪽 글이 통째로 어긋난다.
        List<DocParagraphDto> written = 왕복(List.of(
                new DocParagraphDto(0, "위", null, null),
                new DocParagraphDto(1, "", null, null),
                new DocParagraphDto(2, "아래", null, null)));

        assertThat(written).extracting(DocParagraphDto::text)
                .containsExactly("위", "", "아래");
    }

    @Test
    void 표를_표로_가져온다() throws Exception {
        // 칸 글자를 문단으로 펼치면 세로로 길게 늘어서 원본과 너무 달라 보인다
        // (2026-08-12 지적). 표는 표로 담는다.
        DocContent content = service.readDocx(new ByteArrayInputStream(표가_든_문서()));

        assertThat(content.paragraphs()).extracting(DocParagraphDto::text)
                .containsExactly("머리말");
        assertThat(content.tables()).hasSize(1);
        assertThat(content.tables().get(0).cells())
                .containsExactly(List.of("구분", "내용"), List.of("보안", "접근 통제"));
    }

    @Test
    void 표가_어느_문단_뒤인지_알려_준다() throws Exception {
        // 자리를 잃으면 표가 문서 맨 끝으로 밀린다.
        DocContent content = service.readDocx(new ByteArrayInputStream(표가_든_문서()));

        assertThat(content.tables().get(0).afterParagraphNo()).isZero();
    }

    private byte[] 표가_든_문서() throws Exception {
        try (var document = new org.apache.poi.xwpf.usermodel.XWPFDocument();
             var out = new java.io.ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText("머리말");
            var table = document.createTable(2, 2);
            table.getRow(0).getCell(0).setText("구분");
            table.getRow(0).getCell(1).setText("내용");
            table.getRow(1).getCell(0).setText("보안");
            table.getRow(1).getCell(1).setText("접근 통제");
            document.write(out);
            return out.toByteArray();
        }
    }

    @Test
    void 글자_크기와_글꼴과_색을_가져온다() throws Exception {
        // 이것들이 빠지면 제목이 본문과 같은 크기·검정으로 나와 원본과 딴판이다
        // (2026-08-12 지적: 워드 파일을 열었더니 차이가 많다).
        DocContent content = service.readDocx(new ByteArrayInputStream(꾸민_글이_든_문서()));

        assertThat(content.paragraphs().get(0).runs())
                .contains("\"fs\":20")
                .contains("\"ff\":\"맑은 고딕\"")
                .contains("\"rgb\":\"#ff0000\"");
    }

    @Test
    void 문단_스타일에_적힌_글자_크기도_가져온다() throws Exception {
        // 워드 제목은 크기를 런에 적지 않고 문단 스타일(Heading 1)에만 적는다.
        // 스타일을 안 보면 제목이 본문 크기로 나온다.
        DocContent content = service.readDocx(new ByteArrayInputStream(제목_스타일_문서()));

        assertThat(content.paragraphs().get(0).runs()).contains("\"fs\":24");
    }

    @Test
    void 표의_칸_너비를_가져온다() throws Exception {
        // 너비를 잃으면 모든 칸이 똑같이 나뉜다. 첫 칸이 좁고 둘째 칸이 넓은
        // 원본과 딴판이다(2026-08-12 지적).
        DocContent content = service.readDocx(new ByteArrayInputStream(칸_너비가_다른_표()));

        assertThat(content.tables().get(0).columnWidths()).hasSize(2);
        assertThat(content.tables().get(0).columnWidths().get(1))
                .as("둘째 칸이 첫 칸의 세 배다")
                .isGreaterThan(content.tables().get(0).columnWidths().get(0) * 2);
    }

    @Test
    void 문단_사이_간격을_가져온다() throws Exception {
        // 간격이 빠지면 문단이 다닥다닥 붙어 원본보다 짧아 보이고 쪽 수도 달라진다.
        DocContent content = service.readDocx(new ByteArrayInputStream(간격이_있는_문서()));

        assertThat(content.paragraphs().get(0).style())
                .contains("\"spaceAbove\"").contains("\"spaceBelow\"");
    }

    @Test
    void 그림을_가져온다() throws Exception {
        // 그림이 빠지면 결재 문서의 도장·화면 갈무리가 통째로 사라진다.
        DocContent content = service.readDocx(new ByteArrayInputStream(그림이_든_문서()));

        assertThat(content.pictures()).hasSize(1);
        var picture = content.pictures().get(0);
        assertThat(picture.bytes()).isNotEmpty();
        assertThat(picture.afterParagraphNo()).isZero();
        // 워드는 크기를 EMU 로 적는다. 914400 EMU 가 1 인치(96px)다.
        assertThat(picture.width()).isEqualTo(96);
        assertThat(picture.height()).isEqualTo(48);
    }

    private byte[] 그림이_든_문서() throws Exception {
        try (var document = new org.apache.poi.xwpf.usermodel.XWPFDocument();
             var out = new java.io.ByteArrayOutputStream()) {
            var run = document.createParagraph().createRun();
            run.setText("그림 앞");
            run.addPicture(new ByteArrayInputStream(작은_png()),
                    org.apache.poi.xwpf.usermodel.Document.PICTURE_TYPE_PNG, "one.png",
                    org.apache.poi.util.Units.pixelToEMU(96),
                    org.apache.poi.util.Units.pixelToEMU(48));
            document.write(out);
            return out.toByteArray();
        }
    }

    /** 1x1 짜리 png. 그림인지 아닌지만 가리면 되므로 가장 작은 것을 쓴다. */
    private byte[] 작은_png() {
        return java.util.Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");
    }

    @Test
    void 가로로_합친_칸을_알려_준다() throws Exception {
        // 워드 표 머리는 칸을 합쳐 제목을 한 줄로 쓴다. 합치기를 잃으면 그 줄이
        // 좁은 칸에 눌려 세로로 늘어진다(2026-08-12 지적: 목차 표 머리).
        DocContent content = service.readDocx(new ByteArrayInputStream(가로로_합친_표()));

        DocTableDto table = content.tables().get(0);
        assertThat(table.cells().get(0)).containsExactly("머리", "");
        // 주인 칸은 두 칸을 차지하고, 가려진 칸은 0 이다(Univer 규칙).
        assertThat(table.columnSpans().get(0)).containsExactly(2, 0);
        assertThat(table.columnSpans().get(1)).containsExactly(1, 1);
    }

    @Test
    void 세로로_합친_칸을_알려_준다() throws Exception {
        DocContent content = service.readDocx(new ByteArrayInputStream(세로로_합친_표()));

        DocTableDto table = content.tables().get(0);
        assertThat(table.rowSpans().get(0)).containsExactly(2, 1);
        assertThat(table.rowSpans().get(1)).containsExactly(0, 1);
    }

    private byte[] 가로로_합친_표() throws Exception {
        try (var document = new org.apache.poi.xwpf.usermodel.XWPFDocument();
             var out = new java.io.ByteArrayOutputStream()) {
            var table = document.createTable(2, 2);
            var head = table.getRow(0);
            head.getCell(0).setText("머리");
            head.getCell(0).getCTTc().addNewTcPr().addNewGridSpan()
                    .setVal(java.math.BigInteger.valueOf(2));
            head.getCtRow().removeTc(1);
            table.getRow(1).getCell(0).setText("왼쪽");
            table.getRow(1).getCell(1).setText("오른쪽");
            document.write(out);
            return out.toByteArray();
        }
    }

    private byte[] 세로로_합친_표() throws Exception {
        try (var document = new org.apache.poi.xwpf.usermodel.XWPFDocument();
             var out = new java.io.ByteArrayOutputStream()) {
            var table = document.createTable(2, 2);
            table.getRow(0).getCell(0).setText("위아래");
            table.getRow(0).getCell(0).getCTTc().addNewTcPr().addNewVMerge()
                    .setVal(org.openxmlformats.schemas.wordprocessingml.x2006.main.STMerge.RESTART);
            table.getRow(1).getCell(0).getCTTc().addNewTcPr().addNewVMerge()
                    .setVal(org.openxmlformats.schemas.wordprocessingml.x2006.main.STMerge.CONTINUE);
            document.write(out);
            return out.toByteArray();
        }
    }

    @Test
    void 표_안_글자_서식을_가져온다() throws Exception {
        // 표 머리가 굵은 원본이 흔하다. 서식이 빠지면 머리와 본문이 같아 보인다.
        DocContent content = service.readDocx(new ByteArrayInputStream(꾸민_표()));

        DocTableDto table = content.tables().get(0);
        assertThat(table.cellRuns().get(0).get(0))
                .contains("\"bl\":1").contains("\"fs\":14");
        assertThat(table.cellRuns().get(0).get(1)).isNull();
    }

    private byte[] 꾸민_표() throws Exception {
        try (var document = new org.apache.poi.xwpf.usermodel.XWPFDocument();
             var out = new java.io.ByteArrayOutputStream()) {
            var table = document.createTable(1, 2);
            var head = table.getRow(0).getCell(0).getParagraphs().get(0).createRun();
            head.setText("머리");
            head.setBold(true);
            head.setFontSize(14);
            table.getRow(0).getCell(1).setText("보통");
            document.write(out);
            return out.toByteArray();
        }
    }

    @Test
    void 표가_가운데면_가운데라고_알려_준다() throws Exception {
        // 표지의 주황 막대가 표다. 정렬을 잃으면 가운데 것이 왼쪽에 붙어
        // 원본과 딴판이 된다(2026-08-20 지적).
        DocContent content = service.readDocx(new ByteArrayInputStream(가운데_표()));

        assertThat(content.tables().get(0).align()).isEqualTo(2);
    }

    @Test
    void 정렬을_안_적은_표는_왼쪽이다() throws Exception {
        DocContent content = service.readDocx(new ByteArrayInputStream(표가_든_문서()));

        assertThat(content.tables().get(0).align()).isEqualTo(1);
    }

    @Test
    void 못박은_줄_높이를_가져온다() throws Exception {
        // 막대처럼 쓰는 표는 줄 높이를 정확히 못박는다. 그것을 잃으면 4px 막대가
        // 글 한 줄 높이로 부푼다.
        DocContent content = service.readDocx(new ByteArrayInputStream(가운데_표()));

        assertThat(content.tables().get(0).rowHeights()).hasSize(1);
        assertThat(content.tables().get(0).rowHeights().get(0))
                .as("60 twip = 4px").isEqualTo(4d);
    }

    @Test
    void 높이를_안_적은_표는_줄_높이가_없다() throws Exception {
        DocContent content = service.readDocx(new ByteArrayInputStream(표가_든_문서()));

        assertThat(content.tables().get(0).rowHeights()).allSatisfy(
                height -> assertThat(height).isNull());
    }

    /** 폭 2880 twip, 가운데, 줄 높이 60 twip 못박음. 표지 막대와 같은 모양이다. */
    private byte[] 가운데_표() throws Exception {
        try (var document = new org.apache.poi.xwpf.usermodel.XWPFDocument();
             var out = new java.io.ByteArrayOutputStream()) {
            var table = document.createTable(1, 1);
            var properties = table.getCTTbl().getTblPr();
            properties.addNewJc().setVal(
                    org.openxmlformats.schemas.wordprocessingml.x2006.main.STJcTable.CENTER);
            var height = table.getRow(0).getCtRow().addNewTrPr().addNewTrHeight();
            height.setVal(java.math.BigInteger.valueOf(60));
            height.setHRule(
                    org.openxmlformats.schemas.wordprocessingml.x2006.main.STHeightRule.EXACT);
            document.write(out);
            return out.toByteArray();
        }
    }

    @Test
    void 문단_안_줄바꿈은_다음_줄로_넘긴다() throws Exception {
        // 표지의 두 줄이 한 줄로 붙어 나왔다(2026-08-20 지적). 워드는 한 문단
        // 안에서 <w:br/> 로 줄을 바꾼다.
        DocContent content = service.readDocx(new ByteArrayInputStream(줄바꿈이_든_문단()));

        assertThat(content.paragraphs()).extracting(DocParagraphDto::text)
                .containsExactly("첫 줄", "둘째 줄");
    }

    private byte[] 줄바꿈이_든_문단() throws Exception {
        try (var document = new org.apache.poi.xwpf.usermodel.XWPFDocument();
             var out = new java.io.ByteArrayOutputStream()) {
            var paragraph = document.createParagraph();
            paragraph.createRun().setText("첫 줄");
            paragraph.createRun().addBreak();
            paragraph.createRun().setText("둘째 줄");
            document.write(out);
            return out.toByteArray();
        }
    }

    @Test
    void 쪽_나눔은_다음_문단에_적는다() throws Exception {
        // 표지 다음에 목차가 오는 문서다. 쪽 나눔을 흘리면 목차가 표지 밑에
        // 붙는다(2026-08-20 실측).
        DocContent content = service.readDocx(new ByteArrayInputStream(쪽_나눔이_든_문서()));

        // 쪽 나눔만 든 문단은 줄을 만들지 않는다. 만들면 화면에 원본에 없는 빈
        // 줄이 생긴다(2026-08-25 지적). 워드로 내보낼 때만 그 자리를 만든다.
        assertThat(content.paragraphs()).extracting(DocParagraphDto::text)
                .containsExactly("표지", "목차");
        assertThat(content.paragraphs().get(1).style())
                .as("이 줄부터 새 쪽이다").contains("\"pageBreakBefore\":1");
        assertThat(content.paragraphs().get(0).style() == null
                || !content.paragraphs().get(0).style().contains("pageBreakBefore")).isTrue();
    }

    private byte[] 쪽_나눔이_든_문서() throws Exception {
        try (var document = new org.apache.poi.xwpf.usermodel.XWPFDocument();
             var out = new java.io.ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText("표지");
            document.createParagraph().createRun()
                    .addBreak(org.apache.poi.xwpf.usermodel.BreakType.PAGE);
            document.createParagraph().createRun().setText("목차");
            document.write(out);
            return out.toByteArray();
        }
    }

    @Test
    void 쪽_나눔은_docx_로_담고_다시_뺀다() throws Exception {
        // 내보내기에서 흘리면 웹에서 넘긴 쪽이 워드에서는 이어진다.
        List<DocParagraphDto> written = 왕복(List.of(
                new DocParagraphDto(0, "표지", null, null),
                new DocParagraphDto(1, "목차", "{\"pageBreakBefore\":1}", null)));

        assertThat(written).extracting(DocParagraphDto::text)
                .containsExactly("표지", "목차");
        assertThat(written.get(1).style()).contains("\"pageBreakBefore\":1");
    }

    @Test
    void 문단_스타일에_적힌_뒤_간격을_따른다() throws Exception {
        // 목차 스타일(TOC1)은 뒤 간격을 문서 기본보다 좁게 잡는다. 그것을 흘리면
        // 줄마다 벌어져 원본에서 한 쪽에 들어가던 목차가 두 쪽이 된다
        // (2026-08-21 지적).
        DocContent content = service.readDocx(new ByteArrayInputStream(목차_스타일_문서()));

        // 100 twip = 5pt = 6.67px
        assertThat(content.paragraphs().get(0).style())
                .contains("\"spaceBelow\":{\"v\":6.66");
    }

    @Test
    void 문단_스타일_글자_크기로_줄_높이를_잰다() throws Exception {
        // 줄 높이는 글자 크기에 배수를 곱해 잰다. 스타일에만 적힌 크기를 못 보면
        // 9pt 짜리 목차 줄이 11pt 높이로 그려진다.
        DocContent content = service.readDocx(new ByteArrayInputStream(목차_스타일_문서()));

        // 9pt * 1.15줄 * 글꼴 줄 높이 1.1 = 11.4pt = 15.18px
        assertThat(content.paragraphs().get(0).style()).contains("\"lineSpacing\":15.1");
    }

    /** 목차 스타일 하나. 뒤 간격 100 twip, 글자 9pt(18 반포인트)다. */
    private byte[] 목차_스타일_문서() throws Exception {
        try (var document = new org.apache.poi.xwpf.usermodel.XWPFDocument();
             var out = new java.io.ByteArrayOutputStream()) {
            var styles = document.createStyles();
            var all = org.openxmlformats.schemas.wordprocessingml.x2006.main
                    .CTStyles.Factory.newInstance();
            var paragraphDefault = all.addNewDocDefaults()
                    .addNewPPrDefault().addNewPPr().addNewSpacing();
            paragraphDefault.setAfter(java.math.BigInteger.valueOf(200));
            paragraphDefault.setLine(java.math.BigInteger.valueOf(276));
            paragraphDefault.setLineRule(org.openxmlformats.schemas.wordprocessingml
                    .x2006.main.STLineSpacingRule.AUTO);

            var toc = all.addNewStyle();
            toc.setStyleId("TOC1");
            toc.addNewName().setVal("toc 1");
            var tocParagraph = toc.addNewPPr();
            tocParagraph.addNewSpacing().setAfter(java.math.BigInteger.valueOf(100));
            var stop = tocParagraph.addNewTabs().addNewTab();
            stop.setVal(org.openxmlformats.schemas.wordprocessingml.x2006.main.STTabJc.RIGHT);
            stop.setLeader(org.openxmlformats.schemas.wordprocessingml.x2006.main
                    .STTabTlc.DOT);
            stop.setPos(java.math.BigInteger.valueOf(8630));
            toc.addNewRPr().addNewSz().setVal(java.math.BigInteger.valueOf(18));
            styles.setStyles(all);

            var sub = all.addNewStyle();
            sub.setStyleId("TOC2");
            sub.addNewName().setVal("toc 2");
            var subParagraph = sub.addNewPPr();
            subParagraph.addNewSpacing().setAfter(java.math.BigInteger.valueOf(100));
            subParagraph.addNewInd().setLeft(java.math.BigInteger.valueOf(220));
            styles.setStyles(all);

            var paragraph = document.createParagraph();
            paragraph.setStyle("TOC1");
            paragraph.createRun().setText("1. Introduction	3");
            var deeper = document.createParagraph();
            deeper.setStyle("TOC2");
            deeper.createRun().setText("1.1 About SecurityHub	3");
            document.write(out);
            return out.toByteArray();
        }
    }

    @Test
    void 문단_스타일에_적힌_들여쓰기를_따른다() throws Exception {
        // 목차 아래 단계(toc 2)는 스타일에만 들여쓰기를 적는다. 그것을 흘리면
        // 1.1 이 1 과 같은 자리에 붙어 층이 사라진다(2026-08-21 지적).
        DocContent content = service.readDocx(new ByteArrayInputStream(목차_스타일_문서()));

        // 220 twip = 14.67px
        assertThat(content.paragraphs().get(1).style())
                .contains("\"indentStart\":{\"v\":14.66");
    }

    @Test
    void 오른쪽_탭_정지와_점선을_가져온다() throws Exception {
        // 목차는 제목과 쪽 번호 사이를 점선으로 잇고 번호를 오른쪽 끝에 세운다.
        // 화면이 그것을 그리려면 탭이 멈추는 자리를 알아야 한다.
        DocContent content = service.readDocx(new ByteArrayInputStream(목차_스타일_문서()));

        // 8630 twip = 575.33px
        assertThat(content.paragraphs().get(0).style())
                .contains("\"tabRight\":575.3")
                .contains("\"tabLeader\":\"dot\"");
    }

    @Test
    void 문단_스타일에_적힌_앞_간격을_따른다() throws Exception {
        // 워드 제목 스타일은 앞 간격을 크게 잡는다(Heading 1 은 18pt). 그것을
        // 흘리면 제목이 앞 글에 바짝 붙고, 쪽이 원본보다 덜 차 목차 뒤에 제목이
        // 끼어든다(2026-08-21 지적).
        DocContent content = service.readDocx(new ByteArrayInputStream(제목_간격_문서()));

        // 360 twip = 18pt = 24px
        assertThat(content.paragraphs().get(0).style()).contains("\"spaceAbove\":{\"v\":24");
    }

    /** 앞 간격 360 twip 을 스타일에만 적은 제목 하나. */
    private byte[] 제목_간격_문서() throws Exception {
        try (var document = new org.apache.poi.xwpf.usermodel.XWPFDocument();
             var out = new java.io.ByteArrayOutputStream()) {
            var styles = document.createStyles();
            var all = org.openxmlformats.schemas.wordprocessingml.x2006.main
                    .CTStyles.Factory.newInstance();
            var heading = all.addNewStyle();
            heading.setStyleId("Heading1");
            heading.addNewName().setVal("heading 1");
            heading.addNewPPr().addNewSpacing()
                    .setBefore(java.math.BigInteger.valueOf(360));
            styles.setStyles(all);

            var paragraph = document.createParagraph();
            paragraph.setStyle("Heading1");
            paragraph.createRun().setText("1. Introduction");
            document.write(out);
            return out.toByteArray();
        }
    }

    @Test
    void 스타일에만_적힌_글머리표도_따른다() throws Exception {
        // 워드 목록 스타일(List Bullet)은 목록 번호를 스타일에 적는다. 문단만
        // 보면 글머리표가 통째로 사라진다(2026-08-21 지적).
        DocContent content = service.readDocx(new ByteArrayInputStream(글머리표_문서()));

        assertThat(content.paragraphs().get(0).style())
                .contains("\"listType\":\"BULLET_LIST\"");
    }

    @Test
    void 목록에서_빠진_문단에는_글머리표를_달지_않는다() throws Exception {
        // 워드는 목록에서 뺄 때 numId 를 0 으로 적는다. 그것을 목록으로 읽으면
        // 빈 줄에 점 하나가 덩그러니 남는다(2026-08-21 지적).
        DocContent content = service.readDocx(new ByteArrayInputStream(글머리표_문서()));

        String style = content.paragraphs().get(1).style();
        assertThat(style == null || !style.contains("listType"))
                .as("빈 줄에 점만 남으면 안 된다").isTrue();
    }

    /** 스타일에 글머리표를 적은 문단 하나와, 목록에서 뺀 문단 하나. */
    private byte[] 글머리표_문서() throws Exception {
        try (var document = new org.apache.poi.xwpf.usermodel.XWPFDocument();
             var out = new java.io.ByteArrayOutputStream()) {
            var styles = document.createStyles();
            var all = org.openxmlformats.schemas.wordprocessingml.x2006.main
                    .CTStyles.Factory.newInstance();
            var list = all.addNewStyle();
            list.setStyleId("ListBullet");
            list.addNewName().setVal("List Bullet");
            list.addNewPPr().addNewNumPr().addNewNumId()
                    .setVal(java.math.BigInteger.valueOf(1));
            styles.setStyles(all);

            var first = document.createParagraph();
            first.setStyle("ListBullet");
            first.createRun().setText("첫 항목");

            // 문단 서식을 한 자리에 함께 적는다. setStyle 로 만든 자리에 나중에
            // 더하면 워드가 내는 모양과 달라진다.
            var dropped = document.createParagraph();
            var properties = dropped.getCTP().addNewPPr();
            properties.addNewPStyle().setVal("ListBullet");
            properties.addNewNumPr().addNewNumId().setVal(java.math.BigInteger.ZERO);
            dropped.createRun().setText("");
            document.write(out);
            return out.toByteArray();
        }
    }

    @Test
    void 표_테두리를_가져온다() throws Exception {
        // 테두리가 없는 표를 선이 그어진 표로 그리면 원본과 딴판이다 - 줄 세우기
        // 용도로 쓴 표가 격자로 보인다.
        DocContent content = service.readDocx(new ByteArrayInputStream(테두리_없는_표()));

        assertThat(content.tables().get(0).hasBorders()).isFalse();
    }

    @Test
    void 선이_그어진_표는_테두리가_있다고_한다() throws Exception {
        DocContent content = service.readDocx(new ByteArrayInputStream(표가_든_문서()));

        assertThat(content.tables().get(0).hasBorders()).isTrue();
    }

    private byte[] 테두리_없는_표() throws Exception {
        try (var document = new org.apache.poi.xwpf.usermodel.XWPFDocument();
             var out = new java.io.ByteArrayOutputStream()) {
            var table = document.createTable(1, 2);
            table.setInsideHBorder(org.apache.poi.xwpf.usermodel.XWPFTable.XWPFBorderType.NONE,
                    0, 0, "auto");
            table.setInsideVBorder(org.apache.poi.xwpf.usermodel.XWPFTable.XWPFBorderType.NONE,
                    0, 0, "auto");
            table.setTopBorder(org.apache.poi.xwpf.usermodel.XWPFTable.XWPFBorderType.NONE,
                    0, 0, "auto");
            table.setBottomBorder(org.apache.poi.xwpf.usermodel.XWPFTable.XWPFBorderType.NONE,
                    0, 0, "auto");
            table.setLeftBorder(org.apache.poi.xwpf.usermodel.XWPFTable.XWPFBorderType.NONE,
                    0, 0, "auto");
            table.setRightBorder(org.apache.poi.xwpf.usermodel.XWPFTable.XWPFBorderType.NONE,
                    0, 0, "auto");
            table.getRow(0).getCell(0).setText("가");
            table.getRow(0).getCell(1).setText("나");
            document.write(out);
            return out.toByteArray();
        }
    }

    @Test
    void 표_머리의_바탕색을_가져온다() throws Exception {
        // 머리 줄이 회색인 표가 흔하다. 바탕색이 빠지면 어느 줄이 머리인지 안 보인다.
        DocContent content = service.readDocx(new ByteArrayInputStream(바탕색이_있는_표()));

        assertThat(content.tables().get(0).cellShades().get(0).get(0)).isEqualTo("#d9d9d9");
        assertThat(content.tables().get(0).cellShades().get(0).get(1)).isNull();
    }

    private byte[] 바탕색이_있는_표() throws Exception {
        try (var document = new org.apache.poi.xwpf.usermodel.XWPFDocument();
             var out = new java.io.ByteArrayOutputStream()) {
            var table = document.createTable(1, 2);
            table.getRow(0).getCell(0).setColor("D9D9D9");
            table.getRow(0).getCell(0).setText("머리");
            table.getRow(0).getCell(1).setText("보통");
            document.write(out);
            return out.toByteArray();
        }
    }

    @Test
    void 글머리표_문단을_가져온다() throws Exception {
        // 원본은 글머리표 목록인데 표시가 빠지면 층이 안 보이고 쪽 나눔도 달라진다
        // (2026-08-19 지적: 3쪽이 원본과 많이 달랐다).
        DocContent content = service.readDocx(new ByteArrayInputStream(목록_문서()));

        assertThat(content.paragraphs().get(0).style())
                .contains("\"bullet\"").contains("\"nestingLevel\":0");
        assertThat(content.paragraphs().get(1).style())
                .contains("\"nestingLevel\":1");
    }

    @Test
    void 목록_문단은_사이_간격을_빼다() throws Exception {
        // 워드는 같은 서식 문단끼리 붙여 그린다(contextualSpacing). 간격을 넣으면
        // 목록 하나마다 한 줄씩 벌어져 뒤쪽 글이 다음 쪽으로 밀린다.
        DocContent content = service.readDocx(new ByteArrayInputStream(목록_문서()));

        assertThat(content.paragraphs().get(0).style()).doesNotContain("spaceBelow");
        assertThat(content.paragraphs().get(0).style()).doesNotContain("spaceAbove");
    }

    private byte[] 목록_문서() throws Exception {
        try (var document = new org.apache.poi.xwpf.usermodel.XWPFDocument();
             var out = new java.io.ByteArrayOutputStream()) {
            for (int level = 0; level < 2; level += 1) {
                var paragraph = document.createParagraph();
                var properties = paragraph.getCTP().addNewPPr();
                var numbering = properties.addNewNumPr();
                numbering.addNewIlvl().setVal(java.math.BigInteger.valueOf(level));
                numbering.addNewNumId().setVal(java.math.BigInteger.valueOf(11));
                properties.addNewContextualSpacing();
                properties.addNewSpacing().setAfter(java.math.BigInteger.valueOf(160));
                paragraph.createRun().setText("목록 " + level);
            }
            document.write(out);
            return out.toByteArray();
        }
    }

    @Test
    void 서식에_적힌_줄_간격을_쓴다() throws Exception {
        // 목록 문단은 간격을 제 몸에 안 적고 쓰는 서식(ListParagraph)에 적는다.
        // 그것을 안 보면 문서 기본(1.08줄)이 붙어 줄마다 높아지고, 쪽마다 몇 줄씩
        // 덜 들어가 원본과 쪽 나눔이 어긋난다(2026-08-19 지적).
        DocContent content = service.readDocx(new ByteArrayInputStream(서식으로_간격을_정한_문서()));

        assertThat(content.paragraphs().get(0).style()).contains("\"lineSpacing\":14.6");
    }

    private byte[] 서식으로_간격을_정한_문서() throws Exception {
        try (var document = new org.apache.poi.xwpf.usermodel.XWPFDocument();
             var out = new java.io.ByteArrayOutputStream()) {
            var styles = document.createStyles();
            var style = org.openxmlformats.schemas.wordprocessingml.x2006.main.CTStyle
                    .Factory.newInstance();
            style.setStyleId("ListParagraph");
            style.addNewName().setVal("List Paragraph");
            var properties = style.addNewPPr();
            var spacing = properties.addNewSpacing();
            spacing.setLine(java.math.BigInteger.valueOf(240));
            spacing.setLineRule(org.openxmlformats.schemas.wordprocessingml.x2006.main
                    .STLineSpacingRule.AUTO);
            var wrapped = new org.apache.poi.xwpf.usermodel.XWPFStyle(style);
            wrapped.setStyleId("ListParagraph");
            styles.addStyle(wrapped);

            var paragraph = document.createParagraph();
            paragraph.setStyle("ListParagraph");
            paragraph.createRun().setText("목록 줄");
            document.write(out);
            return out.toByteArray();
        }
    }

    @Test
    void 목록과_다른_서식_사이는_간격을_둘다() throws Exception {
        // 워드는 <b>같은 서식간</b>에만 간격을 뺀다. 목록 끝과 다음 문단 사이까지
        // 붙이면 묶음이 원본보다 밀착해 보인다(2026-08-19 지적).
        DocContent content = service.readDocx(new ByteArrayInputStream(목록과_일반문단()));

        assertThat(content.paragraphs().get(0).style()).doesNotContain("spaceBelow");
        assertThat(content.paragraphs().get(1).style()).contains("spaceBelow");
    }

    private byte[] 목록과_일반문단() throws Exception {
        try (var document = new org.apache.poi.xwpf.usermodel.XWPFDocument();
             var out = new java.io.ByteArrayOutputStream()) {
            for (int at = 0; at < 2; at += 1) {
                var item = document.createParagraph();
                var properties = item.getCTP().addNewPPr();
                properties.addNewPStyle().setVal("ListParagraph");
                properties.addNewContextualSpacing();
                properties.addNewSpacing().setAfter(java.math.BigInteger.valueOf(240));
                item.createRun().setText("목록 " + at);
            }
            var plain = document.createParagraph();
            plain.createRun().setText("일반 문단");
            document.write(out);
            return out.toByteArray();
        }
    }

    @Test
    void 문서_기본_글자_크기를_가져온다() throws Exception {
        // 워드는 문서 전체 기본 크기를 styles.xml 의 docDefaults 에만 적는다.
        // 그것을 안 읽으면 표 안 글자가 화면 기본 크기로 나와 원본보다 크다
        // (2026-08-13 지적: 글자 크기가 많이 차이난다).
        DocContent content = service.readDocx(new ByteArrayInputStream(기본_크기_문서()));

        assertThat(content.paragraphs().get(0).runs()).contains("\"fs\":9");
        assertThat(content.tables().get(0).cellRuns().get(0).get(0)).contains("\"fs\":9");
    }

    private byte[] 기본_크기_문서() throws Exception {
        try (var document = new org.apache.poi.xwpf.usermodel.XWPFDocument();
             var out = new java.io.ByteArrayOutputStream()) {
            var styles = document.createStyles();
            var ctStyles = org.openxmlformats.schemas.wordprocessingml.x2006.main.CTStyles
                    .Factory.newInstance();
            // 워드는 크기를 반 포인트로 적는다. 18 이 9pt 다.
            ctStyles.addNewDocDefaults().addNewRPrDefault().addNewRPr()
                    .addNewSz().setVal(java.math.BigInteger.valueOf(18));
            styles.setStyles(ctStyles);

            document.createParagraph().createRun().setText("글");
            var table = document.createTable(1, 1);
            table.getRow(0).getCell(0).setText("칸");
            document.write(out);
            return out.toByteArray();
        }
    }

    @Test
    void 표_스타일에_적힌_글자_크기도_가져온다() throws Exception {
        // 워드 표는 글자 크기를 칸이 아니라 표 스타일에만 적기도 한다. 그것을 안
        // 보면 표 글자가 문서 기본 크기로 나와 원본보다 크다(2026-08-13 지적).
        DocContent content = service.readDocx(new ByteArrayInputStream(표_스타일_문서()));

        assertThat(content.tables().get(0).cellRuns().get(0).get(0)).contains("\"fs\":9");
    }

    private byte[] 표_스타일_문서() throws Exception {
        try (var document = new org.apache.poi.xwpf.usermodel.XWPFDocument();
             var out = new java.io.ByteArrayOutputStream()) {
            var styles = document.createStyles();
            var ctStyles = org.openxmlformats.schemas.wordprocessingml.x2006.main.CTStyles
                    .Factory.newInstance();
            var style = ctStyles.addNewStyle();
            style.setStyleId("WoTable");
            style.addNewName().setVal("Wo Table");
            // 워드는 크기를 반 포인트로 적는다. 18 이 9pt 다.
            style.addNewRPr().addNewSz().setVal(java.math.BigInteger.valueOf(18));
            styles.setStyles(ctStyles);

            var table = document.createTable(1, 1);
            table.setStyleID("WoTable");
            table.getRow(0).getCell(0).setText("칸");
            document.write(out);
            return out.toByteArray();
        }
    }

    @Test
    void 쪽_여백과_크기를_가져온다() throws Exception {
        // 여백이 다르면 본문 폭이 달라져 표와 그림이 원본과 다른 자리에 앉는다
        // (2026-08-13 지적: 표 크기가 다르다).
        DocContent content = service.readDocx(new ByteArrayInputStream(여백을_준_문서()));

        assertThat(content.page()).isNotNull();
        // 워드는 길이를 twip 으로 적는다. 1440 twip 이 1 인치(96px)다.
        assertThat(content.page().marginLeft()).isEqualTo(96);
        assertThat(content.page().marginRight()).isEqualTo(48);
        assertThat(content.page().width()).isEqualTo(816);
    }

    private byte[] 여백을_준_문서() throws Exception {
        try (var document = new org.apache.poi.xwpf.usermodel.XWPFDocument();
             var out = new java.io.ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText("글");
            var sectPr = document.getDocument().getBody().addNewSectPr();
            var margin = sectPr.addNewPgMar();
            margin.setLeft(java.math.BigInteger.valueOf(1440));
            margin.setRight(java.math.BigInteger.valueOf(720));
            margin.setTop(java.math.BigInteger.valueOf(1440));
            margin.setBottom(java.math.BigInteger.valueOf(1440));
            var size = sectPr.addNewPgSz();
            size.setW(java.math.BigInteger.valueOf(12240));
            size.setH(java.math.BigInteger.valueOf(15840));
            document.write(out);
            return out.toByteArray();
        }
    }

    @Test
    void 문단_들여쓰기를_가져온다() throws Exception {
        // 번호 매긴 목록과 인용은 들여쓰기로 층을 나타낸다. 빠지면 모두 왼쪽에
        // 붙어 층이 사라진다.
        DocContent content = service.readDocx(new ByteArrayInputStream(들여쓴_문서()));

        assertThat(content.paragraphs().get(0).style())
                .contains("\"indentStart\"").contains("\"indentFirstLine\"");
    }

    private byte[] 들여쓴_문서() throws Exception {
        try (var document = new org.apache.poi.xwpf.usermodel.XWPFDocument();
             var out = new java.io.ByteArrayOutputStream()) {
            var paragraph = document.createParagraph();
            // 워드는 들여쓰기도 twip 으로 적는다. 720 twip 이 0.5 인치다.
            paragraph.setIndentationLeft(720);
            paragraph.setFirstLineIndent(360);
            paragraph.createRun().setText("들여 쓴 문단");
            document.write(out);
            return out.toByteArray();
        }
    }

    @Test
    void 줄_간격을_가져온다() throws Exception {
        // 워드 기본은 1.08 줄이다. 1.0 으로 그리면 쪽마다 몇 줄씩 더 들어가
        // 원본과 쪽 나눔이 달라진다.
        DocContent content = service.readDocx(new ByteArrayInputStream(줄_간격_문서()));

        assertThat(content.paragraphs().get(0).style()).contains("\"lineSpacing\":22");
        // 규칙을 빼면 화면이 줄 간격을 어떻게 읽을지 몰라 문서를 통째로 못 그린다
        // - 글자 하나 없이 하얗게 떴다(2026-08-12 실측).
        assertThat(content.paragraphs().get(0).style()).contains("\"spacingRule\":2");
    }

    private byte[] 줄_간격_문서() throws Exception {
        try (var document = new org.apache.poi.xwpf.usermodel.XWPFDocument();
             var out = new java.io.ByteArrayOutputStream()) {
            var paragraph = document.createParagraph();
            // 워드는 줄 간격을 240 분의 1 줄로 적는다. 360 이 1.5 줄이다.
            paragraph.setSpacingBetween(1.5,
                    org.apache.poi.xwpf.usermodel.LineSpacingRule.AUTO);
            paragraph.createRun().setText("줄 사이가 뜬 문단");
            document.write(out);
            return out.toByteArray();
        }
    }

    private byte[] 간격이_있는_문서() throws Exception {
        try (var document = new org.apache.poi.xwpf.usermodel.XWPFDocument();
             var out = new java.io.ByteArrayOutputStream()) {
            var paragraph = document.createParagraph();
            // 워드는 간격도 twip 으로 적는다. 240 twip 이 12pt 다.
            paragraph.setSpacingBefore(240);
            paragraph.setSpacingAfter(120);
            paragraph.createRun().setText("사이가 뜬 문단");
            document.write(out);
            return out.toByteArray();
        }
    }

    private byte[] 꾸민_글이_든_문서() throws Exception {
        try (var document = new org.apache.poi.xwpf.usermodel.XWPFDocument();
             var out = new java.io.ByteArrayOutputStream()) {
            var run = document.createParagraph().createRun();
            run.setText("제목");
            run.setFontSize(20);
            run.setFontFamily("맑은 고딕");
            run.setColor("FF0000");
            document.write(out);
            return out.toByteArray();
        }
    }

    private byte[] 제목_스타일_문서() throws Exception {
        try (var document = new org.apache.poi.xwpf.usermodel.XWPFDocument();
             var out = new java.io.ByteArrayOutputStream()) {
            var styles = document.createStyles();
            var style = org.openxmlformats.schemas.wordprocessingml.x2006.main.CTStyle.Factory
                    .newInstance();
            style.setStyleId("Heading1");
            style.addNewName().setVal("heading 1");
            style.addNewRPr().addNewSz().setVal(java.math.BigInteger.valueOf(48));
            styles.addStyle(new org.apache.poi.xwpf.usermodel.XWPFStyle(style));

            var paragraph = document.createParagraph();
            paragraph.setStyle("Heading1");
            paragraph.createRun().setText("제목");
            document.write(out);
            return out.toByteArray();
        }
    }

    private byte[] 칸_너비가_다른_표() throws Exception {
        try (var document = new org.apache.poi.xwpf.usermodel.XWPFDocument();
             var out = new java.io.ByteArrayOutputStream()) {
            var table = document.createTable(1, 2);
            var grid = table.getCTTbl().addNewTblGrid();
            grid.addNewGridCol().setW(java.math.BigInteger.valueOf(1500));
            grid.addNewGridCol().setW(java.math.BigInteger.valueOf(6000));
            table.getRow(0).getCell(0).setText("좁게");
            table.getRow(0).getCell(1).setText("넓게");
            document.write(out);
            return out.toByteArray();
        }
    }

    @Test
    void 문단이_없으면_빈_파일을_만든다() throws Exception {
        byte[] docx = service.toDocx(List.of());

        assertThat(docx).isNotEmpty();
        assertThat(service.fromDocx(new ByteArrayInputStream(docx))).isEmpty();
    }

    private List<DocParagraphDto> 왕복(List<DocParagraphDto> rows) throws Exception {
        return service.fromDocx(new ByteArrayInputStream(service.toDocx(rows)));
    }

    @Test
    void 머릿글과_꼬릿글을_가져온다() throws Exception {
        // 워드 문서의 머릿글은 쪽마다 되풀이되는 글이다. 안 가져오면 화면에
        // 아무것도 안 나온다(2026-08-13 지적: 머릿글은 안 나오네).
        DocContent content = service.readDocx(new ByteArrayInputStream(머릿글_문서()));

        assertThat(content.header()).isNotNull();
        assertThat(content.header().paragraphs()).extracting(DocParagraphDto::text)
                .containsExactly("쪽마다 나오는 글");
        assertThat(content.footer()).isNotNull();
        assertThat(content.footer().paragraphs()).extracting(DocParagraphDto::text)
                .containsExactly("아래에 나오는 글");
    }

    @Test
    void 머릿글_표도_가져온다() throws Exception {
        // 이 문서의 머릿글은 표 하나다. 표를 놓치면 머릿글이 빈 줄로 나온다.
        DocContent content = service.readDocx(new ByteArrayInputStream(머릿글_표_문서()));

        assertThat(content.header().tables()).hasSize(1);
        assertThat(content.header().tables().get(0).cells().get(0).get(0)).isEqualTo("칸");
    }

    private byte[] 머릿글_문서() throws Exception {
        try (var document = new org.apache.poi.xwpf.usermodel.XWPFDocument();
             var out = new java.io.ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText("본문");
            var policy = new org.apache.poi.xwpf.model.XWPFHeaderFooterPolicy(document);
            var header = policy.createHeader(
                    org.apache.poi.xwpf.model.XWPFHeaderFooterPolicy.DEFAULT);
            header.createParagraph().createRun().setText("쪽마다 나오는 글");
            var footer = policy.createFooter(
                    org.apache.poi.xwpf.model.XWPFHeaderFooterPolicy.DEFAULT);
            footer.createParagraph().createRun().setText("아래에 나오는 글");
            document.write(out);
            return out.toByteArray();
        }
    }

    private byte[] 바닥글이_컨트롤에_든_문서() throws Exception {
        try (var document = new org.apache.poi.xwpf.usermodel.XWPFDocument();
             var out = new java.io.ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText("본문");
            var policy = new org.apache.poi.xwpf.model.XWPFHeaderFooterPolicy(document);
            var footer = policy.createFooter(
                    org.apache.poi.xwpf.model.XWPFHeaderFooterPolicy.DEFAULT);
            // 컨트롤로 감싼 문단 하나와 맨 문단 하나.
            var sdt = footer._getHdrFtr().addNewSdt();
            var inner = sdt.addNewSdtContent().addNewP();
            inner.addNewR().addNewT().setStringValue("쪽 번호 줄");
            footer.createParagraph().createRun().setText("그냥 줄");
            document.write(out);
            return out.toByteArray();
        }
    }

    private byte[] 쪽_번호_바닥글_문서() throws Exception {
        try (var document = new org.apache.poi.xwpf.usermodel.XWPFDocument();
             var out = new java.io.ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText("본문");
            var policy = new org.apache.poi.xwpf.model.XWPFHeaderFooterPolicy(document);
            var footer = policy.createFooter(
                    org.apache.poi.xwpf.model.XWPFHeaderFooterPolicy.DEFAULT);
            var line = footer.createParagraph();
            line.createRun().setText("Page ");
            복합필드(line, "PAGE", "1");
            line.createRun().setText(" of ");
            복합필드(line, "NUMPAGES", "1");
            document.write(out);
            return out.toByteArray();
        }
    }

    /** 워드가 쓰는 복합 필드 한 벌(begin - instrText - separate - 결과 - end). */
    private void 복합필드(org.apache.poi.xwpf.usermodel.XWPFParagraph line,
                      String instruction, String cached) {
        var begin = line.createRun().getCTR();
        begin.addNewFldChar().setFldCharType(
                org.openxmlformats.schemas.wordprocessingml.x2006.main.STFldCharType.BEGIN);
        var instr = line.createRun().getCTR();
        var text = instr.addNewInstrText();
        text.setStringValue(" " + instruction + " ");
        var separate = line.createRun().getCTR();
        separate.addNewFldChar().setFldCharType(
                org.openxmlformats.schemas.wordprocessingml.x2006.main.STFldCharType.SEPARATE);
        line.createRun().setText(cached);
        var end = line.createRun().getCTR();
        end.addNewFldChar().setFldCharType(
                org.openxmlformats.schemas.wordprocessingml.x2006.main.STFldCharType.END);
    }

    private byte[] 머릿글_표_문서() throws Exception {
        try (var document = new org.apache.poi.xwpf.usermodel.XWPFDocument();
             var out = new java.io.ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText("본문");
            var policy = new org.apache.poi.xwpf.model.XWPFHeaderFooterPolicy(document);
            var header = policy.createHeader(
                    org.apache.poi.xwpf.model.XWPFHeaderFooterPolicy.DEFAULT);
            var table = header.createTable(1, 1);
            table.getRow(0).getCell(0).setText("칸");
            document.write(out);
            return out.toByteArray();
        }
    }

    @Test
    void 첫_쪽에만_다른_머릿글을_쓰는지_가져온다() throws Exception {
        // 이 문서는 첫 쪽에 머릿글·꼬릿글이 없다(titlePg). 그것을 안 보면 첫 쪽에도
        // 머릿글이 찍혀 원본과 다르다(2026-08-13 지적).
        DocContent content = service.readDocx(new ByteArrayInputStream(첫_쪽이_다른_문서()));

        assertThat(content.titlePage()).isTrue();
        assertThat(content.header()).isNotNull();
        assertThat(content.firstHeader()).isNull();
    }

    @Test
    void 콘텐츠_컨트롤_안의_바닥글_문단도_읽는다() throws Exception {
        // 워드는 쪽 번호 줄을 콘텐츠 컨트롤(SDT)로 감싸 두곤 한다. 그것을 안 보면
        // 그 줄이 통째로 빠진다(2026-10-16 실측: "Page 2 of 8" 이 사라졌다).
        DocContent content = service.readDocx(
                new ByteArrayInputStream(바닥글이_컨트롤에_든_문서()));

        assertThat(content.footer()).isNotNull();
        assertThat(content.footer().paragraphs()).extracting(DocParagraphDto::text)
                .contains("쪽 번호 줄", "그냥 줄");
    }

    @Test
    void 쪽_번호_필드를_표시해_담는다() throws Exception {
        // 워드의 PAGE·NUMPAGES 는 쪽마다 값이 달라진다. 캐시된 숫자를 글자로만
        // 담으면 모든 쪽에 같은 숫자가 찍힌다 - 어디가 필드였는지 적어 보낸다.
        DocContent content = service.readDocx(new ByteArrayInputStream(쪽_번호_바닥글_문서()));

        var line = content.footer().paragraphs().get(0);
        assertThat(line.text()).isEqualTo("Page 1 of 1");
        assertThat(line.runs()).contains("\"fld\":\"PAGE\"", "\"fld\":\"NUMPAGES\"");
    }

    @Test
    void 첫_쪽이_같으면_표시하지_않는다() throws Exception {
        DocContent content = service.readDocx(new ByteArrayInputStream(머릿글_문서()));

        assertThat(content.titlePage()).isFalse();
    }

    private byte[] 첫_쪽이_다른_문서() throws Exception {
        try (var document = new org.apache.poi.xwpf.usermodel.XWPFDocument();
             var out = new java.io.ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText("본문");
            var policy = new org.apache.poi.xwpf.model.XWPFHeaderFooterPolicy(document);
            var header = policy.createHeader(
                    org.apache.poi.xwpf.model.XWPFHeaderFooterPolicy.DEFAULT);
            header.createParagraph().createRun().setText("쪽마다 나오는 글");
            document.getDocument().getBody().getSectPr().addNewTitlePg();
            document.write(out);
            return out.toByteArray();
        }
    }

    @Test
    void 문서_기본_문단_간격도_옮긴다() throws Exception {
        // 워드는 문단 간격을 문단마다 안 적고 문서 기본(docDefaults)에만 적는다.
        // 그것을 안 보면 빈 줄이 원본보다 낮아 쪽 나눔이 어긋난다 - 다음 쪽 표가
        // 위에 안 붙는다(2026-08-13 지적).
        DocContent content = service.readDocx(new ByteArrayInputStream(기본_간격_문서()));

        String style = content.paragraphs().get(0).style();
        // 화면은 이 값을 픽셀로 읽는다. 포인트로 적으면 문단마다 2.7px 씩 낮아져
        // 쪽마다 한 줄씩 더 들어간다 - 다음 쪽에 있어야 할 제목이 앞 쪽 밑에
        // 붙는다(2026-08-19 지적). 8pt = 10.666px.
        assertThat(style).contains("\"spaceBelow\":{\"v\":10.6");
        assertThat(style).contains("\"lineSpacing\":15.8");
    }

    private byte[] 기본_간격_문서() throws Exception {
        try (var document = new org.apache.poi.xwpf.usermodel.XWPFDocument();
             var out = new java.io.ByteArrayOutputStream()) {
            var styles = document.createStyles();
            var ctStyles = org.openxmlformats.schemas.wordprocessingml.x2006.main.CTStyles
                    .Factory.newInstance();
            var spacing = ctStyles.addNewDocDefaults().addNewPPrDefault().addNewPPr()
                    .addNewSpacing();
            // 워드 기본이다. 뒤 간격 160 twip(8pt), 줄 간격 259(1.08줄).
            spacing.setAfter(java.math.BigInteger.valueOf(160));
            spacing.setLine(java.math.BigInteger.valueOf(259));
            spacing.setLineRule(org.openxmlformats.schemas.wordprocessingml.x2006.main
                    .STLineSpacingRule.AUTO);
            styles.setStyles(ctStyles);

            document.createParagraph().createRun().setText("글");
            document.write(out);
            return out.toByteArray();
        }
    }

    @Test
    void 내보낼_때_표를_함께_싣는다() throws Exception {
        // 내보내기가 문단만 실어 표가 통째로 사라졌다(2026-08-24 지적).
        var table = new DocTableDto(0, List.of(List.of("구분", "값"), List.of("보안", "통제")),
                List.of(120.0, 240.0),
                List.of(java.util.Arrays.asList("#1b365d", null),
                        java.util.Arrays.asList(null, null)),
                List.of(List.of(1, 1), List.of(1, 1)), List.of(List.of(1, 1), List.of(1, 1)),
                true, List.of(), DocTableDto.ALIGN_CENTER, List.of());

        byte[] docx = service.toDocx(
                List.of(new DocParagraphDto(0, "머리말", null, null)), List.of(table), List.of());

        DocContent back = service.readDocx(new ByteArrayInputStream(docx));
        assertThat(back.tables()).hasSize(1);
        var written = back.tables().get(0);
        assertThat(written.cells()).containsExactly(
                List.of("구분", "값"), List.of("보안", "통제"));
        assertThat(written.afterParagraphNo()).isZero();
        assertThat(written.cellShades().get(0).get(0)).isEqualToIgnoringCase("#1b365d");
        assertThat(written.align()).isEqualTo(DocTableDto.ALIGN_CENTER);
    }

    @Test
    void 내보낼_때_선_없는_표는_선을_긋지_않는다() throws Exception {
        var table = new DocTableDto(-1, List.of(List.of("왼", "오")),
                List.of(), List.of(), List.of(), List.of(), false, List.of(),
                DocTableDto.ALIGN_LEFT, List.of());

        byte[] docx = service.toDocx(List.of(), List.of(table), List.of());

        DocContent back = service.readDocx(new ByteArrayInputStream(docx));
        assertThat(back.tables().get(0).hasBorders()).isFalse();
    }

    @Test
    void 내보낼_때_그림을_함께_싣는다() throws Exception {
        // 그림도 문단만 싣느라 사라졌다(2026-08-24 지적).
        var picture = new DocxIoService.Drawing(0, 작은_png(), 96, 48);

        byte[] docx = service.toDocx(
                List.of(new DocParagraphDto(0, "그림 앞", null, null)), List.of(), List.of(picture));

        DocContent back = service.readDocx(new ByteArrayInputStream(docx));
        assertThat(back.pictures()).hasSize(1);
        assertThat(back.pictures().get(0).bytes()).isNotEmpty();
        assertThat(back.pictures().get(0).afterParagraphNo()).isZero();
    }


    @Test
    void 내보낼_때_글자_크기와_색과_글꼴을_싣는다() throws Exception {
        // 내보내기가 굵기만 실어 크기·색·글꼴이 통째로 빠졌다(2026-08-24 지적).
        String runs = "[{\"st\":0,\"ed\":4,\"ts\":{\"fs\":18,\"ff\":\"Gulim\","
                + "\"cl\":{\"rgb\":\"#ff0000\"}}}]";

        byte[] docx = service.toDocx(List.of(new DocParagraphDto(0, "빨간 글", null, runs)));

        var back = service.fromDocx(new ByteArrayInputStream(docx));
        var written = read(back.get(0).runs()).get(0).path("ts");
        assertThat(written.path("fs").asInt(0)).isEqualTo(18);
        assertThat(written.path("ff").asString("")).isEqualTo("Gulim");
        assertThat(written.path("cl").path("rgb").asString(""))
                .isEqualToIgnoringCase("#ff0000");
    }

    /** 서식 JSON 을 마디로. 테스트에서만 쓴다. */
    private java.util.List<tools.jackson.databind.JsonNode> read(String runs) {
        var mapper = tools.jackson.databind.json.JsonMapper.builder().build();
        java.util.List<tools.jackson.databind.JsonNode> nodes = new java.util.ArrayList<>();
        for (var one : mapper.readTree(runs == null ? "[]" : runs)) {
            nodes.add(one);
        }
        return nodes;
    }


    @Test
    void 내보낼_때_줄_간격과_문단_간격과_들여쓰기를_싣는다() throws Exception {
        // 내보내기가 정렬만 실어 간격·들여쓰기가 빠졌다. 원본은 성긴 글인데
        // 내려받으면 빽빽했다(2026-08-24 지적).
        String style = "{\"paragraphStyle\":{\"spaceAbove\":{\"v\":16},"
                + "\"spaceBelow\":{\"v\":8},\"indentStart\":{\"v\":48},"
                + "\"indentFirstLine\":{\"v\":24},\"lineSpacing\":24,\"spacingRule\":2}}";

        byte[] docx = service.toDocx(List.of(new DocParagraphDto(0, "성긴 글", style, null)));

        try (var document = new org.apache.poi.xwpf.usermodel.XWPFDocument(
                new ByteArrayInputStream(docx))) {
            var written = document.getParagraphArray(0);
            // px 를 트윕으로 되돌린 값이다(1px = 15twip).
            assertThat(written.getSpacingBefore()).isEqualTo(240);
            assertThat(written.getSpacingAfter()).isEqualTo(120);
            assertThat(written.getIndentationLeft()).isEqualTo(720);
            assertThat(written.getIndentationFirstLine()).isEqualTo(360);
            // 줄 높이는 배수로 적는다 - 못박으면 글꼴 실제 높이와 어긋난다.
            assertThat(written.getSpacingLineRule())
                    .isEqualTo(org.apache.poi.xwpf.usermodel.LineSpacingRule.AUTO);
        }
    }


    @Test
    void 내보낼_때_글머리_기호와_번호를_싣는다() throws Exception {
        // 내보내기가 글머리표를 흘려 목록이 맨 글줄이 됐다(2026-08-24 지적).
        String bullet = "{\"bullet\":{\"listType\":\"BULLET_LIST\","
                + "\"listId\":\"wo-list-1\",\"nestingLevel\":0}}";
        String numbered = "{\"bullet\":{\"listType\":\"ORDER_LIST\","
                + "\"listId\":\"wo-list-2\",\"nestingLevel\":1}}";

        byte[] docx = service.toDocx(List.of(
                new DocParagraphDto(0, "점 항목", bullet, null),
                new DocParagraphDto(1, "번호 항목", numbered, null)));

        var back = service.fromDocx(new ByteArrayInputStream(docx));
        var first = read2(back.get(0).style()).path("bullet");
        assertThat(first.path("listType").asString("")).isEqualTo("BULLET_LIST");
        var second = read2(back.get(1).style()).path("bullet");
        assertThat(second.path("listType").asString("")).isEqualTo("ORDER_LIST");
        assertThat(second.path("nestingLevel").asInt(0)).isEqualTo(1);
    }

    @Test
    void 내보낼_때_목차의_점선_탭을_싣는다() throws Exception {
        String style = "{\"tabRight\":432,\"tabLeader\":\"dot\"}";

        byte[] docx = service.toDocx(List.of(new DocParagraphDto(0, "머리말	7", style, null)));

        var written = read2(service.fromDocx(new ByteArrayInputStream(docx)).get(0).style());
        assertThat(written.path("tabRight").asDouble(0)).isEqualTo(432);
        assertThat(written.path("tabLeader").asString("")).isEqualTo("dot");
    }

    /** 서식 JSON 을 마디 하나로. 테스트에서만 쓴다. */
    private tools.jackson.databind.JsonNode read2(String style) {
        return tools.jackson.databind.json.JsonMapper.builder().build()
                .readTree(style == null ? "{}" : style);
    }


    @Test
    void 내보낼_때_용지_크기와_여백을_싣는다() throws Exception {
        // 내보내기가 용지를 흘려 A4 원본이 워드 기본(레터)으로 나왔다(2026-08-24 지적).
        var page = new DocContent.DocPage(794, 1123, 96, 96, 72, 72);

        byte[] docx = service.toDocx(List.of(new DocParagraphDto(0, "글", null, null)),
                List.of(), List.of(), page, null, null);

        DocContent back = service.readDocx(new ByteArrayInputStream(docx));
        assertThat(back.page().width()).isCloseTo(794, within(1.0));
        assertThat(back.page().marginLeft()).isCloseTo(96, within(1.0));
        assertThat(back.page().marginTop()).isCloseTo(72, within(1.0));
    }

    @Test
    void 내보낼_때_머릿글과_꼬릿글을_싣는다() throws Exception {
        var header = new DocContent.DocEdge(
                List.of(new DocParagraphDto(0, "쪽마다 나오는 글", null, null)), List.of());
        var footer = new DocContent.DocEdge(
                List.of(new DocParagraphDto(0, "아래 글", null, null)), List.of());

        byte[] docx = service.toDocx(List.of(new DocParagraphDto(0, "본문", null, null)),
                List.of(), List.of(), null, header, footer);

        DocContent back = service.readDocx(new ByteArrayInputStream(docx));
        assertThat(back.header()).isNotNull();
        assertThat(back.header().paragraphs()).extracting(DocParagraphDto::text)
                .containsExactly("쪽마다 나오는 글");
        assertThat(back.footer().paragraphs()).extracting(DocParagraphDto::text)
                .containsExactly("아래 글");
    }


    @Test
    void 내보낼_때_표_칸의_글자_서식과_너비를_싣는다() throws Exception {
        // 칸 글자를 맨 글로 쓰면 표 안이 죄다 기본 크기가 되고, 너비를 안 적으면
        // 표가 쪽 너비로 늘어난다(2026-08-24 실측: 표지의 주황 막대가 통째로 늘었다).
        String runs = "[{\"st\":0,\"ed\":2,\"ts\":{\"fs\":9,\"bl\":1,"
                + "\"cl\":{\"rgb\":\"#ffffff\"}}}]";
        var table = new DocTableDto(-1, List.of(List.of("구분", "값")),
                List.of(120.0, 240.0), List.of(), List.of(), List.of(), true,
                List.of(java.util.Arrays.asList(runs, null)),
                DocTableDto.ALIGN_LEFT, List.of());

        byte[] docx = service.toDocx(List.of(), List.of(table), List.of());

        try (var document = new org.apache.poi.xwpf.usermodel.XWPFDocument(
                new ByteArrayInputStream(docx))) {
            var written = document.getTableArray(0);
            var head = written.getRow(0).getCell(0).getParagraphs().get(0).getRuns().get(0);
            assertThat(head.getFontSizeAsDouble()).isEqualTo(9.0);
            assertThat(head.isBold()).isTrue();
            assertThat(head.getColor()).isEqualToIgnoringCase("ffffff");
            // 120px + 240px = 360px = 5400twip
            assertThat(written.getCTTbl().getTblPr().getTblW().getW().toString())
                    .isEqualTo("5400");
        }
    }


    @Test
    void 그림이_든_문단은_줄_높이를_못박지_않는다() throws Exception {
        // 줄 높이를 못박은 문단에 그림을 넣으면 워드가 그 높이로 그림을 잘라
        // 보여 준다(2026-08-24 지적: 이미지가 잘려 보인다).
        String style = "{\"paragraphStyle\":{\"lineSpacing\":18,\"spacingRule\":2}}";
        var picture = new DocxIoService.Drawing(0, 작은_png(), 96, 96);

        byte[] docx = service.toDocx(List.of(new DocParagraphDto(0, "그림 줄", style, null)),
                List.of(), List.of(picture));

        try (var document = new org.apache.poi.xwpf.usermodel.XWPFDocument(
                new ByteArrayInputStream(docx))) {
            var written = document.getParagraphArray(0);
            var spacing = written.getCTP().getPPr() == null
                    ? null : written.getCTP().getPPr().getSpacing();
            assertThat(spacing == null || !spacing.isSetLine()).isTrue();
        }
    }


    @Test
    void 본문보다_넓은_그림은_줄여_넣는다() throws Exception {
        // 워드는 인라인 그림을 스스로 줄이지 않는다. 본문보다 넓으면 오른쪽이
        // 잘려 보인다(2026-08-24 지적).
        var page = new DocContent.DocPage(794, 1123, 96, 96, 72, 72);
        var picture = new DocxIoService.Drawing(0, 작은_png(), 1200, 600);

        byte[] docx = service.toDocx(List.of(new DocParagraphDto(0, "그림", null, null)),
                List.of(), List.of(picture), page, null, null);

        try (var document = new org.apache.poi.xwpf.usermodel.XWPFDocument(
                new ByteArrayInputStream(docx))) {
            var drawn = document.getParagraphArray(0).getRuns().stream()
                    .flatMap(one -> one.getEmbeddedPictures().stream())
                    .findFirst()
                    .orElseThrow();
            // 본문 폭은 794 - 96 - 96 = 602px 다. 비율은 그대로 반이다.
            double wide = drawn.getCTPicture().getSpPr().getXfrm().getExt().getCx()
                    / (double) org.apache.poi.util.Units.EMU_PER_POINT;
            double high = drawn.getCTPicture().getSpPr().getXfrm().getExt().getCy()
                    / (double) org.apache.poi.util.Units.EMU_PER_POINT;
            assertThat(wide).isCloseTo(602 * 0.75, within(1.0));
            assertThat(high / wide).isCloseTo(0.5, within(0.01));
        }
    }


    @Test
    void 줄_높이는_못박지_않고_배수로_적는다() throws Exception {
        // 화면용으로 못박아 둔 픽셀 높이를 워드에 그대로 못박으면 글꼴 실제 높이와
        // 어긋나 쪽 수가 늘어난다(2026-08-24 지적: 26쪽, 높이가 이상하다).
        String style = "{\"paragraphStyle\":{\"lineSpacing\":24,\"spacingRule\":2}}";
        String runs = "[{\"st\":0,\"ed\":2,\"ts\":{\"fs\":12}}]";

        byte[] docx = service.toDocx(List.of(new DocParagraphDto(0, "글줄", style, runs)));

        try (var document = new org.apache.poi.xwpf.usermodel.XWPFDocument(
                new ByteArrayInputStream(docx))) {
            var spacing = document.getParagraphArray(0).getCTP().getPPr().getSpacing();
            // 12pt = 16px, 한 줄 높이 16 * 1.1 = 17.6px. 24 / 17.6 = 1.36 배.
            assertThat(spacing.getLineRule())
                    .isEqualTo(org.openxmlformats.schemas.wordprocessingml.x2006.main
                            .STLineSpacingRule.AUTO);
            assertThat(Integer.parseInt(spacing.getLine().toString()))
                    .isBetween(320, 335);
        }
    }


    @Test
    void 서식을_안_가리킨_문단은_기본_서식을_따른다() throws Exception {
        // 워드는 서식을 안 적은 문단에 기본 서식(Normal)을 준다. 그것을 못 보고
        // 문서 기본값(docDefaults)으로 떨어지면 간격이 커져 24쪽이 28쪽이 된다
        // (2026-08-24 지적).
        DocContent content = service.readDocx(new ByteArrayInputStream(기본_서식_문서()));

        var style = read2(content.paragraphs().get(0).style()).path("paragraphStyle");
        // Normal 의 뒤 간격 120트윕 = 6pt = 8px 다. 문서 기본값은 200트윕이다.
        assertThat(style.path("spaceBelow").path("v").asDouble(0)).isCloseTo(8, within(0.5));
    }

    /** 문서 기본값과 기본 서식이 다른 문서. 문단은 서식을 가리키지 않는다. */
    private byte[] 기본_서식_문서() throws Exception {
        try (var document = new org.apache.poi.xwpf.usermodel.XWPFDocument();
             var out = new java.io.ByteArrayOutputStream()) {
            var styles = document.createStyles();
            var made = org.openxmlformats.schemas.wordprocessingml.x2006.main
                    .CTStyles.Factory.newInstance();
            var defaults = made.addNewDocDefaults().addNewPPrDefault().addNewPPr()
                    .addNewSpacing();
            defaults.setAfter(java.math.BigInteger.valueOf(200));
            defaults.setLine(java.math.BigInteger.valueOf(276));
            defaults.setLineRule(org.openxmlformats.schemas.wordprocessingml.x2006.main
                    .STLineSpacingRule.AUTO);
            var normal = made.addNewStyle();
            normal.setType(org.openxmlformats.schemas.wordprocessingml.x2006.main
                    .STStyleType.PARAGRAPH);
            normal.setStyleId("Normal");
            normal.setDefault(true);
            normal.addNewName().setVal("Normal");
            var spacing = normal.addNewPPr().addNewSpacing();
            spacing.setAfter(java.math.BigInteger.valueOf(120));
            spacing.setLine(java.math.BigInteger.valueOf(259));
            spacing.setLineRule(org.openxmlformats.schemas.wordprocessingml.x2006.main
                    .STLineSpacingRule.AUTO);
            styles.setStyles(made);
            // POI 는 XML 을 갈아 끼워도 서식 목록을 다시 읽지 않는다. 목록에도 넣는다.
            styles.addStyle(new org.apache.poi.xwpf.usermodel.XWPFStyle(
                    made.getStyleArray(0), styles));

            document.createParagraph().createRun().setText("보통 글");
            document.write(out);
            return out.toByteArray();
        }
    }


    @Test
    void 내보낼_때_탭은_글자가_아니라_탭으로_적는다() throws Exception {
        // 탭을 글자로 적으면 워드가 탭 정지로 보내지 않는다. 목차 줄이 길어져
        // 두 줄이 되고 그 뒤 쪽이 통째로 밀린다(2026-08-24 지적).
        String style = "{\"tabRight\":432,\"tabLeader\":\"dot\"}";
        String text = "1. Introduction" + "	" + "3";

        byte[] docx = service.toDocx(List.of(new DocParagraphDto(0, text, style, null)));

        try (var document = new org.apache.poi.xwpf.usermodel.XWPFDocument(
                new ByteArrayInputStream(docx))) {
            String xml = document.getParagraphArray(0).getCTP().xmlText();
            assertThat(xml).contains("<w:tab/>");
            // 글자 자리(<w:t>)에는 탭이 남지 않는다.
            assertThat(xml.replaceAll("(?s)<w:tab/>", "")).doesNotContain("	");
        }
    }


    @Test
    void 줄바꿈으로_쪼갠_줄은_내보낼_때_다시_붙는다() throws Exception {
        // 한 문단 안 줄바꿈(<w:br/>)을 화면에서 문단으로 쪼갠다. 내보낼 때 되돌리지
        // 않으면 문단이 늘고 문단 뒤 간격이 한 번 더 붙어 쪽이 밀린다(2026-08-24 지적).
        DocContent read = service.readDocx(new ByteArrayInputStream(줄바꿈이_든_문단()));
        assertThat(read.paragraphs()).hasSize(2);

        byte[] docx = service.toDocx(read.paragraphs());

        try (var document = new org.apache.poi.xwpf.usermodel.XWPFDocument(
                new ByteArrayInputStream(docx))) {
            assertThat(document.getParagraphs()).hasSize(1);
            assertThat(document.getParagraphArray(0).getCTP().xmlText())
                    .contains("<w:br/>");
            assertThat(document.getParagraphArray(0).getText()).contains("첫 줄");
            assertThat(document.getParagraphArray(0).getText()).contains("둘째 줄");
        }
    }



    @Test
    void 문단_표시에도_글자_크기를_적는다() throws Exception {
        // 워드는 문단 표시(¶)의 글자 크기까지 보고 줄 높이를 잡는다. 9pt 목차 줄에
        // 문단 표시만 11pt 로 남으면 줄마다 높아져 쪽이 밀린다(2026-08-24 실측:
        // 목차 35줄에서 141pt 가 벌어졌다).
        String runs = "[{\"st\":0,\"ed\":3,\"ts\":{\"fs\":9}}]";

        byte[] docx = service.toDocx(List.of(new DocParagraphDto(0, "작은 글", null, runs)));

        try (var document = new org.apache.poi.xwpf.usermodel.XWPFDocument(
                new ByteArrayInputStream(docx))) {
            var mark = document.getParagraphArray(0).getCTP().getPPr().getRPr();
            assertThat(mark).isNotNull();
            assertThat(mark.getSzArray(0).getVal().toString()).isEqualTo("18");
        }
    }


    @Test
    void 문서_기본_글꼴과_크기를_읽고_다시_싣는다() throws Exception {
        // 내보낸 문서에 문서 기본(styles.xml)이 없으면 워드가 제 기본 글꼴로
        // 그린다. 맑은 고딕은 행간이 커서 9pt 목차 줄이 16.8pt 가 됐다
        // (2026-08-24 실측: 원본은 11.7pt).
        Path source = Path.of("D:/user/itits/etc/MPM Security Hub Documentation Guide.docx");
        org.junit.jupiter.api.Assumptions.assumeTrue(java.nio.file.Files.exists(source));

        DocContent read = service.readDocx(
                new ByteArrayInputStream(java.nio.file.Files.readAllBytes(source)));
        assertThat(read.defaults()).isNotNull();
        assertThat(read.defaults().fontSize()).isEqualTo(11);
        assertThat(read.defaults().font()).isNotBlank();
        assertThat(read.defaults().linePitch()).isEqualTo(360);

        byte[] docx = service.toDocx(read.paragraphs(), read.tables(), List.of(),
                read.page(), null, null, read.defaults());

        try (var document = new org.apache.poi.xwpf.usermodel.XWPFDocument(
                new ByteArrayInputStream(docx))) {
            var styles = document.getStyles();
            assertThat(styles).as("문서 기본 서식이 실려 있다").isNotNull();
            String xml = document.getDocument().getBody().getSectPr().xmlText();
            assertThat(xml).contains("docGrid");
        }
    }


    @Test
    void 새_쪽을_여는_문단은_쪽_나눔_속성으로_적는다() throws Exception {
        // 문단 앞에 쪽 나눔 글자를 넣으면 그 문단이 앞 쪽 끝에 걸린다. 워드가
        // 새 쪽 맨 위로 올리도록 문단 속성으로 적는다(2026-08-25 실측).
        String style = "{\"pageBreakBefore\":1}";

        byte[] docx = service.toDocx(List.of(
                new DocParagraphDto(0, "앞 쪽", null, null),
                new DocParagraphDto(1, "새 쪽", style, null)));

        try (var document = new org.apache.poi.xwpf.usermodel.XWPFDocument(
                new ByteArrayInputStream(docx))) {
            var second = document.getParagraphArray(1);
            assertThat(second.isPageBreak()).isTrue();
            assertThat(second.getCTP().xmlText()).doesNotContain("w:type=\"page\"");
        }
    }


    @Test
    void 줄_간격_배수를_그대로_담고_되돌린다() throws Exception {
        // 픽셀 높이만 담으면 내보낼 때 글자 크기로 되나눠야 한다. 제목처럼 크기가
        // 다르면 배수가 틀어져 줄 간격이 0.5 배가 됐다(2026-08-25 실측).
        DocContent read = service.readDocx(new ByteArrayInputStream(목차_스타일_문서()));

        var style = read2(read.paragraphs().get(0).style()).path("paragraphStyle");
        assertThat(style.path("lineMultiple").asDouble(0)).isGreaterThan(0);

        byte[] docx = service.toDocx(read.paragraphs());
        try (var document = new org.apache.poi.xwpf.usermodel.XWPFDocument(
                new ByteArrayInputStream(docx))) {
            var spacing = document.getParagraphArray(0).getCTP().getPPr().getSpacing();
            double written = Double.parseDouble(spacing.getLine().toString()) / 240;
            assertThat(written).isCloseTo(style.path("lineMultiple").asDouble(0),
                    within(0.02));
        }
    }


    @Test
    void 쪽_나눔만_든_문단은_줄을_만들지_않는다() throws Exception {
        // 만들면 화면에 원본에 없는 빈 줄이 생긴다(2026-08-25 지적).
        DocContent read = service.readDocx(new ByteArrayInputStream(빈_쪽나눔_문서()));

        assertThat(read.paragraphs()).extracting(DocParagraphDto::text)
                .containsExactly("앞 글", "새 쪽");
        assertThat(read.paragraphs().get(1).style()).contains("pageBreakBefore");

    }

    /** 앞 글 · 쪽 나눔만 든 빈 문단 · 새 쪽 글. */
    private byte[] 빈_쪽나눔_문서() throws Exception {
        try (var document = new org.apache.poi.xwpf.usermodel.XWPFDocument();
             var out = new java.io.ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText("앞 글");
            document.createParagraph().createRun()
                    .addBreak(org.apache.poi.xwpf.usermodel.BreakType.PAGE);
            document.createParagraph().createRun().setText("새 쪽");
            document.write(out);
            return out.toByteArray();
        }
    }


    @Test
    void 제목의_붙이기_규칙을_읽고_다시_싣는다() throws Exception {
        // 워드 제목 서식은 "다음 문단과 붙이기" 를 갖는다. 흘리면 제목이 쪽 끝에
        // 홀로 남고 뒤 내용만 넘어가, 원본보다 쪽이 덜 든다(2026-08-25 실측).
        Path source = Path.of("D:/user/itits/etc/MPM Security Hub Documentation Guide.docx");
        org.junit.jupiter.api.Assumptions.assumeTrue(java.nio.file.Files.exists(source));

        DocContent read = service.readDocx(
                new ByteArrayInputStream(java.nio.file.Files.readAllBytes(source)));
        var heading = read.paragraphs().stream()
                .filter(row -> "1. Introduction".equals(row.text()))
                .findFirst()
                .orElseThrow();
        assertThat(read2(heading.style()).path("paragraphStyle").path("keepNext").asInt(0))
                .isEqualTo(1);

        byte[] docx = service.toDocx(List.of(heading));
        try (var document = new org.apache.poi.xwpf.usermodel.XWPFDocument(
                new ByteArrayInputStream(docx))) {
            var properties = document.getParagraphArray(0).getCTP().getPPr();
            assertThat(properties.isSetKeepNext()).isTrue();
            assertThat(properties.isSetKeepLines()).isTrue();
        }
    }


    @Test
    void 첫_쪽만_다른_머릿글도_내보낸다() throws Exception {
        // 첫 쪽에만 다른 머릿글을 쓰는 문서(titlePg)다. 그것을 흘리면 둘째 쪽부터
        // 머릿글이 통째로 사라진다(2026-08-25 실측).
        var header = new DocContent.DocEdge(
                List.of(new DocParagraphDto(0, "쪽마다 나오는 글", null, null)), List.of());
        var firstHeader = new DocContent.DocEdge(
                List.of(new DocParagraphDto(0, "첫 쪽만", null, null)), List.of());

        byte[] docx = service.toDocx(List.of(new DocParagraphDto(0, "본문", null, null)),
                List.of(), List.of(), null, header, null, null, true, firstHeader, null);

        DocContent back = service.readDocx(new ByteArrayInputStream(docx));
        assertThat(back.titlePage()).isTrue();
        assertThat(back.header().paragraphs()).extracting(DocParagraphDto::text)
                .containsExactly("쪽마다 나오는 글");
        assertThat(back.firstHeader()).isNotNull();
        assertThat(back.firstHeader().paragraphs()).extracting(DocParagraphDto::text)
                .containsExactly("첫 쪽만");
    }


    @Test
    void 못박은_줄_높이는_규칙까지_적는다() throws Exception {
        // 규칙 없이 높이만 적으면 워드가 "최소 높이" 로 본다. 3pt 짜리 색 막대가
        // 글자 높이만큼 굵어졌다(2026-08-25 실측).
        var table = new DocTableDto(-1, List.of(List.of("")), List.of(192.0),
                List.of(List.of("#e87502")), List.of(), List.of(), false, List.of(),
                DocTableDto.ALIGN_CENTER, List.of(4.0));

        byte[] docx = service.toDocx(List.of(), List.of(table), List.of());

        try (var document = new org.apache.poi.xwpf.usermodel.XWPFDocument(
                new ByteArrayInputStream(docx))) {
            var row = document.getTableArray(0).getRow(0).getCtRow();
            assertThat(row.getTrPr().getTrHeightArray(0).getHRule())
                    .isEqualTo(org.openxmlformats.schemas.wordprocessingml.x2006.main
                            .STHeightRule.EXACT);
        }
    }


    @Test
    void 테두리도_서식도_없는_표는_선이_없다() throws Exception {
        // 워드 기본 표 서식은 선이 없다. "안 적혔으니 선이 있다" 로 보면 색 막대에
        // 검은 테두리가 생긴다(2026-08-25 실측).
        DocContent read = service.readDocx(new ByteArrayInputStream(민무늬_표_문서()));

        assertThat(read.tables().get(0).hasBorders()).isFalse();
    }

    /** 테두리도 표 서식도 안 적은 표. 워드가 선 없이 그린다. */
    private byte[] 민무늬_표_문서() throws Exception {
        try (var document = new org.apache.poi.xwpf.usermodel.XWPFDocument();
             var out = new java.io.ByteArrayOutputStream()) {
            var table = document.createTable(1, 1);
            var properties = table.getCTTbl().getTblPr();
            if (properties != null && properties.isSetTblBorders()) {
                properties.unsetTblBorders();
            }
            table.getRow(0).getCell(0).setText("막대");
            document.write(out);
            return out.toByteArray();
        }
    }




    @Test
    void 머릿글_안_표도_함께_내보낸다() throws Exception {
        // 이 문서의 머릿글은 로고와 글을 표 한 줄에 담는다. 표를 안 쓰면 머릿글이
        // 통째로 빈다(2026-08-25 실측).
        var header = new DocContent.DocEdge(
                List.of(new DocParagraphDto(0, "", null, null)),
                List.of(new DocTableDto(-1, List.of(List.of("SecurityHub Application Guide")))));

        byte[] docx = service.toDocx(List.of(new DocParagraphDto(0, "본문", null, null)),
                List.of(), List.of(), null, header, null);

        DocContent back = service.readDocx(new ByteArrayInputStream(docx));
        assertThat(back.header()).isNotNull();
        assertThat(back.header().tables()).hasSize(1);
        assertThat(back.header().tables().get(0).cells().get(0).get(0))
                .isEqualTo("SecurityHub Application Guide");
    }


    @Test
    void 문서_끝에_오는_표도_내보낸다() throws Exception {
        // 마지막 문단보다 뒤에 놓인 표는 어느 문단 뒤에도 안 붙어 통째로 빠졌다
        // (2026-08-25 실측: 연락처 표가 사라졌다).
        var table = new DocTableDto(9, List.of(List.of("연락처")));

        byte[] docx = service.toDocx(List.of(new DocParagraphDto(0, "본문", null, null)),
                List.of(table), List.of());

        DocContent back = service.readDocx(new ByteArrayInputStream(docx));
        assertThat(back.tables()).hasSize(1);
        assertThat(back.tables().get(0).cells().get(0).get(0)).isEqualTo("연락처");
    }


    @Test
    void 표_칸의_가로_맞춤을_읽고_다시_싣는다() throws Exception {
        // 머릿글은 로고와 글을 표 한 줄에 담고 글을 오른쪽에 세운다. 맞춤을 흘리면
        // 글이 가운데로 온다(2026-08-25 지적).
        DocContent read = service.readDocx(new ByteArrayInputStream(오른쪽_맞춘_표()));

        var table = read.tables().get(0);
        assertThat(table.cellAligns().get(0).get(0)).isEqualTo(DocTableDto.ALIGN_RIGHT);

        byte[] docx = service.toDocx(List.of(), List.of(table), List.of());
        DocContent back = service.readDocx(new ByteArrayInputStream(docx));
        assertThat(back.tables().get(0).cellAligns().get(0).get(0))
                .isEqualTo(DocTableDto.ALIGN_RIGHT);
    }

    /** 칸 글을 오른쪽에 세운 표. */
    private byte[] 오른쪽_맞춘_표() throws Exception {
        try (var document = new org.apache.poi.xwpf.usermodel.XWPFDocument();
             var out = new java.io.ByteArrayOutputStream()) {
            var table = document.createTable(1, 1);
            var cell = table.getRow(0).getCell(0);
            var paragraph = cell.getParagraphs().get(0);
            paragraph.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.RIGHT);
            paragraph.createRun().setText("오른쪽");
            document.write(out);
            return out.toByteArray();
        }
    }


    @Test
    void 머릿글_그림도_읽고_다시_싣는다() throws Exception {
        // 이 문서의 머릿글에는 회사 로고가 있다. 안 담으면 내려받은 문서에서
        // 로고가 통째로 빠진다(2026-08-25 지적).
        DocContent read = service.readDocx(new ByteArrayInputStream(로고가_든_머릿글()));

        assertThat(read.header()).isNotNull();
        assertThat(read.header().pictures()).hasSize(1);
        assertThat(read.header().pictures().get(0).bytes()).isNotEmpty();

        byte[] docx = service.toDocx(List.of(new DocParagraphDto(0, "본문", null, null)),
                List.of(), List.of(), null, read.header(), null);

        DocContent back = service.readDocx(new ByteArrayInputStream(docx));
        assertThat(back.header().pictures()).hasSize(1);
    }

    /** 머릿글에 그림을 넣은 문서. */
    private byte[] 로고가_든_머릿글() throws Exception {
        try (var document = new org.apache.poi.xwpf.usermodel.XWPFDocument();
             var out = new java.io.ByteArrayOutputStream()) {
            var policy = new org.apache.poi.xwpf.model.XWPFHeaderFooterPolicy(document);
            var header = policy.createHeader(
                    org.apache.poi.xwpf.model.XWPFHeaderFooterPolicy.DEFAULT);
            var line = header.getParagraphs().isEmpty()
                    ? header.createParagraph() : header.getParagraphs().get(0);
            var run = line.createRun();
            run.addPicture(new ByteArrayInputStream(작은_png()),
                    org.apache.poi.xwpf.usermodel.Document.PICTURE_TYPE_PNG, "logo.png",
                    org.apache.poi.util.Units.toEMU(60), org.apache.poi.util.Units.toEMU(20));
            document.createParagraph().createRun().setText("본문");
            document.write(out);
            return out.toByteArray();
        }
    }


    @Test
    void 머릿글_여백도_읽고_다시_싣는다() throws Exception {
        // 머릿글 여백을 빼먹으면 워드가 0 으로 보고 머릿글이 본문을 밀어
        // 쪽이 늘어난다(2026-08-25 실측: 24쪽이 25쪽).
        byte[] source;
        try (var document = new org.apache.poi.xwpf.usermodel.XWPFDocument();
             var out = new java.io.ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText("글");
            var sectPr = document.getDocument().getBody().addNewSectPr();
            var size = sectPr.addNewPgSz();
            size.setW(java.math.BigInteger.valueOf(12240));
            size.setH(java.math.BigInteger.valueOf(15840));
            var margin = sectPr.addNewPgMar();
            margin.setLeft(java.math.BigInteger.valueOf(1800));
            margin.setRight(java.math.BigInteger.valueOf(1800));
            margin.setTop(java.math.BigInteger.valueOf(1440));
            margin.setBottom(java.math.BigInteger.valueOf(1440));
            margin.setHeader(java.math.BigInteger.valueOf(720));
            margin.setFooter(java.math.BigInteger.valueOf(720));
            document.write(out);
            source = out.toByteArray();
        }

        DocContent read = service.readDocx(new ByteArrayInputStream(source));
        assertThat(read.page().marginHeader()).isEqualTo(48);

        byte[] again = service.toDocx(read.paragraphs(), read.tables(), List.of(),
                read.page(), null, null);
        try (var document = new org.apache.poi.xwpf.usermodel.XWPFDocument(
                new ByteArrayInputStream(again))) {
            var margin = document.getDocument().getBody().getSectPr().getPgMar();
            assertThat(String.valueOf(margin.getHeader())).isEqualTo("720");
            assertThat(String.valueOf(margin.getFooter())).isEqualTo("720");
        }
    }

    @Test
    void 기본_줄간격은_Normal_스타일이_먼저다() throws Exception {
        // 문서 기본값만 보면 워드가 다른 줄 높이로 그려 쪽이 늘어난다
        // (2026-08-25 실측: 줄 높이 12.95pt 를 13.8pt 로 그려 24쪽이 25쪽).
        byte[] source;
        try (var document = new org.apache.poi.xwpf.usermodel.XWPFDocument();
             var out = new java.io.ByteArrayOutputStream()) {
            var styles = document.createStyles();
            var made = org.openxmlformats.schemas.wordprocessingml.x2006.main
                    .CTStyles.Factory.newInstance();
            var basics = made.addNewDocDefaults().addNewPPrDefault().addNewPPr()
                    .addNewSpacing();
            basics.setLine(java.math.BigInteger.valueOf(276));
            basics.setLineRule(org.openxmlformats.schemas.wordprocessingml.x2006.main
                    .STLineSpacingRule.AUTO);
            basics.setAfter(java.math.BigInteger.valueOf(200));
            var normal = made.addNewStyle();
            normal.setStyleId("Normal");
            normal.setDefault(true);
            normal.setType(org.openxmlformats.schemas.wordprocessingml.x2006.main
                    .STStyleType.PARAGRAPH);
            normal.addNewName().setVal("Normal");
            var own = normal.addNewPPr().addNewSpacing();
            own.setLine(java.math.BigInteger.valueOf(259));
            own.setLineRule(org.openxmlformats.schemas.wordprocessingml.x2006.main
                    .STLineSpacingRule.AUTO);
            own.setAfter(java.math.BigInteger.valueOf(120));
            styles.setStyles(made);
            document.createParagraph().createRun().setText("글");
            document.write(out);
            source = out.toByteArray();
        }

        DocContent read = service.readDocx(new ByteArrayInputStream(source));

        assertThat(read.defaults().lineSpacing()).isCloseTo(259 / 240.0, within(0.001));
        assertThat(read.defaults().spaceAfter()).isCloseTo(6.0, within(0.01));
    }

    @Test
    void 칸_안_여러_문단은_줄을_나눠_담는다() throws Exception {
        // 박스(한 칸 표) 안 문단을 이어 붙이면 줄이 덜 접혀 표가 낮아지고
        // 뒷글이 앞 쪽으로 올라온다(2026-08-25 지적: 표가 다음 쪽으로 넘어간다).
        byte[] source;
        try (var document = new org.apache.poi.xwpf.usermodel.XWPFDocument();
             var out = new java.io.ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText("앞");
            var table = document.createTable(1, 1);
            var cell = table.getRow(0).getCell(0);
            cell.getParagraphs().get(0).createRun().setText("첫 줄");
            cell.addParagraph().createRun().setText("둘째 줄");
            document.write(out);
            source = out.toByteArray();
        }

        DocContent read = service.readDocx(new ByteArrayInputStream(source));
        assertThat(read.tables()).hasSize(1);
        assertThat(read.tables().get(0).cells().get(0).get(0))
                .isEqualTo("첫 줄" + (char) 10 + "둘째 줄");

        byte[] again = service.toDocx(read.paragraphs(), read.tables(), List.of(),
                read.page(), null, null);
        try (var document = new org.apache.poi.xwpf.usermodel.XWPFDocument(
                new ByteArrayInputStream(again))) {
            var cell = document.getTables().get(0).getRow(0).getCell(0);
            assertThat(cell.getParagraphs()).hasSize(2);
            assertThat(cell.getParagraphs().get(0).getText()).isEqualTo("첫 줄");
            assertThat(cell.getParagraphs().get(1).getText()).isEqualTo("둘째 줄");
        }
    }

    @Test
    void 칸_안_문단_간격도_읽고_다시_싣는다() throws Exception {
        // 칸 문단 간격을 흘리면 박스가 원본보다 낮아져 뒷글이 앞 쪽으로 올라온다
        // (2026-08-25 지적: 박스가 다음 쪽으로 넘어간다).
        byte[] source;
        try (var document = new org.apache.poi.xwpf.usermodel.XWPFDocument();
             var out = new java.io.ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText("앞");
            var table = document.createTable(1, 1);
            var cell = table.getRow(0).getCell(0);
            var first = cell.getParagraphs().get(0);
            first.createRun().setText("첫 줄");
            first.getCTP().addNewPPr().addNewSpacing()
                    .setAfter(java.math.BigInteger.valueOf(60));
            cell.addParagraph().createRun().setText("둘째 줄");
            document.write(out);
            source = out.toByteArray();
        }

        DocContent read = service.readDocx(new ByteArrayInputStream(source));
        // 60 twip 은 3pt 이고 화면에서는 4px 다.
        assertThat(read.tables().get(0).cellSpaces().get(0).get(0)).isCloseTo(4, within(0.1));

        byte[] again = service.toDocx(read.paragraphs(), read.tables(), List.of(),
                read.page(), null, null);
        try (var document = new org.apache.poi.xwpf.usermodel.XWPFDocument(
                new ByteArrayInputStream(again))) {
            var cell = document.getTables().get(0).getRow(0).getCell(0);
            assertThat(cell.getParagraphs().get(0).getSpacingAfter()).isEqualTo(60);
        }
    }

    @Test
    void 구역_나눔도_새_쪽으로_읽는다() throws Exception {
        // 표지 문단이 구역 나눔으로 끝나는 문서가 있다. 그것을 흘리면 표지와
        // 목차가 한 쪽에 겹친다(2026-08-25 지적: 첫 쪽 그림이 글을 덮었다).
        byte[] source;
        try (var document = new org.apache.poi.xwpf.usermodel.XWPFDocument();
             var out = new java.io.ByteArrayOutputStream()) {
            var cover = document.createParagraph();
            cover.createRun().setText("표지");
            cover.getCTP().addNewPPr().addNewSectPr();
            document.createParagraph().createRun().setText("목차");
            document.write(out);
            source = out.toByteArray();
        }

        DocContent read = service.readDocx(new ByteArrayInputStream(source));

        assertThat(read.paragraphs()).hasSize(2);
        assertThat(read.paragraphs().get(1).style()).contains("pageBreakBefore");
    }

    @Test
    void 그림만_든_문단은_자리를_지킨다() throws Exception {
        // 표지는 글이 도형 안에 있어 문단 글자가 비어 있다. 그 문단을 버리면
        // 표지 쪽이 통째로 사라진다(2026-08-25 지적: 첫 쪽 그림).
        byte[] source;
        try (var document = new org.apache.poi.xwpf.usermodel.XWPFDocument();
             var out = new java.io.ByteArrayOutputStream()) {
            var cover = document.createParagraph();
            var run = cover.createRun();
            run.addBreak(org.apache.poi.xwpf.usermodel.BreakType.PAGE);
            run.addPicture(new java.io.ByteArrayInputStream(그림()),
                    org.apache.poi.xwpf.usermodel.Document.PICTURE_TYPE_PNG, "cover.png",
                    org.apache.poi.util.Units.toEMU(100), org.apache.poi.util.Units.toEMU(50));
            document.createParagraph().createRun().setText("목차");
            document.write(out);
            source = out.toByteArray();
        }

        DocContent read = service.readDocx(new ByteArrayInputStream(source));

        assertThat(read.paragraphs()).hasSize(2);
        assertThat(read.pictures()).hasSize(1);
        assertThat(read.pictures().get(0).afterParagraphNo()).isZero();
    }

    /** 1x1 짜리 png. */
    private byte[] 그림() {
        return java.util.Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk"
                + "YPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==");
    }

    @Test
    void 떠_있는_그림은_떠_있는_채로_싣는다() throws Exception {
        // 흐름에 넣으면 표지 배경이 제 쪽을 차지해 쪽이 하나 는다
        // (2026-08-25 실측: 6쪽 문서가 7쪽).
        var picture = new DocxIoService.Drawing(0, 그림(), 820, 1140, true, true, -22, -132);

        byte[] made = service.toDocx(
                List.of(new DocParagraphDto(0, "", null, null),
                        new DocParagraphDto(1, "목차", null, null)),
                List.of(), List.of(picture));

        try (var document = new org.apache.poi.xwpf.usermodel.XWPFDocument(
                new ByteArrayInputStream(made))) {
            String xml = document.getDocument().getBody().xmlText();
            assertThat(xml).contains("anchor");
            assertThat(xml).doesNotContain("<wp:inline");
        }
    }

    @Test
    void 쪽_나눔_뒤에_오는_표는_새_쪽에서_연다() throws Exception {
        // 표지 다음 목차가 표로 시작하는 문서가 있다. 쪽 나눔을 표 뒤 문단이
        // 가져가면 목차 표만 표지 쪽에 남는다(2026-08-25 지적).
        byte[] source;
        try (var document = new org.apache.poi.xwpf.usermodel.XWPFDocument();
             var out = new java.io.ByteArrayOutputStream()) {
            var cover = document.createParagraph();
            cover.createRun().setText("표지");
            cover.getCTP().addNewPPr().addNewSectPr();
            var table = document.createTable(1, 1);
            table.getRow(0).getCell(0).setText("목차");
            document.createParagraph().createRun().setText("1. 머리말");
            document.write(out);
            source = out.toByteArray();
        }

        DocContent read = service.readDocx(new ByteArrayInputStream(source));

        assertThat(read.tables()).hasSize(1);
        assertThat(read.tables().get(0).opensNewPage()).isTrue();
        // 표가 쪽 나눔을 가져갔으니 뒤 문단은 새 쪽을 열지 않는다.
        String after = read.paragraphs().get(1).style();
        assertThat(after == null || !after.contains("pageBreakBefore")).isTrue();
    }

    @Test
    void 글상자_안_글도_읽는다() throws Exception {
        // 표지 제목이 도형(글상자) 안에 든 문서가 있다. 그것을 흘리면 표지가
        // 그림만 남고 글이 통째로 사라진다(2026-08-25 지적).
        String box = "<w:pict xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\""
                + " xmlns:v=\"urn:schemas-microsoft-com:vml\">"
                + "<v:shape><v:textbox><w:txbxContent>"
                + "<w:p><w:r><w:t>표지 제목</w:t></w:r></w:p>"
                + "</w:txbxContent></v:textbox></v:shape></w:pict>";
        byte[] source;
        try (var document = new org.apache.poi.xwpf.usermodel.XWPFDocument();
             var out = new java.io.ByteArrayOutputStream()) {
            var paragraph = document.createParagraph();
            var run = paragraph.createRun().getCTR();
            var parsed = org.apache.xmlbeans.XmlObject.Factory.parse(box);
            var into = run.newCursor();
            into.toEndToken();
            parsed.newCursor().copyXmlContents(into);
            into.dispose();
            document.write(out);
            source = out.toByteArray();
        }

        DocContent read = service.readDocx(new ByteArrayInputStream(source));

        assertThat(read.paragraphs()).extracting(DocParagraphDto::text)
                .contains("표지 제목");
    }

    @Test
    void 아래쪽만_그은_표는_그_변만_담는다() throws Exception {
        byte[] source;
        try (var document = new org.apache.poi.xwpf.usermodel.XWPFDocument();
             var out = new java.io.ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText("앞");
            var table = document.createTable(1, 1);
            table.getRow(0).getCell(0).setText("목차");
            table.setTopBorder(org.apache.poi.xwpf.usermodel.XWPFTable
                    .XWPFBorderType.NONE, 0, 0, "auto");
            table.setLeftBorder(org.apache.poi.xwpf.usermodel.XWPFTable
                    .XWPFBorderType.NONE, 0, 0, "auto");
            table.setRightBorder(org.apache.poi.xwpf.usermodel.XWPFTable
                    .XWPFBorderType.NONE, 0, 0, "auto");
            table.setBottomBorder(org.apache.poi.xwpf.usermodel.XWPFTable
                    .XWPFBorderType.SINGLE, 18, 0, "FC5E20");
            document.write(out);
            source = out.toByteArray();
        }

        DocContent read = service.readDocx(new ByteArrayInputStream(source));

        DocTableDto grid = read.tables().get(0);
        assertThat(grid.hasBorders()).isTrue();
        assertThat(grid.borders()).contains("\"bottom\"");
        assertThat(grid.borders()).doesNotContain("\"top\"");
    }

    @Test
    void 크기를_안_적은_문서는_10pt_다() throws Exception {
        // 워드 파일 규칙에서 크기를 안 적으면 20 half-point, 곧 10pt 다. 11pt 로
        // 보면 글이 원본보다 커진다(2026-08-26 지적).
        byte[] source;
        try (var document = new org.apache.poi.xwpf.usermodel.XWPFDocument();
             var out = new java.io.ByteArrayOutputStream()) {
            // 서식표가 있어야 문서 기본을 읽는다. 크기는 일부러 안 적는다.
            document.createStyles();
            document.createParagraph().createRun().setText("글");
            document.write(out);
            source = out.toByteArray();
        }

        DocContent read = service.readDocx(new ByteArrayInputStream(source));

        assertThat(read.defaults().fontSize()).isEqualTo(10);
    }

    @Test
    void 칸_안_번호도_글자로_담는다() throws Exception {
        // 제목 표 안 문단이 자동 번호를 쓴다. 그것을 흘리면 "1." 이 사라진다
        // (2026-08-26 지적: 데이터셋 파라미터 앞 번호).
        byte[] source;
        try (var document = new org.apache.poi.xwpf.usermodel.XWPFDocument();
             var out = new java.io.ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText("앞");
            번호_규칙(document);
            var table = document.createTable(1, 1);
            var cell = table.getRow(0).getCell(0);
            var inside = cell.getParagraphs().get(0);
            inside.createRun().setText("데이터셋 파라미터");
            var numbering = inside.getCTP().addNewPPr().addNewNumPr();
            numbering.addNewNumId().setVal(java.math.BigInteger.ONE);
            numbering.addNewIlvl().setVal(java.math.BigInteger.ZERO);
            document.write(out);
            source = out.toByteArray();
        }

        DocContent read = service.readDocx(new ByteArrayInputStream(source));

        assertThat(read.tables().get(0).cells().get(0).get(0))
                .isEqualTo("1. 데이터셋 파라미터");
    }

    /** 번호 매기기 규칙 한 벌. 규칙이 없으면 워드도 번호를 못 매긴다. */
    private void 번호_규칙(org.apache.poi.xwpf.usermodel.XWPFDocument document) {
        var made = org.openxmlformats.schemas.wordprocessingml.x2006.main
                .CTNumbering.Factory.newInstance();
        var rule = made.addNewAbstractNum();
        rule.setAbstractNumId(java.math.BigInteger.ZERO);
        var level = rule.addNewLvl();
        level.setIlvl(java.math.BigInteger.ZERO);
        level.addNewNumFmt().setVal(org.openxmlformats.schemas.wordprocessingml.x2006.main
                .STNumberFormat.DECIMAL);
        level.addNewLvlText().setVal("%1.");
        level.addNewStart().setVal(java.math.BigInteger.ONE);
        var num = made.addNewNum();
        num.setNumId(java.math.BigInteger.ONE);
        num.addNewAbstractNumId().setVal(java.math.BigInteger.ZERO);
        document.createNumbering().setNumbering(made);
    }

    @Test
    void 열_너비가_칸_수와_어긋나면_고르게_나눈다() throws Exception {
        // 한글 문서는 표 전체 너비 하나만 알려 준다. 그것을 눈금으로 그대로 쓰면
        // 아홉 칸짜리 표가 한 칸이 되어 통째로 무너진다(2026-08-26 지적).
        var grid = new DocTableDto(0,
                List.of(List.of("가", "나", "다"), List.of("라", "마", "바")),
                List.of(640.6));

        byte[] made = service.toDocx(
                List.of(new DocParagraphDto(0, "앞", null, null)), List.of(grid), List.of());

        try (var document = new org.apache.poi.xwpf.usermodel.XWPFDocument(
                new ByteArrayInputStream(made))) {
            var table = document.getTables().get(0);
            assertThat(table.getRow(0).getTableCells()).hasSize(3);
            assertThat(table.getCTTbl().getTblGrid().sizeOfGridColArray()).isEqualTo(3);
        }
    }

}
