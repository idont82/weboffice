package weboffice.doc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import kr.dogfoot.hwpxlib.object.HWPXFile;
import kr.dogfoot.hwpxlib.reader.HWPXReader;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 한글 문서(hwpx) 쓰기.
 *
 * <p>내보낸 것을 우리 읽기로 되읽어 견준다. 실제 한글에서 열어 보는 것은 사람이
 * 따로 한다 - 자동으로 할 길이 없다.
 */
class HwpxWriteServiceTest {

    private final HwpxWriteService service = new HwpxWriteService();
    private final HwpIoService reader = new HwpIoService();

    @Test
    void 빈_문서도_한글이_읽을_수_있게_쓴다() {
        byte[] made = service.toHwpx(DocContent.of(List.of()));

        DocContent again = reader.readHwpx(made);
        assertThat(again.paragraphs()).hasSize(1);
        assertThat(again.paragraphs().get(0).text()).isEmpty();
    }

    @Test
    void px_를_HWPUNIT_으로_옮긴다() {
        assertThat(HwpxUnit.px(96)).isEqualTo(7200L);
        assertThat(HwpxUnit.pt(10)).isEqualTo(1000);
    }

    @Test
    void 문단_글자를_옮긴다() {
        byte[] made = service.toHwpx(DocContent.of(List.of(
                new DocParagraphDto(0, "첫째 문단", null, null),
                new DocParagraphDto(1, "둘째 문단", null, null))));

        assertThat(reader.readHwpx(made).paragraphs())
                .extracting(DocParagraphDto::text)
                .containsExactly("첫째 문단", "둘째 문단");
    }

    @Test
    void 글자_크기와_굵기를_옮긴다() {
        byte[] made = service.toHwpx(DocContent.of(List.of(
                new DocParagraphDto(0, "크고 작고", null,
                        "[{\"st\":0,\"ed\":2,\"ts\":{\"fs\":25,\"bl\":1}},"
                        + "{\"st\":3,\"ed\":5,\"ts\":{\"fs\":9}}]"))));

        String runs = reader.readHwpx(made).paragraphs().get(0).runs();
        assertThat(runs).contains("\"fs\":25").contains("\"bl\":1").contains("\"fs\":9");
    }

    @Test
    void 같은_서식은_번호를_하나만_쓴다() throws Exception {
        int before = 글자서식_수(service.toHwpx(DocContent.of(List.of())));
        List<DocParagraphDto> many = new java.util.ArrayList<>();
        for (int i = 0; i < 100; i++) {
            many.add(new DocParagraphDto(i, "같은 서식 " + i, null,
                    "[{\"st\":0,\"ed\":3,\"ts\":{\"fs\":11}}]"));
        }

        assertThat(글자서식_수(service.toHwpx(DocContent.of(many)))).isEqualTo(before + 1);
    }

    @Test
    void 글꼴_이름이_없으면_기본_글꼴로_떨어진다() throws Exception {
        byte[] made = service.toHwpx(DocContent.of(List.of(
                new DocParagraphDto(0, "글꼴 없음", null,
                        "[{\"st\":0,\"ed\":4,\"ts\":{\"fs\":11}}]"),
                new DocParagraphDto(1, "빈 글꼴", null,
                        "[{\"st\":0,\"ed\":3,\"ts\":{\"fs\":11,\"ff\":\"\"}}]"))));

        HWPXFile file = 읽는다(made);
        for (var faces : file.headerXMLFile().refList().fontfaces().fontfaces()) {
            for (var font : faces.fonts()) {
                assertThat(font.face()).as("빈 이름 글꼴이 생기면 안 된다").isNotBlank();
            }
        }
    }

    @Test
    void 문서_기본_글꼴과_크기를_옮긴다() {
        DocContent content = new DocContent(
                List.of(new DocParagraphDto(0, "서식 없는 글", null, null)),
                List.of(), List.of(), null, null, null, false, null, null,
                new DocContent.DocDefaults("Arial", "함초롬바탕", 12, null, null, 0));

        String runs = reader.readHwpx(service.toHwpx(content)).paragraphs().get(0).runs();
        assertThat(runs).contains("\"fs\":12");
    }

