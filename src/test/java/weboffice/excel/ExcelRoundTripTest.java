package weboffice.excel;

import java.io.ByteArrayInputStream;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExcelRoundTripTest {

    private final ExcelImportService importService = new ExcelImportService();
    private final ExcelExportService exportService = new ExcelExportService();

    @Test
    void 읽고_쓰고_다시_읽으면_셀이_보존된다() throws Exception {
        SheetDto original = importService.importFirstSheet(
                new ByteArrayInputStream(TestWorkbooks.mixedSheet()));

        byte[] exported = exportService.export(original);
        SheetDto reimported = importService.importFirstSheet(new ByteArrayInputStream(exported));

        // 전 필드 비교는 양쪽이 똑같이 비어 있어도 통과한다.
        // 원본에 실제로 값이 들어있음을 먼저 고정한다.
        assertThat(original.cells()).hasSize(9);

        assertThat(reimported.sheetName()).isEqualTo(original.sheetName());
        assertThat(reimported.mergeRanges()).isEqualTo(original.mergeRanges());
        assertThat(reimported.cells())
                .usingRecursiveFieldByFieldElementComparator()
                .containsExactlyInAnyOrderElementsOf(original.cells());
    }

    @Test
    void 문자열_수식은_왕복_후에도_STRING_으로_남는다() throws Exception {
        SheetDto original = importService.importFirstSheet(
                new ByteArrayInputStream(TestWorkbooks.mixedSheet()));

        SheetDto reimported = importService.importFirstSheet(
                new ByteArrayInputStream(exportService.export(original)));

        CellDto cell = reimported.cells().stream()
                .filter(c -> c.rowNo() == 0 && c.columnNo() == 5)
                .findFirst()
                .orElseThrow(() -> new AssertionError("F1 셀 없음"));

        // 캐시 결과를 쓰지 않으면 여기서 NUMBER 로 변한다.
        assertThat(cell.valueType()).isEqualTo(CellDto.ValueType.STRING);
        assertThat(cell.textValue()).isEqualTo("품목명/EA");
        assertThat(cell.formulaValue()).isEqualTo("A1&\"/EA\"");
    }

    @Test
    void 그림이_왕복해도_바이트와_위치가_유지된다() throws Exception {
        SheetDto original = importService.importFirstSheet(
                new ByteArrayInputStream(TestWorkbooks.withPicture()));
        assertThat(original.pictures()).hasSize(1);

        SheetDto again = importService.importFirstSheet(
                new ByteArrayInputStream(exportService.export(original)));

        assertThat(again.pictures()).hasSize(1);
        PictureDto picture = again.pictures().get(0);
        assertThat(picture.bytes()).isEqualTo(original.pictures().get(0).bytes());
        assertThat(picture.fromRowNo()).isEqualTo(1);
        assertThat(picture.fromColumnNo()).isEqualTo(1);
        assertThat(picture.toRowNo()).isEqualTo(5);
        assertThat(picture.toColumnNo()).isEqualTo(3);
    }

    @Test
    void 오프셋도_왕복한다() throws Exception {
        // 앵커의 dx/dy 를 EMU 로 바꿔 쓰고 다시 픽셀로 읽는 경로다.
        // 상수가 어긋나면 여기서 값이 달라진다.
        PictureDto placed = new PictureDto(TestWorkbooks.onePixelPng(),
                2, 7, 3, 11, 8, 13, 6, 17, 0);
        SheetDto sheet = new SheetDto("시트1", java.util.List.of(), java.util.List.of(),
                java.util.List.of(placed));

        SheetDto again = importService.importFirstSheet(
                new ByteArrayInputStream(exportService.export(sheet)));

        PictureDto read = again.pictures().get(0);
        assertThat(read.fromRowOffset()).isEqualTo(7);
        assertThat(read.fromColumnOffset()).isEqualTo(11);
        assertThat(read.toRowOffset()).isEqualTo(13);
        assertThat(read.toColumnOffset()).isEqualTo(17);
    }

    @Test
    void 같은_그림이_두_장이어도_둘_다_나온다() throws Exception {
        SheetDto original = importService.importFirstSheet(
                new ByteArrayInputStream(TestWorkbooks.withTwoSamePictures()));

        SheetDto again = importService.importFirstSheet(
                new ByteArrayInputStream(exportService.export(original)));

        assertThat(again.pictures()).hasSize(2);
    }

    @Test
    void GIF_와_BMP_는_PNG_로_바뀌어_실린다() throws Exception {
        // POI 5.2.5 의 Workbook 에는 GIF 상수가 없다. 그대로 두면 GIF·BMP 로
        // 올린 그림이 내려받기에서만 조용히 사라진다.
        for (byte[] source : new byte[][] {onePixelGif(), onePixelBmp()}) {
            SheetDto sheet = new SheetDto("시트1", java.util.List.of(), java.util.List.of(),
                    java.util.List.of(new PictureDto(source, 0, 0, 0, 0, 2, 0, 2, 0, 0)));

            SheetDto again = importService.importFirstSheet(
                    new ByteArrayInputStream(exportService.export(sheet)));

            assertThat(again.pictures()).hasSize(1);
            byte[] written = again.pictures().get(0).bytes();
            assertThat(written[0] & 0xFF).as("PNG 시그니처여야 한다").isEqualTo(0x89);
            assertThat(new String(written, 1, 3, java.nio.charset.StandardCharsets.US_ASCII))
                    .isEqualTo("PNG");
        }
    }

    private byte[] onePixelGif() throws Exception {
        return encode("gif");
    }

    private byte[] onePixelBmp() throws Exception {
        return encode("bmp");
    }

    private byte[] encode(String format) throws Exception {
        java.awt.image.BufferedImage image =
                new java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_RGB);
        var out = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(image, format, out);
        return out.toByteArray();
    }

    @Test
    void 그림이_없으면_그리기_객체를_만들지_않는다() throws Exception {
        // createDrawingPatriarch() 를 무조건 부르면 그림 없는 시트에도 빈
        // drawing XML 이 붙는다.
        SheetDto sheet = new SheetDto("시트1", java.util.List.of(), java.util.List.of(),
                java.util.List.of());

        SheetDto again = importService.importFirstSheet(
                new ByteArrayInputStream(exportService.export(sheet)));

        assertThat(again.pictures()).isEmpty();
    }
    @Test
    void 틀_고정과_필터가_왕복한다() throws Exception {
        // 화면에서 잡은 틀 고정과 필터가 내려받은 파일에 없었다(2026-08-25 지적).
        byte[] source;
        try (var book = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
             var out = new java.io.ByteArrayOutputStream()) {
            var sheet = book.createSheet("첫 장");
            sheet.createRow(0).createCell(0).setCellValue("머리");
            sheet.getRow(0).createCell(1).setCellValue("값");
            sheet.createRow(1).createCell(0).setCellValue("가");
            // 두 줄·한 열을 얼려 두고 머리줄에 필터를 건다.
            sheet.createFreezePane(1, 2);
            sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0, 1, 0, 1));
            book.write(out);
            source = out.toByteArray();
        }

        SheetDto read = importService.importFirstSheet(new ByteArrayInputStream(source));
        assertThat(read.freezeRowNo()).isEqualTo(2);
        assertThat(read.freezeColumnNo()).isEqualTo(1);
        assertThat(read.filterRange()).isEqualTo("A1:B2");

        SheetDto again = importService.importFirstSheet(
                new ByteArrayInputStream(exportService.export(read)));
        assertThat(again.freezeRowNo()).isEqualTo(2);
        assertThat(again.freezeColumnNo()).isEqualTo(1);
        assertThat(again.filterRange()).isEqualTo("A1:B2");
    }

    @Test
    void 손댐_표시가_없어도_높이가_다르면_담는다() throws Exception {
        // 엑셀은 자동으로 맞춘 높이를 손댐 표시(customHeight) 없이 적기도 한다.
        // 그 줄을 흘리면 원본보다 낮게 그려진다(2026-08-25 지적: 308줄부터).
        byte[] source;
        try (var book = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
             var out = new java.io.ByteArrayOutputStream()) {
            var sheet = book.createSheet("첫 장");
            sheet.setDefaultRowHeightInPoints(15);
            var loud = sheet.createRow(0);
            loud.createCell(0).setCellValue("높은 줄");
            loud.setHeightInPoints(84);
            loud.getCTRow().setCustomHeight(false);
            var plain = sheet.createRow(1);
            plain.createCell(0).setCellValue("보통 줄");
            book.write(out);
            source = out.toByteArray();
        }

        SheetDto read = importService.importFirstSheet(new ByteArrayInputStream(source));

        assertThat(read.sizes())
                .filteredOn(one -> "ROW".equals(one.axisType()))
                .extracting(weboffice.sheet.SheetSizeDto::indexNo)
                .containsExactly(0);
    }

}
