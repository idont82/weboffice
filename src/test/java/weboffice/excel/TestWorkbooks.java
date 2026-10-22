package weboffice.excel;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Base64;

import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.FormulaError;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFClientAnchor;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

final class TestWorkbooks {

    private TestWorkbooks() {
    }

    /**
     * 검증 대상을 한 시트에 모은 픽스처.
     * A1 문자열(서식 없음) / B1 숫자(소수+서식) / C1 날짜 / D1 불린
     * E1 숫자 수식 / F1 문자열 수식 / G1 고정밀 소수 / H1 오류
     * A2 문자열 / B2 빈 셀(레코드 생성 안 됨) / A2:B2 병합
     */
    static byte[] mixedSheet() throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = wb.createSheet("시트1");
            CreationHelper helper = wb.getCreationHelper();

            Row row0 = sheet.createRow(0);
            row0.createCell(0).setCellValue("품목명");

            CellStyle numberStyle = wb.createCellStyle();
            numberStyle.setDataFormat(helper.createDataFormat().getFormat("#,##0.00"));
            var numberCell = row0.createCell(1);
            numberCell.setCellValue(1234.56);
            numberCell.setCellStyle(numberStyle);

            CellStyle dateStyle = wb.createCellStyle();
            dateStyle.setDataFormat(helper.createDataFormat().getFormat("yyyy-mm-dd"));
            var dateCell = row0.createCell(2);
            dateCell.setCellValue(LocalDateTime.of(2026, 7, 30, 0, 0));
            dateCell.setCellStyle(dateStyle);

            row0.createCell(3).setCellValue(true);
            row0.createCell(4).setCellFormula("B1*2");

            // 문자열을 반환하는 수식. valueType 이 STRING 으로 분류돼야 한다.
            row0.createCell(5).setCellFormula("A1&\"/EA\"");

            // 소수점 10자리. xlsx 숫자 셀은 double 이므로 유효숫자 15자리까지가 한계다.
            CellStyle highPrecisionStyle = wb.createCellStyle();
            highPrecisionStyle.setDataFormat(helper.createDataFormat().getFormat("0.0000000000"));
            var precisionCell = row0.createCell(6);
            precisionCell.setCellValue(0.1234567890);
            precisionCell.setCellStyle(highPrecisionStyle);

            row0.createCell(7).setCellErrorValue(FormulaError.DIV0.getCode());