    @Test
    void 가운데_맞춤을_옮긴다() {
        String style = 되읽은_문단서식("{\"paragraphStyle\":{\"horizontalAlign\":2}}");

        assertThat(style).contains("\"horizontalAlign\":2");
    }

    @Test
    void 들여쓰기와_왼쪽_여백을_옮긴다() {
        String style = 되읽은_문단서식(
                "{\"paragraphStyle\":{\"indentStart\":{\"v\":24},"
                + "\"indentFirstLine\":{\"v\":12}}}");

        assertThat(style).contains("\"indentStart\"").contains("\"indentFirstLine\"");
    }

    @Test
    void 문단_앞뒤_간격을_옮긴다() {
        String style = 되읽은_문단서식(
                "{\"paragraphStyle\":{\"spaceAbove\":{\"v\":6},\"spaceBelow\":{\"v\":3}}}");

        assertThat(style).contains("\"spaceAbove\"").contains("\"spaceBelow\"");
    }

    @Test
    void 줄_간격을_옮긴다() throws Exception {
        // 읽기 쪽이 줄 간격을 안 보므로 파일을 바로 들여다본다.
        byte[] made = service.toHwpx(DocContent.of(List.of(
                new DocParagraphDto(0, "줄 간격", 
                        "{\"paragraphStyle\":{\"lineSpacing\":1.5,\"spacingRule\":0}}", null))));

        assertThat(마지막_문단서식(made).lineSpacing().value()).isEqualTo(150);
    }

    @Test
    void 다음_쪽에_붙이기를_옮긴다() throws Exception {
        byte[] made = service.toHwpx(DocContent.of(List.of(
                new DocParagraphDto(0, "제목", 
                        "{\"paragraphStyle\":{\"keepNext\":1}}", null))));

        assertThat(마지막_문단서식(made).breakSetting().keepWithNext()).isTrue();
    }

    @Test
    void 같은_문단_서식은_번호를_하나만_쓴다() throws Exception {
        int before = 문단서식_수(service.toHwpx(DocContent.of(List.of())));
        List<DocParagraphDto> many = new java.util.ArrayList<>();
        for (int i = 0; i < 50; i++) {
            many.add(new DocParagraphDto(i, "가운데 " + i,
                    "{\"paragraphStyle\":{\"horizontalAlign\":2}}", null));
        }

        assertThat(문단서식_수(service.toHwpx(DocContent.of(many)))).isEqualTo(before + 1);
    }

    private int 문단서식_수(byte[] made) throws Exception {
        return 읽는다(made).headerXMLFile().refList().paraProperties().count();
    }

    /** 방금 더한 문단모양. 빈 파일이 여럿 깔아 두므로 맨 끝이 내 것이다. */
    private kr.dogfoot.hwpxlib.object.content.header_xml.references.ParaPr
            마지막_문단서식(byte[] made) throws Exception {
        var list = 읽는다(made).headerXMLFile().refList().paraProperties();
        return list.get(list.count() - 1);
    }

    @Test
    void 표_글자와_칸_수를_옮긴다() {
        DocContent made = 되읽는다(표(List.of(
                List.of("구분", "내용"), List.of("보안", "접근 통제"))));

        assertThat(made.tables()).hasSize(1);
        assertThat(made.tables().get(0).cells())
                .containsExactly(List.of("구분", "내용"), List.of("보안", "접근 통제"));
    }

    @Test
    void 열_너비와_행_높이를_옮긴다() {
        DocTableDto one = new DocTableDto(-1, List.of(List.of("가", "나")),
                List.of(120d, 240d), List.of(), List.of(List.of(1, 1)), List.of(List.of(1, 1)),
                true, List.of(), 0, List.of(48d), List.of(), List.of(), false, null, true);

        DocTableDto back = 되읽는다(one).tables().get(0);
        assertThat(back.columnWidths()).containsExactly(120d, 240d);
        assertThat(back.rowHeights()).containsExactly(48d);
    }

