package weboffice.doc;

import java.util.List;

import org.junit.jupiter.api.Test;

import kr.dogfoot.hwplib.object.bodytext.paragraph.Paragraph;
import kr.dogfoot.hwpxlib.object.content.section_xml.paragraph.Para;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 한글 문서 읽기.
 *
 * <p>읽기만 한다. 내보내기는 docx 다 - 한글 형식으로 되돌려 주지 않는다.
 *
 * <p>같은 한글 문서라도 hwp(2진)와 hwpx(OWPML 압축)는 파일 형식이 아주 다르다.
 * 그래서 두 길을 따로 확인한다.
 */
class HwpIoServiceTest {

    private final HwpIoService service = new HwpIoService();
    private final HwpTestFiles 시험파일 = new HwpTestFiles();

    @Test
    void hwp_글을_읽는다() throws Exception {
        byte[] file = 시험파일.hwp(f -> {
            시험파일.문단(f, "첫째 문단");
            시험파일.문단(f, "둘째 문단");
        });

        List<DocParagraphDto> rows = service.fromHwp(file);

        assertThat(rows).extracting(DocParagraphDto::text)
                .containsExactly("첫째 문단", "둘째 문단");
        assertThat(rows).extracting(DocParagraphDto::paragraphNo)
                .containsExactly(0, 1);
    }

    @Test
    void hwp_굵게가_글자_서식으로_온다() throws Exception {
        byte[] file = 시험파일.hwp(f -> {
            int boldId = 시험파일.굵은_글자모양(f);
            Paragraph p = 시험파일.문단(f, "보통굵게");
            // hwp 의 자리는 문단 첫머리의 확장 제어문자가 8 을 차지해 우리 글자 자리와
            // 다르다. 그것을 옮기는 것이 이 시험의 뜻이다.
            p.getCharShape().getPositonShapeIdPairList().clear();
            p.getCharShape().addParaCharShape(시험파일.글자자리(p, 0), 0);
            p.getCharShape().addParaCharShape(시험파일.글자자리(p, 2), boldId);
            p.getHeader().setCharShapeCount(2);
        });

        List<DocParagraphDto> rows = service.fromHwp(file);

        assertThat(rows.get(0).text()).isEqualTo("보통굵게");
        assertThat(rows.get(0).runs())
                .as("뒤 두 글자만 굵어야 한다")
                .contains("\"st\":2").contains("\"ed\":4").contains("\"bl\":1");
    }

    @Test
    void hwp_가운데_맞춤이_문단_서식으로_온다() throws Exception {
        byte[] file = 시험파일.hwp(f -> {
            int centerId = 시험파일.가운데_문단모양(f);
            시험파일.문단(f, "가운데").getHeader().setParaShapeId(centerId);
        });

        assertThat(service.fromHwp(file).get(0).style()).contains("\"horizontalAlign\":2");
    }

    @Test
    void hwp_빈_문단도_자리를_지킨다() throws Exception {
        // 빈 줄을 버리면 문단 번호가 밀려 뒤쪽 글이 통째로 어긋난다.
        byte[] file = 시험파일.hwp(f -> {
            시험파일.문단(f, "위");
            시험파일.문단(f, "");
            시험파일.문단(f, "아래");
        });

        assertThat(service.fromHwp(file)).extracting(DocParagraphDto::text)
                .containsExactly("위", "", "아래");
    }

    @Test
    void hwpx_글을_읽는다() throws Exception {
        byte[] file = 시험파일.hwpx(f -> {
            시험파일.문단(f, "첫째 문단");
            시험파일.문단(f, "둘째 문단");
        });

        assertThat(service.fromHwpx(file)).extracting(DocParagraphDto::text)
                .containsExactly("첫째 문단", "둘째 문단");
    }

    @Test
    void hwpx_굵게가_글자_서식으로_온다() throws Exception {
        byte[] file = 시험파일.hwpx(f -> {
            String boldId = 시험파일.굵은_글자모양(f);
            Para para = 시험파일.문단(f, "보통");
            시험파일.글토막(para, "굵게").charPrIDRef(boldId);
        });

        List<DocParagraphDto> rows = service.fromHwpx(file);

        assertThat(rows.get(0).text()).isEqualTo("보통굵게");
        assertThat(rows.get(0).runs())
                .contains("\"st\":2").contains("\"ed\":4").contains("\"bl\":1");
    }

