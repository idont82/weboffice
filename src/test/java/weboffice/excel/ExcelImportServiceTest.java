package weboffice.excel;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExcelImportServiceTest {

    private ExcelImportService service;

    @BeforeEach
    void setUp() {
        service = new ExcelImportService();
    }

    @Test
    void 시트가_여럿이면_전부_읽는다() throws Exception {
        byte[] xlsx = TestWorkbooks.threeSheets();

        WorkbookDataDto workbook = service.importWorkbook(
                new ByteArrayInputStream(xlsx), "3시트.xlsx");

        assertThat(workbook.sheets()).extracting(SheetDto::sheetName)
                .containsExactly("1분기", "2분기", "3분기");
        assertThat(workbook.sheets().get(1).cells())
                .as("첫 시트만 읽으면 export 때 나머지가 소실된다")
                .isNotEmpty();
    }

    @Test
    void 시트명과_셀_개수를_읽는다() throws Exception {
        SheetDto sheet = importFixture();

        assertThat(sheet.sheetName()).isEqualTo("시트1");
        // A1 문자열, B1 숫자, C1 날짜, D1 불린, E1 숫자수식, F1 문자열수식,
        // G1 고정밀, H1 오류, A2 문자열 = 9개.
        // B2 는 빈 셀이므로 레코드를 만들지 않는다(스펙 4절 설계 판단 1항).
        assertThat(sheet.cells()).hasSize(9);
    }

    @Test
    void 문자열_셀을_읽는다() throws Exception {
        CellDto cell = cellAt(importFixture(), 0, 0);

        assertThat(cell.valueType()).isEqualTo(CellDto.ValueType.STRING);
        assertThat(cell.textValue()).isEqualTo("품목명");
    }

    @Test
    void 서식이_없는_셀은_styleValue가_null이다() throws Exception {
        // POI 는 서식 없는 셀에도 "General" 을 반환한다.
        // 그것을 그대로 저장하면 "서식 없음"과 "명시적 General"을 구분할 수 없고,
        // 문자열·불린 레코드까지 무의미한 서식 문자열로 오염된다.
        assertThat(cellAt(importFixture(), 0, 0).styleValue()).isNull();
    }

    @Test
    void 숫자_셀은_값과_숫자서식을_함께_읽는다() throws Exception {
        CellDto cell = cellAt(importFixture(), 0, 1);

        assertThat(cell.valueType()).isEqualTo(CellDto.ValueType.NUMBER);
        assertThat(cell.numberValue()).isEqualByComparingTo(new BigDecimal("1234.56"));
        assertThat(cell.styleValue()).isEqualTo("{\"n\":{\"pattern\":\"#,##0.00\"}}");
    }

    @Test
    void 날짜_셀은_DATETIME_으로_구분한다() throws Exception {
        CellDto cell = cellAt(importFixture(), 0, 2);

        assertThat(cell.valueType()).isEqualTo(CellDto.ValueType.DATETIME);
        assertThat(cell.datetimeValue()).isNotNull();
        assertThat(cell.datetimeValue().toLocalDate().toString()).isEqualTo("2026-07-30");
    }

    @Test
    void 불린_셀은_Y_N_으로_저장한다() throws Exception {
        CellDto cell = cellAt(importFixture(), 0, 3);

        assertThat(cell.valueType()).isEqualTo(CellDto.ValueType.BOOLEAN);
        assertThat(cell.booleanYn()).isEqualTo("Y");
    }

    @Test
    void 숫자_수식_셀은_수식과_계산값을_함께_보존한다() throws Exception {
        CellDto cell = cellAt(importFixture(), 0, 4);

        assertThat(cell.formulaValue()).isEqualTo("B1*2");
        assertThat(cell.valueType()).isEqualTo(CellDto.ValueType.NUMBER);
        // 스펙 4절 설계 판단 3항: 수식은 문자열과 계산값을 함께 저장한다.
        assertThat(cell.numberValue()).isEqualByComparingTo(new BigDecimal("2469.12"));
    }

    @Test
    void 문자열_수식_셀은_STRING_으로_분류한다() throws Exception {
        CellDto cell = cellAt(importFixture(), 0, 5);

        assertThat(cell.formulaValue()).isEqualTo("A1&\"/EA\"");
        // 수식이라는 이유로 NUMBER 로 고정하면 안 된다. 결과 종류로 분류한다.
        assertThat(cell.valueType()).isEqualTo(CellDto.ValueType.STRING);
        assertThat(cell.textValue()).isEqualTo("품목명/EA");
    }

    @Test
    void 소수점_10자리_숫자가_보존된다() throws Exception {
        CellDto cell = cellAt(importFixture(), 0, 6);

        // xlsx 숫자 셀은 double 이므로 유효숫자 15자리까지가 형식 자체의 한계다.
        // 우리 변환이 그 한계 안에서 손실을 더하지 않는지 확인한다.
        assertThat(cell.numberValue()).isEqualByComparingTo(new BigDecimal("0.1234567890"));
        assertThat(cell.styleValue()).isEqualTo("{\"n\":{\"pattern\":\"0.0000000000\"}}");
    }

    @Test
    void 오류_셀을_ERROR_로_읽는다() throws Exception {
        CellDto cell = cellAt(importFixture(), 0, 7);

        assertThat(cell.valueType()).isEqualTo(CellDto.ValueType.ERROR);
        // isNotBlank() 로는 부족하다 — POI 의 원시 오류 코드("23")도 만족하므로
        // 사람이 읽는 형태로 변환됐는지를 검증하지 못한다.
        assertThat(cell.textValue()).isEqualTo("#DIV/0!");
    }

    @Test
    void 병합범위를_읽는다() throws Exception {
        assertThat(importFixture().mergeRanges())
                .containsExactly(new MergeRangeDto(1, 0, 1, 1));
    }

    @Test
    void 그림이_든_xlsx_에서_그림을_읽는다() throws Exception {
        SheetDto sheet = service.importFirstSheet(
                new ByteArrayInputStream(TestWorkbooks.withPicture()));

        assertThat(sheet.pictures()).hasSize(1);
        PictureDto picture = sheet.pictures().get(0);
        assertThat(picture.bytes()).isEqualTo(TestWorkbooks.onePixelPng());
        assertThat(picture.fromRowNo()).isEqualTo(1);
        assertThat(picture.fromColumnNo()).isEqualTo(1);
        assertThat(picture.toRowNo()).isEqualTo(5);
        assertThat(picture.toColumnNo()).isEqualTo(3);
    }

    @Test
    void 그림이_없는_xlsx_는_빈_목록을_준다() throws Exception {
        // getDrawingPatriarch() 는 그림이 없으면 null 을 준다. 막지 않으면 NPE 다.
        assertThat(importFixture().pictures()).isEmpty();
    }

    @Test
    void 그림이_여럿이면_전부_읽고_순서를_매긴다() throws Exception {
        SheetDto sheet = service.importFirstSheet(
                new ByteArrayInputStream(TestWorkbooks.withTwoSamePictures()));

        assertThat(sheet.pictures()).hasSize(2);
        assertThat(sheet.pictures()).extracting(PictureDto::zOrder).containsExactly(0, 1);
    }

    private SheetDto importFixture() throws Exception {
        return service.importFirstSheet(new ByteArrayInputStream(TestWorkbooks.mixedSheet()));
    }

    private CellDto cellAt(SheetDto sheet, int rowNo, int columnNo) {
        return sheet.cells().stream()
                .filter(c -> c.rowNo() == rowNo && c.columnNo() == columnNo)
                .findFirst()
                .orElseThrow(() -> new AssertionError("셀 없음: " + rowNo + "," + columnNo));
    }

    @Test
    void 값이_없어도_테두리가_있으면_읽는다() throws Exception {
        // 엑셀 서식표는 빈 칸에도 테두리를 긋는다. 값만 보고 버리면 올린 순간
        // 격자가 통째로 사라진다(2026-08-13 지적).
        SheetDto sheet = service.importWorkbook(
                new ByteArrayInputStream(TestWorkbooks.borderedBlank()), "테두리.xlsx")
                .sheets().get(0);

        CellDto cell = cellAt(sheet, 0, 0);
        assertThat(cell.valueType()).isEqualTo(CellDto.ValueType.STRING);
        assertThat(cell.textValue()).isEmpty();
        assertThat(cell.styleValue()).contains("bd");
    }

    @Test
    void 값도_서식도_없는_셀은_그냥_버린다() throws Exception {
        // 빈 칸까지 다 담으면 시트 하나에 레코드가 수만 개로 불어난다.
        SheetDto sheet = service.importWorkbook(
                new ByteArrayInputStream(TestWorkbooks.borderedBlank()), "테두리.xlsx")
                .sheets().get(0);

        assertThat(sheet.cells()).extracting(CellDto::columnNo).doesNotContain(5);
    }

    @Test
    void applyBorder_가_없어도_테두리를_읽는다() throws Exception {
        // 엑셀이 아닌 도구(openpyxl 등)가 만든 파일은 xf 에 applyBorder 를 안 적는다.
        // POI 는 그러면 테두리를 없는 것으로 준다. 우리는 borderId 를 직접 본다
        // (2026-08-13 실측: 테두리를 그린 파일인데 격자가 통째로 사라졌다).
        SheetDto sheet = service.importWorkbook(
                new ByteArrayInputStream(TestWorkbooks.borderWithoutApplyFlag()),
                "테두리.xlsx").sheets().get(0);

        assertThat(cellAt(sheet, 0, 0).styleValue()).contains("bd");
    }

    @Test
    void 열_너비를_파일의_기본_글꼴로_잰다() throws Exception {
        // 늘 7px(Calibri 11)로 재던 탓에 맑은 고딕 12pt 문서의 열이 원본보다 10%
        // 남짓 좁았다(2026-08-13 지적).
        SheetDto sheet = service.importWorkbook(
                new ByteArrayInputStream(TestWorkbooks.bigDefaultFont()), "큰글꼴.xlsx")
                .sheets().get(0);

        int width = sheet.sizes().stream()
                .filter(one -> "COLUMN".equals(one.axisType()) && one.indexNo() == 0)
                .findFirst().orElseThrow().sizePx();

        // 20글자 × 7px + 5 = 145. 큰 글꼴이면 그보다 넓어야 한다.
        assertThat(width).isGreaterThan(145);
    }

    @Test
    void 손대지_않은_행_높이는_담지_않는다() throws Exception {
        // 파일 기본 행 높이가 우리 기본과 다르다고 온 행을 다 담으면 시트 하나가
        // 오만 줄이 된다(2026-08-18 실측: 49,862줄이 전부 같은 22px).
        SheetDto sheet = service.importWorkbook(
                new ByteArrayInputStream(TestWorkbooks.tallDefaultRows()), "높은시트.xlsx")
                .sheets().get(0);

        assertThat(sheet.sizes()).filteredOn(one -> "ROW".equals(one.axisType()))
                .extracting(weboffice.sheet.SheetSizeDto::indexNo)
                .containsExactly(2);
    }

    @Test
    void 시트_기본_행_높이를_따로_알려준다() throws Exception {
        // 손댄 줄만 담는 대신 기본 높이를 한 번 알려 준다. 안 그러면 온 줄이 우리
        // 기본(20px)으로 그려져 원본보다 짧아진다.
        SheetDto sheet = service.importWorkbook(
                new ByteArrayInputStream(TestWorkbooks.tallDefaultRows()), "높은시트.xlsx")
                .sheets().get(0);

        assertThat(sheet.defaultRowHeightPx()).isEqualTo(22);
    }

    @Test
    void 테마_색의_명암까지_읽는다() throws Exception {
        // 엑셀은 "흰색 배경1, 15% 더 어둡게" 를 테마 0 + tint -0.15 로 적는다.
        // 명암을 안 보면 흰색이 되어 회색 칠이 통째로 사라진다(2026-08-19 지적:
        // 업무 진행표의 회색 막대가 하나도 안 찍혔다).
        SheetDto sheet = service.importFirstSheet(
                new ByteArrayInputStream(TestWorkbooks.themeTintFill()));

        assertThat(cellAt(sheet, 0, 0).styleValue()).contains("#D9D9D9");
    }
}