    @Test
    void 가로_세로_병합을_옮긴다() {
        DocTableDto one = new DocTableDto(-1,
                List.of(List.of("넓은 칸", ""), List.of("아래", "오른쪽")),
                List.of(100d, 100d), List.of(),
                List.of(List.of(2, 1), List.of(1, 1)), List.of(List.of(1, 1), List.of(1, 1)),
                true, List.of(), 0, List.of(20d, 20d), List.of(), List.of(), false, null, true);

        DocTableDto back = 되읽는다(one).tables().get(0);
        assertThat(back.columnSpans().get(0).get(0)).isEqualTo(2);
    }

    @Test
    void 칸마다_다른_테두리를_옮긴다() {
        String onlyTopBottom = """
                {"top":{"color":"#000000","size":1},"bottom":{"color":"#000000","size":1}}""";
        DocTableDto one = new DocTableDto(-1, List.of(List.of("제목")),
                List.of(300d), List.of(), List.of(List.of(1)), List.of(List.of(1)),
                true, List.of(), 0, List.of(60d), List.of(), List.of(), false, null, true,
                null, List.of(java.util.Collections.singletonList(onlyTopBottom)));

        String back = 되읽는다(one).tables().get(0).cellBorders().get(0).get(0);
        assertThat(back).contains("top").contains("bottom");
        assertThat(back).doesNotContain("left").doesNotContain("right");
    }

    @Test
    void 칸_바탕색을_옮긴다() {
        DocTableDto one = new DocTableDto(-1, List.of(List.of("어두운 칸")),
                List.of(200d), List.of(List.of("#d9d9d9")), List.of(List.of(1)),
                List.of(List.of(1)), true, List.of(), 0, List.of(30d),
                List.of(), List.of(), false, null, true);

        assertThat(되읽는다(one).tables().get(0).cellShades().get(0).get(0))
                .isEqualToIgnoringCase("#d9d9d9");
    }

    @Test
    void 칸_안_문단마다의_맞춤을_옮긴다() {
        String centered = """
                [{"paragraphStyle":{"horizontalAlign":2}}]""";
        DocTableDto one = new DocTableDto(-1, List.of(List.of("가운데")),
                List.of(200d), List.of(), List.of(List.of(1)), List.of(List.of(1)),
                true, List.of(), 0, List.of(30d), List.of(), List.of(), false, null, true,
                null, List.of(), List.of(List.of(centered)));

        assertThat(되읽는다(one).tables().get(0).cellAligns().get(0).get(0)).isEqualTo(2);
    }

    @Test
    void 글자가_없는_칸도_문단_하나를_갖는다() throws Exception {
        byte[] made = service.toHwpx(new DocContent(List.of(), List.of(
                표(List.of(List.of("", "")))), List.of()));

        var table = 표하나(읽는다(made));
        for (var tr : table.trs()) {
            for (var tc : tr.tcs()) {
                assertThat(tc.subList().countOfPara())
                        .as("문단 없는 칸은 한글이 거절한다").isEqualTo(1);
            }
        }
    }

    @Test
    void 흐름_안_그림을_옮긴다() throws Exception {
        byte[] made = service.toHwpx(new DocContent(
                List.of(new DocParagraphDto(0, "그림 앞", null, null)),
                List.of(), List.of(new DocContent.DocPicture(0, png(), 96, 48))));

        var file = 읽는다(made);
        assertThat(그림_알맹이(file)).hasSize(1);
        assertThat(그림(file).img().binaryItemIDRef()).isNotBlank();
        assertThat(그림(file).sz().width()).isEqualTo(HwpxUnit.px(96));
    }

    @Test
    void 떠_있는_그림의_자리를_옮긴다() throws Exception {
        byte[] made = service.toHwpx(new DocContent(
                List.of(new DocParagraphDto(0, "표지", null, null)), List.of(),
                List.of(new DocContent.DocPicture(0, png(), 100, 50, true, true, 30, 60))));

        var 하나 = 그림(읽는다(made));
        assertThat(하나.pos().treatAsChar()).isFalse();
        assertThat(하나.pos().horzOffset()).isEqualTo(HwpxUnit.px(30));
        assertThat(하나.pos().vertOffset()).isEqualTo(HwpxUnit.px(60));
    }

