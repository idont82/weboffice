package weboffice.excel;

import java.io.ByteArrayInputStream;
import java.util.List;

import org.junit.jupiter.api.Test;

import weboffice.sheet.SheetSizeDto;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 서식과 행·열 크기의 xlsx 왕복.
 *
 * <p>DB 와 화면은 서식 전체를 들고 있는데 <b>내보내기가 숫자 서식만 실었다.</b>
 * 내려받은 파일의 {@code cellXfs} 가 기본 하나뿐이라 배경색·글자색·굵기·테두리가
 * 전부 빠졌고, {@code <cols>} 도 없어 열 너비가 사라졌다(2026-08-07 실측).
 *
 * <p>여기서 재는 것은 "우리가 쓴 것을 우리가 다시 읽는가" 다. 엑셀이 그것을 어떻게
 * 보여주는지는 브라우저·엑셀 실측의 몫이다.
 */
class ExcelStyleRoundTripTest {

    private final ExcelImportService importService = new ExcelImportService();
    private final ExcelExportService exportService = new ExcelExportService();

    /** Univer 가 실제로 내는 모양이다. 엑셀에서 붙여넣은 셀에서 그대로 가져왔다. */
    private static final String STYLE = """
            {"bd":{"b":{"s":1,"cl":{"rgb":"#000000"}},"t":{"s":1,"cl":{"rgb":"#000000"}},
                   "l":{"s":1,"cl":{"rgb":"#000000"}},"r":{"s":1,"cl":{"rgb":"#000000"}}},
             "bl":1,"it":1,"cl":{"rgb":"#FF0000"},"bg":{"rgb":"#FFFF00"},
             "ff":"맑은 고딕","fs":14,"ht":2,"vt":2,"tb":3,
             "ul":{"s":1},"st":{"s":1},"n":{"pattern":"#,##0"}}""";

    private SheetDto 왕복(SheetDto sheet) throws Exception {
        return importService.importFirstSheet(
                new ByteArrayInputStream(exportService.export(sheet)));
    }

    private static CellDto 셀(int rowNo, int columnNo, String text, String styleValue) {
        return new CellDto(rowNo, columnNo, CellDto.ValueType.STRING, text,
                null, null, null, null, styleValue);
    }

    private static tools.jackson.databind.JsonNode 서식(CellDto cell) {
        assertThat(cell.styleValue()).as("서식이 통째로 비었다").isNotNull();
        return tools.jackson.databind.json.JsonMapper.builder().build()
                .readTree(cell.styleValue());
    }

    @Test
    void 글꼴과_굵기_기울임이_왕복한다() throws Exception {
        SheetDto again = 왕복(new SheetDto("시트", List.of(셀(0, 0, "값", STYLE)),
                List.of(), List.of(), List.of()));

        var style = 서식(again.cells().get(0));
        assertThat(style.path("bl").asInt(0)).isEqualTo(1);
        assertThat(style.path("it").asInt(0)).isEqualTo(1);
        assertThat(style.path("ff").asString("")).isEqualTo("맑은 고딕");
        assertThat(style.path("fs").asInt(0)).isEqualTo(14);
        assertThat(style.path("ul").path("s").asInt(0)).isEqualTo(1);
        assertThat(style.path("st").path("s").asInt(0)).isEqualTo(1);
    }

    @Test
    void 글자색과_배경색이_왕복한다() throws Exception {
        SheetDto again = 왕복(new SheetDto("시트", List.of(셀(0, 0, "값", STYLE)),
                List.of(), List.of(), List.of()));

        var style = 서식(again.cells().get(0));
        assertThat(style.path("cl").path("rgb").asString("")).isEqualToIgnoringCase("#FF0000");
        assertThat(style.path("bg").path("rgb").asString("")).isEqualToIgnoringCase("#FFFF00");
    }

    @Test
    void 테두리_네_변이_왕복한다() throws Exception {
        SheetDto again = 왕복(new SheetDto("시트", List.of(셀(0, 0, "값", STYLE)),
                List.of(), List.of(), List.of()));

        var border = 서식(again.cells().get(0)).path("bd");
        for (String side : List.of("t", "b", "l", "r")) {
            assertThat(border.path(side).path("s").asInt())
                    .as("%s 변이 사라졌다", side).isEqualTo(1);
            assertThat(border.path(side).path("cl").path("rgb").asString())
                    .as("%s 변 색", side).isEqualToIgnoringCase("#000000");
        }
    }

    @Test
    void 정렬과_줄바꿈이_왕복한다() throws Exception {
        SheetDto again = 왕복(new SheetDto("시트", List.of(셀(0, 0, "값", STYLE)),
                List.of(), List.of(), List.of()));

        var style = 서식(again.cells().get(0));
        // 2 = 가운데, 3 = 줄바꿈(WrapStrategy.WRAP)
        assertThat(style.path("ht").asInt(0)).isEqualTo(2);
        assertThat(style.path("vt").asInt(0)).isEqualTo(2);
        assertThat(style.path("tb").asInt(0)).isEqualTo(3);
    }

