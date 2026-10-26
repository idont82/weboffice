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
    void hwp_합친_칸으로만_아는_열의_너비를_나눠_갖는다() throws Exception {
        // 관공서 서식에는 단독으로 선 적이 없는 열이 흔하다. 그 열을 "아는 열들의
        // 평균" 으로 메우면 표가 쪽보다 넓어져 글이 잘린다(장기요양인정 신청서
        // 실측: 640px 짜리 표가 942px 이 됐다). 합친 칸의 너비를 나눠 가져야 한다.
        byte[] file = 시험파일.hwp(f -> {
            var table = 시험파일.표(f, "넓은 칸", "합친 칸");
            var cells = table.getRowList().get(0).getCellList();
            cells.get(0).getListHeader().setColIndex((short) 0);
            cells.get(0).getListHeader().setWidth(30000);
            cells.get(1).getListHeader().setColIndex((short) 1);
            cells.get(1).getListHeader().setColSpan((short) 2);
            cells.get(1).getListHeader().setWidth(7500);
        });

        DocContent content = service.readHwp(file);

        assertThat(content.tables().get(0).columnWidths())
                .containsExactly(400d, 50d, 50d);
    }

    @Test
    void hwp_칸_가로_맞춤을_가져온다() throws Exception {
        // 서식의 레이블은 거의 다 가운데다. 맞춤이 빠지면 모두 왼쪽으로 붙어
        // 칸마다 글이 제각각 어긋나 보인다.
        byte[] file = 시험파일.hwp(f -> {
            var table = 시험파일.표(f, "가운데", "왼쪽");
            int 가운데 = 시험파일.가운데_문단모양(f);
            table.getRowList().get(0).getCellList().get(0).getParagraphList()
                    .getParagraph(0).getHeader().setParaShapeId(가운데);
        });

        DocContent content = service.readHwp(file);

        assertThat(content.tables().get(0).cellAligns())
                .containsExactly(java.util.List.of(2, 1));
    }

    @Test
    void hwpx_칸_가로_맞춤을_가져온다() throws Exception {
        byte[] file = 시험파일.hwpx(f -> {
            var table = 시험파일.표(f, "가운데", "왼쪽");
            String 가운데 = 시험파일.가운데_문단모양(f);
            table.getTr(0).getTc(0).subList().getPara(0).paraPrIDRef(가운데);
        });

        DocContent content = service.readHwpx(file);

        assertThat(content.tables().get(0).cellAligns())
                .containsExactly(java.util.List.of(2, 1));
    }

    @Test
    void hwp_쪽_나눔을_가져온다() throws Exception {
        // 쪽 나눔을 흘리면 표지 다음 글이 표지 쪽으로 올라온다. 실거래가 가이드
        // 실측(2026-10-26): 일곱 쪽짜리 표지가 본문과 한 쪽에 뭉쳤다.
        byte[] file = 시험파일.hwp(f -> {
            시험파일.문단(f, "표지");
            시험파일.문단(f, "새 쪽").getHeader().getDivideSort().setDividePage(true);
        });

        List<DocParagraphDto> rows = service.fromHwp(file);

        // 서식이 아예 없는 문단은 style 이 null 이다. 없는 것과 "쪽 나눔이 없다" 는
        // 같은 뜻이라 빈 글로 바꿔 본다.
        assertThat(java.util.Objects.toString(rows.get(0).style(), ""))
                .doesNotContain("pageBreakBefore");
        assertThat(rows.get(1).style()).contains("\"pageBreakBefore\":1");
    }

    @Test
    void hwpx_쪽_나눔을_가져온다() throws Exception {
        byte[] file = 시험파일.hwpx(f -> {
            시험파일.문단(f, "표지");
            시험파일.문단(f, "새 쪽").pageBreak(Boolean.TRUE);
        });

        List<DocParagraphDto> rows = service.readHwpx(file).paragraphs();

        // 서식이 아예 없는 문단은 style 이 null 이다. 없는 것과 "쪽 나눔이 없다" 는
        // 같은 뜻이라 빈 글로 바꿔 본다.
        assertThat(java.util.Objects.toString(rows.get(0).style(), ""))
                .doesNotContain("pageBreakBefore");
        assertThat(rows.get(1).style()).contains("\"pageBreakBefore\":1");
    }

    @Test
    void hwp_칸마다_그은_변만_가져온다() throws Exception {
        // 서식은 네 변을 다 긋지 않는다. 가로줄만 긋고 세로선은 레이블 옆에만
        // 두는 식이다. 표 하나에 변 하나만 담으면 모든 칸이 격자가 되어
        // 장기요양인정 신청서가 딴 문서로 보인다(2026-10-26 비교).
        byte[] file = 시험파일.hwp(f -> {
            var table = 시험파일.표(f, "아래만", "선 없음");
            int 아래선 = 시험파일.아래선_칸모양(f, 0xff, 0x00, 0x00,
                    kr.dogfoot.hwplib.object.docinfo.borderfill.BorderThickness.MM0_5);
            table.getRowList().get(0).getCellList().get(0)
                    .getListHeader().setBorderFillId(아래선);
        });

        DocContent content = service.readHwp(file);

        assertThat(content.tables().get(0).cellBorders().get(0).get(0))
                .contains("\"bottom\"").contains("#ff0000")
                .doesNotContain("\"top\"").doesNotContain("\"left\"");
        assertThat(content.tables().get(0).cellBorders().get(0).get(1)).isNull();
    }

    @Test
    void hwp_칸_안_문단마다_맞춤을_가져온다() throws Exception {
        // 칸 글자는 줄바꿈으로 이어 붙인 한 덩이라 문단마다의 맞춤을 담을 데가
        // 없었다. 칸 하나에 맞춤 하나만 두면 여러 문단이 든 칸이 다 같아진다.
        byte[] file = 시험파일.hwp(f -> {
            var table = 시험파일.표(f, "가운데", "보통");
            table.getRowList().get(0).getCellList().get(0).getParagraphList()
                    .getParagraph(0).getHeader()
                    .setParaShapeId(시험파일.가운데_문단모양(f));
        });

        DocContent content = service.readHwp(file);

        assertThat(content.tables().get(0).cellStyles().get(0).get(0))
                .contains("\"horizontalAlign\":2");
    }

    @Test
    void hwp_칸_안_문단의_첫줄_들여쓰기는_싣지_않는다() throws Exception {
        // 파일에 적힌 값과 한글이 그리는 자리가 맞지 않는다 - 신청서 제목은
        // 18816(250.9px)으로 적혀 있는데 한글은 137px 만 들여 그린다. 적힌 값을
        // 그대로 쓰면 긴 줄이 칸을 넘어가고, 줄 높이가 못박혀 있어 넘친 글자가
        // 사라진다(2026-10-26 실측). 자리가 덜 맞는 편이 낫다.
        byte[] file = 시험파일.hwp(f -> {
            var table = 시험파일.표(f, "제목", "보통");
            int 들여쓴 = 시험파일.들여쓴_문단모양(f, 18816);
            table.getRowList().get(0).getCellList().get(0).getParagraphList()
                    .getParagraph(0).getHeader().setParaShapeId(들여쓴);
        });

        DocContent content = service.readHwp(file);

        assertThat(content.tables().get(0).cellStyles())
                .allSatisfy(row -> assertThat(row).allSatisfy(one ->
                        assertThat(java.util.Objects.toString(one, ""))
                                .doesNotContain("indentFirstLine")));
    }

    @Test
    void hwp_칸_바탕색을_가져온다() throws Exception {
        // 관공서 서식은 바탕색이 뜻을 담는다 - "어두운 란은 신청인이 적지
        // 않습니다". 색이 빠지면 어디를 비워 둬야 하는지 알 길이 없어진다.
        byte[] file = 시험파일.hwp(f -> {
            var table = 시험파일.표(f, "접수번호", "접수일시");
            int 회색 = 시험파일.바탕색_칸모양(f, 0xd9, 0xd9, 0xd9);
            table.getRowList().get(0).getCellList().get(0)
                    .getListHeader().setBorderFillId(회색);
        });

        DocContent content = service.readHwp(file);

        assertThat(content.tables().get(0).cellShades())
                .containsExactly(java.util.Arrays.asList("#d9d9d9", null));
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
    void hwpx_칸_바탕색을_가져온다() throws Exception {
        byte[] file = 시험파일.hwpx(f -> {
            var table = 시험파일.표(f, "접수번호", "접수일시");
            String 회색 = 시험파일.바탕색_칸모양(f, "#D9D9D9");
            table.getTr(0).getTc(0).borderFillIDRef(회색);
        });

        DocContent content = service.readHwpx(file);

        assertThat(content.tables().get(0).cellShades())
                .containsExactly(java.util.Arrays.asList("#d9d9d9", null));
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