            Row row1 = sheet.createRow(1);
            row1.createCell(0).setCellValue("병합셀");
            row1.createCell(1);
            sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, 1));

            // 수식 셀의 캐시 결과를 채운다. 이걸 하지 않으면 저장된 xlsx 에
            // 캐시 값이 없어 다시 읽을 때 결과 타입이 항상 NUMERIC 으로 잡힌다.
            wb.getCreationHelper().createFormulaEvaluator().evaluateAll();

            wb.write(out);
            return out.toByteArray();
        }
    }

    /** 그림 한 장이 B2:D6 에 놓인 xlsx. */
    static byte[] withPicture() throws IOException {
        return withPictures(1);
    }

    /**
     * 같은 그림이 두 장 놓인 xlsx. POI 의 addPicture 가 같은 바이트에 같은
     * 인덱스를 주는지, 우리 쪽 해시 중복 제거가 도는지 보는 픽스처다.
     */
    static byte[] withTwoSamePictures() throws IOException {
        return withPictures(2);
    }

    private static byte[] withPictures(int count) throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            XSSFSheet sheet = wb.createSheet("시트1");
            sheet.createRow(0).createCell(0).setCellValue("표 안의 글자");

            int index = wb.addPicture(onePixelPng(), Workbook.PICTURE_TYPE_PNG);
            XSSFDrawing patriarch = sheet.createDrawingPatriarch();

            for (int i = 0; i < count; i++) {
                XSSFClientAnchor anchor = new XSSFClientAnchor();
                anchor.setCol1(1);
                anchor.setRow1(1 + i * 10);
                anchor.setCol2(3);
                anchor.setRow2(5 + i * 10);
                patriarch.createPicture(anchor, index);
            }

            wb.write(out);
            return out.toByteArray();
        }
    }

    /**
     * 진짜 1x1 PNG. POI 가 xlsx 에 넣을 때 실제로 읽으므로 시그니처만 맞춘
     * 가짜 바이트로는 안 된다.
     */
    static byte[] onePixelPng() {
        return Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8"
                        + "z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");
    }

    /** 시트 3개. 각 시트에 셀 2개씩. 다중 시트 보존을 확인하는 픽스처다. */
    static byte[] threeSheets() throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            String[] names = {"1분기", "2분기", "3분기"};
            for (int i = 0; i < names.length; i++) {
                Sheet sheet = wb.createSheet(names[i]);
                Row row = sheet.createRow(0);
                row.createCell(0).setCellValue(names[i] + " 합계");
                row.createCell(1).setCellValue(100.0 * (i + 1));
            }

            wb.write(out);
            return out.toByteArray();
        }
    }

    /** 값 없이 테두리만 있는 칸이 든 시트. 옆 칸(F1)은 값도 서식도 없다. */
    static byte[] borderedBlank() throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            var sheet = wb.createSheet("시트1");
            var style = wb.createCellStyle();
            style.setBorderTop(org.apache.poi.ss.usermodel.BorderStyle.THIN);
            style.setBorderBottom(org.apache.poi.ss.usermodel.BorderStyle.THIN);
            style.setBorderLeft(org.apache.poi.ss.usermodel.BorderStyle.THIN);
            style.setBorderRight(org.apache.poi.ss.usermodel.BorderStyle.THIN);

            var row = sheet.createRow(0);
            row.createCell(0).setCellStyle(style);
            row.createCell(5);
            wb.write(out);
            return out.toByteArray();
        }
    }

    /**
     * 테두리를 쓰되 xf 에 applyBorder 를 적지 않은 시트.
     *
     * <p>엑셀이 아닌 도구가 만든 파일이 이렇다. POI 는 이 깃발이 없으면 테두리를
     * 없는 것으로 준다.
     */
    static byte[] borderWithoutApplyFlag() throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            var sheet = wb.createSheet("시트1");
            var style = (org.apache.poi.xssf.usermodel.XSSFCellStyle) wb.createCellStyle();
            style.setBorderTop(org.apache.poi.ss.usermodel.BorderStyle.THIN);
            style.setBorderBottom(org.apache.poi.ss.usermodel.BorderStyle.THIN);
            style.setBorderLeft(org.apache.poi.ss.usermodel.BorderStyle.THIN);
            style.setBorderRight(org.apache.poi.ss.usermodel.BorderStyle.THIN);
            // 깃발만 내린다. borderId 는 그대로다 - 남이 만든 파일과 같은 꼴이다.
            style.getCoreXf().setApplyBorder(false);

            sheet.createRow(0).createCell(0).setCellStyle(style);
            wb.write(out);
            return out.toByteArray();
        }
    }

    /** 기본 글꼴이 큰(맑은 고딕 12pt) 시트. 열 하나는 너비를 손댔다. */
    static byte[] bigDefaultFont() throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            var font = wb.getFontAt(0);
            font.setFontName("Malgun Gothic");
            font.setFontHeightInPoints((short) 12);

            var sheet = wb.createSheet("시트1");
            // 20 글자. 엑셀은 이 글자 수에 기본 글꼴 폭을 곱해 그린다.
            sheet.setColumnWidth(0, 20 * 256);
            sheet.createRow(0).createCell(0).setCellValue("값");
            wb.write(out);
            return out.toByteArray();
        }
    }

    /** 기본 행 높이가 16.5pt(22px)인 시트. 셋째 줄만 손으로 높였다. */
    static byte[] tallDefaultRows() throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            var sheet = wb.createSheet("시트1");
            sheet.setDefaultRowHeightInPoints(16.5f);
            for (int at = 0; at < 4; at++) {
                sheet.createRow(at).createCell(0).setCellValue("값");
            }
            var tall = sheet.getRow(2);
            tall.setHeightInPoints(40f);
            wb.write(out);
            return out.toByteArray();
        }
    }

    /**
     * 테마 색에 명암(tint)을 준 셀. A1 은 흰 테마를 15% 어둡게 칠한 회색이다.
     *
     * <p>엑셀에서 "채우기 색 - 흰색, 배경1, 15% 더 어둡게" 를 고르면 이 모양이
     * 나온다. 실제 업무 파일의 회색 막대가 모두 이것이다(IT 업무 진행표 2026).
     */
    static byte[] themeTintFill() throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            var sheet = wb.createSheet("시트1");
            // 엑셀 파일은 테마 번호만 적지만, POI 는 파일을 읽을 때 테마의 밑색을
            // rgb 에 채워 준다(실측: 진행표의 회색 칸이 #FFFFFF 로 들어왔다).
            // 그 상태를 그대로 흉내낸다 - 흰 밑색 + 명암 -15%.
            var themed = new org.apache.poi.xssf.usermodel.XSSFColor(
                    new byte[] { (byte) 0xFF, (byte) 0xFF, (byte) 0xFF },
                    wb.getStylesSource().getIndexedColors());
            themed.setTheme(0);
            themed.setTint(-0.149998474074526);
            var style = wb.createCellStyle();
            style.setFillForegroundColor(themed);
            style.setFillPattern(org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND);
            var cell = sheet.createRow(0).createCell(0);
            cell.setCellValue("회색");
            cell.setCellStyle(style);
            wb.write(out);
            return out.toByteArray();
        }
    }
}