    @Test
    void 알_수_없는_그림_형식은_건너뛴다() throws Exception {
        byte[] made = service.toHwpx(new DocContent(
                List.of(new DocParagraphDto(0, "글", null, null)), List.of(),
                List.of(new DocContent.DocPicture(0, "그림아님".getBytes(), 10, 10),
                        new DocContent.DocPicture(0, png(), 96, 48))));

        assertThat(그림_알맹이(읽는다(made))).as("읽을 수 있는 것만 싣는다").hasSize(1);
    }

    @Test
    void 종이_크기와_여백을_옮긴다() throws Exception {
        byte[] made = service.toHwpx(new DocContent(
                List.of(new DocParagraphDto(0, "글", null, null)), List.of(), List.of(),
                new DocContent.DocPage(794, 1123, 96, 96, 72, 72, 48, 48)));

        var pagePr = 구역(읽는다(made)).pagePr();
        assertThat(pagePr.width()).isEqualTo((int) HwpxUnit.px(794));
        assertThat(pagePr.height()).isEqualTo((int) HwpxUnit.px(1123));
        assertThat(pagePr.margin().left()).isEqualTo((int) HwpxUnit.px(96));
        assertThat(pagePr.margin().header()).isEqualTo((int) HwpxUnit.px(48));
    }

    @Test
    void 머리글과_바닥글을_옮긴다() throws Exception {
        byte[] made = service.toHwpx(new DocContent(
                List.of(new DocParagraphDto(0, "본문", null, null)), List.of(), List.of(),
                null, 가장자리("머리글 글"), 가장자리("바닥글 글")));

        assertThat(가장자리_글(읽는다(made), true)).isEqualTo("머리글 글");
        assertThat(가장자리_글(읽는다(made), false)).isEqualTo("바닥글 글");
    }

    @Test
    void 머리글_안의_표도_옮긴다() throws Exception {
        var edge = new DocContent.DocEdge(List.of(new DocParagraphDto(0, "", null, null)),
                List.of(표(List.of(List.of("로고", "회사")))));
        byte[] made = service.toHwpx(new DocContent(
                List.of(new DocParagraphDto(0, "본문", null, null)), List.of(), List.of(),
                null, edge, null));

        assertThat(머리글(읽는다(made)).subList().countOfPara())
                .as("표가 제 문단을 차지한다").isGreaterThan(1);
    }

    private DocContent.DocEdge 가장자리(String text) {
        return new DocContent.DocEdge(
                List.of(new DocParagraphDto(0, text, null, null)), List.of());
    }

    private kr.dogfoot.hwpxlib.object.content.section_xml.paragraph.secpr.SecPr
            구역(HWPXFile file) {
        for (var para : file.sectionXMLFileList().get(0).paras()) {
            for (var run : para.runs()) {
                if (run.secPr() != null) {
                    return run.secPr();
                }
            }
        }
        throw new IllegalStateException("구역 설정이 없다");
    }

    private List<kr.dogfoot.hwpxlib.object.content.section_xml.paragraph.ctrl.Header>
            머리글들(HWPXFile file) {
        List<kr.dogfoot.hwpxlib.object.content.section_xml.paragraph.ctrl.Header> found =
                new java.util.ArrayList<>();
        for (var para : file.sectionXMLFileList().get(0).paras()) {
            for (var run : para.runs()) {
                for (int i = 0; i < run.countOfRunItem(); i++) {
                    if (!(run.getRunItem(i) instanceof kr.dogfoot.hwpxlib.object.content
                            .section_xml.paragraph.Ctrl ctrl)) {
                        continue;
                    }
                    for (int k = 0; k < ctrl.countOfCtrlItems(); k++) {
                        if (ctrl.getCtrlItem(k) instanceof kr.dogfoot.hwpxlib.object.content
                                .section_xml.paragraph.ctrl.Header header) {
                            found.add(header);
                        }
                    }
                }
            }
        }
        return found;
    }