    @Test
    void hwpx_취소선이_아닌_글자를_취소선으로_보지_않는다() throws Exception {
        // 실물 문서의 글자모양에는 "취소선 없음" 이 값으로 들어 있다. 있고 없고만
        // 보면 문서 전체에 줄이 그어진다 - 실제로 겪었다.
        byte[] file = 시험파일.hwpx(f -> {
            String plainId = 시험파일.줄_없는_글자모양(f);
            시험파일.문단(f, "보통").getRun(0).charPrIDRef(plainId);
        });

        // 크기는 실제 서식이라 담는다. 없는 줄만 안 그으면 된다.
        assertThat(service.fromHwpx(file).get(0).runs())
                .doesNotContain("\"st\":{").doesNotContain("\"ul\":{");
    }

    @Test
    void hwpx_가운데_맞춤이_문단_서식으로_온다() throws Exception {
        byte[] file = 시험파일.hwpx(f -> {
            String centerId = 시험파일.가운데_문단모양(f);
            시험파일.문단(f, "가운데").paraPrIDRef(centerId);
        });

        assertThat(service.fromHwpx(file).get(0).style()).contains("\"horizontalAlign\":2");
    }

    @Test
    void hwp_표를_표로_가져온다() throws Exception {
        // 관공서 서식은 글이 거의 다 표 안에 있다. 표를 건너뛰면 문서가 통째로
        // 비어 버리고, 칸 글자를 문단으로 펼치면 세로로 길게 늘어선다.
        byte[] file = 시험파일.hwp(f -> {
            시험파일.문단(f, "머리말");
            시험파일.표(f, "왼쪽 칸", "오른쪽 칸");
        });

        DocContent content = service.readHwp(file);

        assertThat(content.paragraphs()).extracting(DocParagraphDto::text).contains("머리말");
        assertThat(content.tables()).hasSize(1);
        assertThat(content.tables().get(0).cells())
                .containsExactly(java.util.List.of("왼쪽 칸", "오른쪽 칸"));
    }

    @Test
    void hwpx_표를_표로_가져온다() throws Exception {
        byte[] file = 시험파일.hwpx(f -> {
            시험파일.문단(f, "머리말");
            시험파일.표(f, "왼쪽 칸", "오른쪽 칸");
        });

        DocContent content = service.readHwpx(file);

        assertThat(content.paragraphs()).extracting(DocParagraphDto::text).contains("머리말");
        assertThat(content.tables().get(0).cells())
                .containsExactly(java.util.List.of("왼쪽 칸", "오른쪽 칸"));
    }

    @Test
    void hwp_글자_크기와_색을_가져온다() throws Exception {
        // 크기와 색이 빠지면 제목이 본문과 같은 크기·검정으로 나와 원본과 딴판이다.
        byte[] file = 시험파일.hwp(f -> {
            int 제목모양 = 시험파일.큰_빨간_글자모양(f, 20);
            Paragraph p = 시험파일.문단(f, "제목");
            p.getCharShape().getPositonShapeIdPairList().clear();
            p.getCharShape().addParaCharShape(시험파일.글자자리(p, 0), 제목모양);
            p.getHeader().setCharShapeCount(1);
        });

        List<DocParagraphDto> rows = service.fromHwp(file);

        assertThat(rows.get(0).runs())
                .contains("\"fs\":20").contains("\"rgb\":\"#ff0000\"");
    }

    @Test
    void hwp_표의_칸_너비를_가져온다() throws Exception {
        byte[] file = 시험파일.hwp(f -> {
            var table = 시험파일.표(f, "좁게", "넓게");
            // hwp 는 길이를 1/7200 인치로 적는다. 7200 이 1 인치(96px)다.
            table.getRowList().get(0).getCellList().get(0).getListHeader().setWidth(7200);
            table.getRowList().get(0).getCellList().get(1).getListHeader().setWidth(21600);
        });

        DocContent content = service.readHwp(file);

        assertThat(content.tables().get(0).columnWidths())
                .containsExactly(96d, 288d);
    }

    @Test
    void hwpx_글자_크기와_색을_가져온다() throws Exception {
        byte[] file = 시험파일.hwpx(f -> {
            String 제목모양 = 시험파일.큰_빨간_글자모양(f, 20);
            Para para = 시험파일.문단(f, "제목");
            para.getRun(0).charPrIDRef(제목모양);
        });

        List<DocParagraphDto> rows = service.fromHwpx(file);

        assertThat(rows.get(0).runs())
                .contains("\"fs\":20").contains("\"rgb\":\"#ff0000\"");
    }

    @Test
    void hwpx_표의_칸_너비를_가져온다() throws Exception {
        byte[] file = 시험파일.hwpx(f -> {
            var table = 시험파일.표(f, "좁게", "넓게");
            table.getTr(0).getTc(0).createCellSz();
            table.getTr(0).getTc(0).cellSz().width(7200L);
            table.getTr(0).getTc(1).createCellSz();
            table.getTr(0).getTc(1).cellSz().width(21600L);
        });

        DocContent content = service.readHwpx(file);

        assertThat(content.tables().get(0).columnWidths())
                .containsExactly(96d, 288d);
    }