    @Test
    void 숫자_서식도_그대로_남는다() throws Exception {
        // 이것만 되던 것을 다른 것을 붙이며 깨뜨리지 않는다.
        SheetDto again = 왕복(new SheetDto("시트", List.of(셀(0, 0, "값", STYLE)),
                List.of(), List.of(), List.of()));

        assertThat(서식(again.cells().get(0)).path("n").path("pattern").asString())
                .isEqualTo("#,##0");
    }

    @Test
    void 서식이_같은_셀은_스타일을_나눠_쓴다() throws Exception {
        // 셀마다 CellStyle 을 만들면 xlsx 의 cellXfs 가 셀 수만큼 불어난다.
        // 엑셀의 스타일 상한은 64,000 개다.
        SheetDto sheet = new SheetDto("시트",
                List.of(셀(0, 0, "가", STYLE), 셀(0, 1, "나", STYLE), 셀(1, 0, "다", STYLE)),
                List.of(), List.of(), List.of());

        byte[] bytes = exportService.export(sheet);

        try (var wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook(
                new ByteArrayInputStream(bytes))) {
            // 기본 하나 + 우리 것 하나.
            assertThat(wb.getNumCellStyles()).isEqualTo(2);
        }
    }

    @Test
    void 글꼴이_같으면_서식이_달라도_한_벌을_나눠_쓴다() throws Exception {
        // 서식마다 글꼴을 새로 만들면 fonts 수가 cellXfs 수와 같아진다 - 굵기만
        // 다르고 글꼴은 같은 서식들이 저마다 글꼴을 하나씩 차지한다(2026-08-07 실측).
        // 엑셀의 글꼴 상한은 32,767 이고, 그 전에 파일이 쓸데없이 커진다.
        String 굵게 = "{\"bl\":1,\"ff\":\"맑은 고딕\",\"fs\":11}";
        String 기울임 = "{\"it\":1,\"ff\":\"맑은 고딕\",\"fs\":11}";
        String 굵게또 = "{\"bl\":1,\"ff\":\"맑은 고딕\",\"fs\":11,\"ht\":2}";

        SheetDto sheet = new SheetDto("시트",
                List.of(셀(0, 0, "가", 굵게), 셀(0, 1, "나", 기울임), 셀(0, 2, "다", 굵게또)),
                List.of(), List.of(), List.of());

        try (var wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook(
                new ByteArrayInputStream(exportService.export(sheet)))) {
            // 서식은 셋이지만 글꼴은 기본 + 굵게 + 기울임 = 셋이다.
            // 굵게와 굵게또는 정렬만 다르므로 글꼴을 나눠 쓴다.
            assertThat(wb.getNumCellStyles()).isEqualTo(4);
            assertThat(wb.getNumberOfFonts()).isEqualTo(3);
        }
    }

    @Test
    void 서식이_없는_셀은_스타일을_만들지_않는다() throws Exception {
        SheetDto again = 왕복(new SheetDto("시트", List.of(셀(0, 0, "값", null)),
                List.of(), List.of(), List.of()));

        assertThat(again.cells().get(0).styleValue()).isNull();
    }

    @Test
    void 행_높이와_열_너비가_왕복한다() throws Exception {
        SheetDto sheet = new SheetDto("시트", List.of(셀(0, 0, "값", null)), List.of(), List.of(),
                List.of(new SheetSizeDto("ROW", 0, 48), new SheetSizeDto("COLUMN", 1, 210)));

        SheetDto again = 왕복(sheet);

        assertThat(again.sizes()).as("크기가 통째로 사라졌다").isNotEmpty();
        SheetSizeDto row = 크기(again, "ROW", 0);
        SheetSizeDto column = 크기(again, "COLUMN", 1);
        // 픽셀 → 포인트 → 픽셀은 정확히 안 떨어진다. 한 픽셀 어긋남까지는 받는다.
        assertThat(row.sizePx()).isBetween(47, 49);
        assertThat(column.sizePx()).isBetween(207, 213);
    }