    private kr.dogfoot.hwpxlib.object.content.section_xml.paragraph.ctrl.Header
            머리글(HWPXFile file) {
        List<kr.dogfoot.hwpxlib.object.content.section_xml.paragraph.ctrl.Header> found =
                머리글들(file);
        if (found.isEmpty()) {
            throw new IllegalStateException("머리글이 없다");
        }
        return found.get(0);
    }

    /** 머리글이나 바닥글의 첫 문단 글. */
    private String 가장자리_글(HWPXFile file, boolean head) throws Exception {
        for (var para : file.sectionXMLFileList().get(0).paras()) {
            for (var run : para.runs()) {
                for (int i = 0; i < run.countOfRunItem(); i++) {
                    if (!(run.getRunItem(i) instanceof kr.dogfoot.hwpxlib.object.content
                            .section_xml.paragraph.Ctrl ctrl)) {
                        continue;
                    }
                    for (int k = 0; k < ctrl.countOfCtrlItems(); k++) {
                        var item = ctrl.getCtrlItem(k);
                        boolean isHead = item instanceof kr.dogfoot.hwpxlib.object.content
                                .section_xml.paragraph.ctrl.Header;
                        boolean isFoot = item instanceof kr.dogfoot.hwpxlib.object.content
                                .section_xml.paragraph.ctrl.Footer;
                        if (head ? !isHead : !isFoot) {
                            continue;
                        }
                        return 첫글((kr.dogfoot.hwpxlib.object.content.section_xml
                                .paragraph.ctrl.inner.HeaderFooterCore<?>) item);
                    }
                }
            }
        }
        throw new IllegalStateException("가장자리가 없다");
    }

    private String 첫글(kr.dogfoot.hwpxlib.object.content.section_xml.paragraph.ctrl.inner
            .HeaderFooterCore<?> core) {
        var t = (kr.dogfoot.hwpxlib.object.content.section_xml.paragraph.T)
                core.subList().getPara(0).getRun(0).getRunItem(0);
        return t.onlyText();
    }

    /** 1x1 짜리 투명 png. */
    private byte[] png() {
        return java.util.Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=");
    }

    private List<kr.dogfoot.hwpxlib.object.content.context_hpf.ManifestItem>
            그림_알맹이(HWPXFile file) {
        List<kr.dogfoot.hwpxlib.object.content.context_hpf.ManifestItem> items =
                new java.util.ArrayList<>();
        for (var item : file.contentHPFFile().manifest().items()) {
            if (item.hasAttachedFile()) {
                items.add(item);
            }
        }
        return items;
    }

    private kr.dogfoot.hwpxlib.object.content.section_xml.paragraph.object.Picture
            그림(HWPXFile file) {
        var section = file.sectionXMLFileList().get(0);
        for (var para : section.paras()) {
            for (var run : para.runs()) {
                for (int i = 0; i < run.countOfRunItem(); i++) {
                    if (run.getRunItem(i) instanceof kr.dogfoot.hwpxlib.object.content
                            .section_xml.paragraph.object.Picture picture) {
                        return picture;
                    }
                }
            }
        }
        throw new IllegalStateException("그림이 없다");
    }

    private kr.dogfoot.hwpxlib.object.content.section_xml.paragraph.object.Table
            표하나(HWPXFile file) {
        for (var para : file.sectionXMLFileList().get(0).paras()) {
            for (var run : para.runs()) {
                for (int i = 0; i < run.countOfRunItem(); i++) {
                    if (run.getRunItem(i) instanceof kr.dogfoot.hwpxlib.object.content
                            .section_xml.paragraph.object.Table table) {
                        return table;
                    }
                }
            }
        }
        throw new IllegalStateException("표가 없다");
    }