    @Test
    void hwp_합친_칸을_알려_준다() throws Exception {
        // 한글은 합친 칸을 하나로만 적고 가려진 자리는 아예 없다. 그대로 옮기면
        // 행마다 칸 수가 달라 표가 어긋난다.
        byte[] file = 시험파일.hwp(f -> {
            var table = 시험파일.표(f, "머리");
            var cell = table.getRowList().get(0).getCellList().get(0);
            cell.getListHeader().setColSpan(2);
            table.getTable().setColumnCount(2);
        });

        DocContent content = service.readHwp(file);

        assertThat(content.tables().get(0).cells().get(0)).containsExactly("머리", "");
        assertThat(content.tables().get(0).columnSpans().get(0)).containsExactly(2, 0);
    }

    @Test
    void hwpx_합친_칸을_알려_준다() throws Exception {
        byte[] file = 시험파일.hwpx(f -> {
            var table = 시험파일.표(f, "머리");
            var tc = table.getTr(0).getTc(0);
            tc.createCellSpan();
            tc.cellSpan().colSpan((short) 2);
            tc.cellSpan().rowSpan((short) 1);
            table.colCnt((short) 2);
        });

        DocContent content = service.readHwpx(file);

        assertThat(content.tables().get(0).cells().get(0)).containsExactly("머리", "");
        assertThat(content.tables().get(0).columnSpans().get(0)).containsExactly(2, 0);
    }

    @Test
    void hwp_문단_사이_간격을_가져온다() throws Exception {
        // 간격이 빠지면 문단이 다닥다닥 붙어 원본보다 짧아 보이고 쪽 수도 달라진다.
        byte[] file = 시험파일.hwp(f -> {
            int 사이뜬모양 = 시험파일.사이가_뜬_문단모양(f);
            Paragraph p = 시험파일.문단(f, "문단");
            p.getHeader().setParaShapeId(사이뜬모양);
        });

        List<DocParagraphDto> rows = service.fromHwp(file);

        assertThat(rows.get(0).style())
                .contains("\"spaceAbove\"").contains("\"spaceBelow\"");
    }

    @Test
    void hwpx_문단_사이_간격을_가져온다() throws Exception {
        byte[] file = 시험파일.hwpx(f -> {
            String 사이뜬모양 = 시험파일.사이가_뜬_문단모양(f);
            시험파일.문단(f, "문단").paraPrIDRef(사이뜬모양);
        });

        List<DocParagraphDto> rows = service.fromHwpx(file);

        assertThat(rows.get(0).style())
                .contains("\"spaceAbove\"").contains("\"spaceBelow\"");
    }

    @Test
    void hwp_표_안_글자_서식을_가져온다() throws Exception {
        // 표 머리가 굵은 서식이 흔하다. 서식이 빠지면 머리와 본문이 같아 보인다.
        byte[] file = 시험파일.hwp(f -> {
            int 굵게 = 시험파일.굵은_글자모양(f);
            var table = 시험파일.표(f, "머리");
            var 문단 = table.getRowList().get(0).getCellList().get(0)
                    .getParagraphList().getParagraph(0);
            문단.getCharShape().getPositonShapeIdPairList().clear();
            문단.getCharShape().addParaCharShape(0, 굵게);
            문단.getHeader().setCharShapeCount(1);
        });

        DocContent content = service.readHwp(file);

        assertThat(content.tables().get(0).cellRuns().get(0).get(0)).contains("\"bl\":1");
    }

    @Test
    void hwp_그림을_가져온다() throws Exception {
        // 관공서 서식에는 직인·안내 그림이 흔하다. 빠지면 서식이 반쪽이 된다.
        byte[] file = 시험파일.hwp(f -> 시험파일.그림(f, 작은_png(), 7200));

        DocContent content = service.readHwp(file);

        assertThat(content.pictures()).hasSize(1);
        assertThat(content.pictures().get(0).bytes()).isNotEmpty();
        // 한글은 길이를 1/7200 인치로 적는다. 7200 이 1 인치(96px)다.
        assertThat(content.pictures().get(0).width()).isEqualTo(96);
        assertThat(content.pictures().get(0).height()).isEqualTo(48);
    }

    /** 1x1 짜리 png. 그림인지 아닌지만 가리면 되므로 가장 작은 것을 쓴다. */
    private byte[] 작은_png() {
        return java.util.Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");
    }