    @Test
    void 손대지_않은_열도_데이터_범위_안이면_너비를_적는다() throws Exception {
        // 손댄 줄만 저장하니, 화면 기본과 엑셀 기본이 다르면 그 줄들이 왕복마다
        // 어긋난다 - 엑셀에서 열면 좁아져 글자가 잘렸다(2026-08-07 사용자 실측).
        //
        // SXSSF 는 기본 열 너비를 정수 글자수로만 받아 엑셀 기본 8.43글자(64px)를
        // 줄 수 없다. 그래서 데이터가 닿는 열마다 너비를 적는다.
        SheetDto sheet = new SheetDto("시트",
                List.of(셀(0, 0, "가", null), 셀(0, 2, "다", null)),
                List.of(), List.of(),
                List.of(new SheetSizeDto("COLUMN", 1, 150)));

        try (var wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook(
                new ByteArrayInputStream(exportService.export(sheet)))) {
            var out = wb.getSheetAt(0);
            assertThat(Px.fromColumnUnits(out.getColumnWidth(0)))
                    .as("손대지 않은 열도 기본 너비로 적힌다").isBetween(63, 65);
            assertThat(Px.fromColumnUnits(out.getColumnWidth(1)))
                    .as("손댄 열은 그 값").isBetween(149, 151);
            assertThat(Px.fromColumnUnits(out.getColumnWidth(2)))
                    .as("데이터가 있는 마지막 열까지").isBetween(63, 65);
            assertThat(Px.fromPoints(out.getDefaultRowHeightInPoints()))
                    .as("행은 기본값을 실수로 줄 수 있다").isBetween(19, 21);
        }
    }

    @Test
    void 기본_높이는_시트에_적고_손댄_줄만_행에_적는다() throws Exception {
        // 높이를 아무 데도 안 적으면 엑셀이 글꼴에 맞춰 스스로 높인다. 맑은 고딕
        // 11pt 는 기본 15pt 보다 높아 화면보다 성기게 보였다(2026-08-07 실측).
        //
        // 그렇다고 온 행에 ht 를 적으면 엑셀이 그 줄을 "높이를 지정한 줄" 로 표시하고,
        // 그 파일을 다시 읽을 때 온 줄이 크기 레코드가 된다(2026-08-18 실측:
        // 시트 하나에 49,862줄). 기본 높이는 시트에 한 번만 적는다.
        SheetDto sheet = new SheetDto("시트",
                List.of(셀(0, 0, "가", null), 셀(3, 0, "나", null)),
                List.of(), List.of(),
                List.of(new SheetSizeDto("ROW", 1, 48)));

        String xml = sheetXmlOf(exportService.export(sheet));
        java.util.List<String> rows = new java.util.ArrayList<>();
        var matcher = java.util.regex.Pattern.compile("<row [^>]*>").matcher(xml);
        while (matcher.find()) {
            rows.add(matcher.group());
        }

        assertThat(xml).as("시트 기본 높이를 적어야 엑셀이 제 마음대로 높이지 않는다")
                .contains("defaultRowHeight=\"15.0\"");
        assertThat(rows).as("행이 만들어져야 한다").isNotEmpty();
        assertThat(rows).filteredOn(row -> row.contains("ht="))
                .as("손댄 줄만 높이를 적는다").hasSize(1);
        assertThat(rows).filteredOn(row -> row.contains("ht="))
                .first(org.assertj.core.api.InstanceOfAssertFactories.STRING)
                .contains("customHeight");
    }

    /**
     * xlsx 안의 시트 xml 을 읽는다.
     *
     * <p>{@code ZipInputStream} 을 쓰지 않는다. SXSSF 가 쓴 zip 은 엔트리 크기를
     * 0 으로 적어 스트림으로 읽으면 "invalid entry size" 가 난다. 임시 파일에 놓고
     * {@code ZipFile} 로 여는 쪽은 중앙 디렉터리를 보므로 문제가 없다.
     */
    private static String sheetXmlOf(byte[] xlsx) throws Exception {
        java.nio.file.Path temp = java.nio.file.Files.createTempFile("wo", ".xlsx");
        try {
            java.nio.file.Files.write(temp, xlsx);
            try (var zip = new java.util.zip.ZipFile(temp.toFile())) {
                var entry = zip.getEntry("xl/worksheets/sheet1.xml");
                assertThat(entry).as("sheet1.xml 이 있어야 한다").isNotNull();
                try (var in = zip.getInputStream(entry)) {
                    return new String(in.readAllBytes(),
                            java.nio.charset.StandardCharsets.UTF_8);
                }
            }
        } finally {
            java.nio.file.Files.deleteIfExists(temp);
        }
    }

    @Test
    void 손대지_않은_열은_크기를_적지_않는다() throws Exception {
        // 전부 적으면 시트 하나에 열이 만 육천 개라 표가 손댄 적 없는 줄로 채워진다.
        SheetDto again = 왕복(new SheetDto("시트", List.of(셀(0, 0, "값", null)),
                List.of(), List.of(), List.of()));

        assertThat(again.sizes()).isEmpty();
    }

    private static SheetSizeDto 크기(SheetDto sheet, String axisType, int indexNo) {
        return sheet.sizes().stream()
                .filter(s -> s.axisType().equals(axisType) && s.indexNo() == indexNo)
                .findFirst()
                .orElseThrow(() -> new AssertionError(axisType + " " + indexNo + " 없음"));
    }

}