    @Test
    void 줄_높이를_모르는_표도_내보낸다() {
        // 줄 높이를 못박지 않은 docx 표는 rowHeights 가 [null, null] 이다.
        List<Double> heights = new java.util.ArrayList<>();
        heights.add(null);
        DocTableDto one = new DocTableDto(-1, List.of(List.of("가", "나")),
                List.of(100d, 100d), List.of(), List.of(List.of(1, 1)), List.of(List.of(1, 1)),
                true, List.of(), 0, heights, List.of(), List.of(), false, null, false);

        assertThat(되읽는다(one).tables()).hasSize(1);
    }

    @Test
    void 병합에_가려진_칸은_적지_않는다() throws Exception {
        // 가려진 칸은 0 이다(DocTableDto 규칙). 한글은 그 칸을 아예 안 적는다.
        DocTableDto one = new DocTableDto(-1,
                List.of(java.util.Arrays.asList("넓은 칸", ""), List.of("아래", "오른쪽")),
                List.of(100d, 100d), List.of(),
                List.of(List.of(2, 0), List.of(1, 1)), List.of(List.of(1, 1), List.of(1, 1)),
                true, List.of(), 0, List.of(20d, 20d), List.of(), List.of(), false, null, true);

        var table = 표하나(읽는다(service.toHwpx(
                new DocContent(List.of(), List.of(one), List.of()))));
        assertThat(table.getTr(0).countOfTc()).as("가려진 칸은 빠진다").isEqualTo(1);
        assertThat(table.getTr(0).getTc(0).cellSpan().colSpan()).isEqualTo((short) 2);
    }

    @Test
    void 빈_문단에도_서식_번호를_붙인다() throws Exception {
        var para = 읽는다(service.toHwpx(DocContent.of(List.of()))).sectionXMLFileList()
                .get(0).getPara(0);

        assertThat(para.paraPrIDRef()).isNotNull();
        assertThat(para.styleIDRef()).isNotNull();
    }

    @Test
    void 문단_앞뒤_간격은_px_로_적는다() throws Exception {
        byte[] made = service.toHwpx(DocContent.of(List.of(new DocParagraphDto(0, "글",
                """
                {"paragraphStyle":{"spaceAbove":{"v":6}}}""", null))));

        assertThat(마지막_문단서식(made).margin().prev().value())
                .isEqualTo((int) HwpxUnit.px(6));
    }

    @Test
    void 쪽_나눔을_옮긴다() throws Exception {
        byte[] made = service.toHwpx(DocContent.of(List.of(new DocParagraphDto(0, "새 쪽",
                """
                {"paragraphStyle":{"pageBreakBefore":1}}""", null))));

        var section = 읽는다(made).sectionXMLFileList().get(0);
        assertThat(section.getPara(section.countOfPara() - 1).pageBreak()).isTrue();
    }

    @Test
    void 마지막_문단_뒤의_표도_싣는다() {
        DocTableDto one = new DocTableDto(5, List.of(List.of("끝 표")),
                List.of(100d), List.of(), List.of(List.of(1)), List.of(List.of(1)),
                true, List.of(), 0, List.of(20d), List.of(), List.of(), false, null, true);

        DocContent back = reader.readHwpx(service.toHwpx(new DocContent(
                List.of(new DocParagraphDto(0, "하나뿐인 문단", null, null)),
                List.of(one), List.of())));
        assertThat(back.tables()).as("문서 끝의 표가 사라지면 안 된다").hasSize(1);
    }

    @Test
    void 머리글_안의_그림도_옮긴다() throws Exception {
        var edge = new DocContent.DocEdge(
                List.of(new DocParagraphDto(0, "로고 옆 글", null, null)), List.of(),
                List.of(new DocContent.DocPicture(0, png(), 40, 20)));
        byte[] made = service.toHwpx(new DocContent(
                List.of(new DocParagraphDto(0, "본문", null, null)), List.of(), List.of(),
                null, edge, null));

        assertThat(그림_알맹이(읽는다(made))).hasSize(1);
    }