    @Test
    void hwp_문단_들여쓰기를_가져온다() throws Exception {
        // 번호 매긴 목록과 인용은 들여쓰기로 층을 나타낸다. 빠지면 모두 왼쪽에
        // 붙어 층이 사라진다.
        byte[] file = 시험파일.hwp(f -> {
            int 들여쓴모양 = 시험파일.들여쓴_문단모양(f);
            시험파일.문단(f, "들여 쓴 문단").getHeader().setParaShapeId(들여쓴모양);
        });

        List<DocParagraphDto> rows = service.fromHwp(file);

        assertThat(rows.get(0).style())
                .contains("\"indentStart\"").contains("\"indentFirstLine\"");
    }

    @Test
    void 한글_문서가_아니면_알려_준다() {
        // 빈 문서를 만들어 주면 올린 사람은 자기 글이 들어간 줄 안다.
        assertThatThrownBy(() -> service.fromHwp("한글 문서가 아니다".getBytes()))
                .isInstanceOf(DocImportException.class);
        assertThatThrownBy(() -> service.fromHwpx("한글 문서가 아니다".getBytes()))
                .isInstanceOf(DocImportException.class);
    }
    @Test
    void hwp_종이_크기와_여백을_읽는다() throws Exception {
        // 종이를 안 담으면 내려받은 파일이 워드 기본 종이로 열려 표가 넘친다
        // (2026-08-26 지적: 한글 신청서를 내려받으면 많이 깨진다).
        byte[] file = 시험파일.hwp(f -> 시험파일.문단(f, "글"));

        DocContent read = service.readHwp(file);

        assertThat(read.page()).isNotNull();
        // 한글 기본은 A4(210x297mm)다. 96dpi 화면에서 794x1123px 다.
        assertThat(read.page().width()).isCloseTo(794, within(2.0));
        assertThat(read.page().height()).isCloseTo(1123, within(2.0));
        assertThat(read.page().marginLeft()).isGreaterThan(0);
    }

    @Test
    void hwp_표의_줄_높이도_읽는다() throws Exception {
        // 줄 높이를 흘리면 표가 글에 맞춰 늘어나 세 쪽짜리 서식이 여섯 쪽이 된다
        // (2026-08-26 지적).
        byte[] file = 시험파일.hwp(f -> {
            var table = 시험파일.표(f, "왼쪽", "오른쪽");
            for (var cell : table.getRowList().get(0).getCellList()) {
                // 한글 길이 단위(1/7200 인치). 2400 이면 32px 다.
                cell.getListHeader().setHeight(2400);
                cell.getListHeader().setWidth(4800);
            }
        });

        DocContent read = service.readHwp(file);

        DocTableDto grid = read.tables().get(0);
        assertThat(grid.rowHeights()).hasSize(1);
        assertThat(grid.rowHeights().get(0)).isCloseTo(32, within(1.0));
        assertThat(grid.columnWidths()).hasSize(2);
    }

    @Test
    void hwp_열_너비는_칸이_많은_줄에서_잰다() throws Exception {
        // 첫 줄이 제목 한 칸이면 너비가 하나만 잡혀 표가 통째로 뭉갠다
        // (2026-08-26 실측: 아홉 칸 표에 너비 하나).
        byte[] file = 시험파일.hwp(f -> {
            var table = 시험파일.표(f, "제목");
            table.getRowList().get(0).getCellList().get(0).getListHeader().setWidth(9600);
            시험파일.줄(table, "왼쪽", "오른쪽");
            for (var cell : table.getRowList().get(1).getCellList()) {
                cell.getListHeader().setWidth(4800);
            }
        });

        DocContent read = service.readHwp(file);

        assertThat(read.tables().get(0).columnWidths()).hasSize(2);
        // 합친 칸이 있는 표도 열 수만큼 너비를 낸다.
        assertThat(read.tables().get(0).columnWidths())
                .allSatisfy(one -> assertThat(one).isGreaterThan(0));
    }

    @Test
    void hwp_칸_여백도_읽는다() throws Exception {
        // 칸 여백을 우리 기본(좌우 10px)으로 그리면 표가 원본보다 높아진다
        // (2026-08-26 지적: 세 쪽짜리 서식이 다섯 쪽).
        byte[] file = 시험파일.hwp(f -> {
            var table = 시험파일.표(f, "칸");
            var header = table.getRowList().get(0).getCellList().get(0).getListHeader();
            header.setWidth(4800);
            // 141 은 한글 기본 여백(0.5mm 남짓)이다. 96dpi 화면에서는 2px 다.
            header.setLeftMargin(141);
            header.setRightMargin(141);
            header.setTopMargin(141);
            header.setBottomMargin(141);
        });

        DocContent read = service.readHwp(file);

        assertThat(read.tables().get(0).cellMargin()).isNotNull();
        assertThat(read.tables().get(0).cellMargin().left()).isCloseTo(2, within(0.5));
        assertThat(read.tables().get(0).cellMargin().top()).isCloseTo(2, within(0.5));
    }

}