    @Test
    void 열_너비를_모르면_칸_크기를_적지_않는다() throws Exception {
        // 반만 맞는 너비를 적으면 앞 칸만 넓어지고 뒤 칸이 찌그러진다.
        DocTableDto one = new DocTableDto(-1, List.of(List.of("가", "나")),
                List.of(), List.of(), List.of(List.of(1, 1)), List.of(List.of(1, 1)),
                true, List.of(), 0, List.of(20d), List.of(), List.of(), false, null, true);

        var table = 표하나(읽는다(service.toHwpx(
                new DocContent(List.of(), List.of(one), List.of()))));
        assertThat(table.getTr(0).getTc(0).cellSz().width())
                .as("모르는 너비는 비워 둔다").isNull();
        assertThat(table.getTr(0).getTc(0).cellSz().height())
                .as("아는 높이는 적는다").isEqualTo(HwpxUnit.px(20));
    }

    @Test
    void 내어쓰기를_옮긴다() throws Exception {
        byte[] made = service.toHwpx(DocContent.of(List.of(new DocParagraphDto(0, "목록",
                """
                {"paragraphStyle":{"indentStart":{"v":24},"hanging":{"v":24}}}""", null))));

        assertThat(마지막_문단서식(made).margin().intent().value())
                .as("내어쓰기는 음수 들여쓰기다").isEqualTo((int) -HwpxUnit.px(24));
    }

    @Test
    void 표를_가운데_놓는다() throws Exception {
        DocTableDto one = new DocTableDto(-1, List.of(List.of("가운데 표")),
                List.of(100d), List.of(), List.of(List.of(1)), List.of(List.of(1)),
                true, List.of(), 2, List.of(20d), List.of(), List.of(), false, null, true);

        var table = 표하나(읽는다(service.toHwpx(
                new DocContent(List.of(), List.of(one), List.of()))));
        assertThat(table.pos().horzAlign()).isEqualTo(
                kr.dogfoot.hwpxlib.object.content.section_xml.enumtype.HorzAlign.CENTER);
    }

    @Test
    void 밑줄을_끈_글자는_긋지_않는다() throws Exception {
        byte[] made = service.toHwpx(DocContent.of(List.of(new DocParagraphDto(0, "민글",
                null,
                """
                [{"st":0,"ed":2,"ts":{"fs":11,"ul":{"s":0}}}]"""))));

        assertThat(reader.readHwpx(made).paragraphs().get(0).runs())
                .doesNotContain("\"ul\"");
    }

    /** 칸 글자만 주면 나머지는 그럴듯한 값으로 채운 표. */
    private DocTableDto 표(List<List<String>> cells) {
        List<Double> widths = new java.util.ArrayList<>();
        List<List<Integer>> spans = new java.util.ArrayList<>();
        List<Double> heights = new java.util.ArrayList<>();
        for (int c = 0; c < cells.get(0).size(); c++) {
            widths.add(100d);
        }
        for (List<String> row : cells) {
            List<Integer> one = new java.util.ArrayList<>();
            row.forEach(cell -> one.add(1));
            spans.add(one);
            heights.add(24d);
        }
        return new DocTableDto(-1, cells, widths, List.of(), spans, spans,
                true, List.of(), 0, heights, List.of(), List.of(), false, null, true);
    }

    private DocContent 되읽는다(DocTableDto table) {
        return reader.readHwpx(service.toHwpx(
                new DocContent(List.of(), List.of(table), List.of())));
    }

    private String 되읽은_문단서식(String style) {
        byte[] made = service.toHwpx(DocContent.of(List.of(
                new DocParagraphDto(0, "한 문단", style, null))));
        return reader.readHwpx(made).paragraphs().get(0).style();
    }

    private int 글자서식_수(byte[] made) throws Exception {
        return 읽는다(made).headerXMLFile().refList().charProperties().count();
    }

    private HWPXFile 읽는다(byte[] made) throws Exception {
        Path temp = Files.createTempFile("kweboffice-test-", ".hwpx");
        try {
            Files.write(temp, made);
            return HWPXReader.fromFile(temp.toFile());
        } finally {
            Files.deleteIfExists(temp);
        }
    }
}
