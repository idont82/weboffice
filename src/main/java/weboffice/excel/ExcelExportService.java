package weboffice.excel;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.ClientAnchor;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Drawing;
import org.apache.poi.ss.usermodel.FormulaError;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;

import weboffice.sheet.SheetSizeDto;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Service;

/**
 * SheetDto → xlsx 변환.
 *
 * SXSSF(윈도우 100행)를 써서 셀 수가 늘어도 메모리 사용량을 일정하게 유지한다(스펙 5절).
 * 숫자 서식은 문자열 → CellStyle 캐시로 중복 생성을 막는다.
 */
@Service
public class ExcelExportService {

    private static final int WINDOW_SIZE = 100;

    public byte[] export(SheetDto sheetDto) throws IOException {
        return export(new WorkbookDataDto(null, List.of(sheetDto)));
    }

    public byte[] export(WorkbookDataDto workbook) throws IOException {
        SXSSFWorkbook wb = new SXSSFWorkbook(WINDOW_SIZE);

        // POI 5.2.5 의 SXSSFWorkbook.close() 는 dispose() 를 호출하지 않는다
        // (자동 dispose 는 5.3.0 에서 추가됐다). 예외가 나면 윈도우 밖 행을 담아둔
        // 임시 파일이 디스크에 남으므로 finally 에서 명시적으로 정리한다.
        try (wb; ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            // 스타일 캐시를 시트 간에 공유한다. 시트마다 새로 만들면 같은 서식이
            // 시트 수만큼 중복 생성된다. 엑셀의 스타일 상한은 64,000 개다.
            CellStyleMapper styles = CellStyleMapper.create();
            for (SheetDto sheetDto : workbook.sheets()) {
                var made = wb.createSheet(sheetDto.sheetName());
                // 기본 높이를 못 박는다. 안 적으면 엑셀이 글꼴에 맞춰 스스로 높여
                // 화면보다 성기게 보인다(2026-08-07 사용자 실측).
                made.setDefaultRowHeightInPoints(Px.toPoints(Px.DEFAULT_ROW_HEIGHT));
                writeSheet(wb, made, sheetDto, styles);
            }

            wb.write(out);
            return out.toByteArray();
        } finally {
            wb.dispose();
        }
    }

    private void writeSheet(SXSSFWorkbook wb, Sheet sheet, SheetDto sheetDto,
                            CellStyleMapper styles) {
        // 열 너비를 셀보다 먼저 쓴다. SXSSF 는 열 설정에 윈도우 제약이 없지만
        // 행 높이는 그 행이 아직 윈도우 안에 있을 때만 줄 수 있다.
        writeColumnWidths(sheet, sheetDto);

        // 행 기본은 실수로 줄 수 있다. 열과 달리 줄마다 적을 수 없어서다 - 행은
        // 백만 개가 될 수 있다.
        sheet.setDefaultRowHeightInPoints(Px.toPoints(Px.DEFAULT_ROW_HEIGHT));

        Map<Integer, Integer> rowHeights = rowHeightsOf(sheetDto.sizes());

        // SXSSF 는 윈도우를 넘긴 행에 접근할 수 없다. 행 번호 오름차순으로 써야 한다.
        sheetDto.cells().stream()
                .sorted((a, b) -> a.rowNo() != b.rowNo()
                        ? Integer.compare(a.rowNo(), b.rowNo())
                        : Integer.compare(a.columnNo(), b.columnNo()))
                .forEach(dto -> writeCell(wb, sheet, dto, styles, rowHeights));

        // 값이 없는 행의 높이. 위 반복에서 행이 안 만들어졌으므로 여기서 만든다.
        // 남은 것만 도는 이유: 이미 쓴 행을 다시 건드리면 윈도우 밖이라 터진다.
        for (Map.Entry<Integer, Integer> entry : rowHeights.entrySet()) {
            Row row = sheet.getRow(entry.getKey());
            if (row == null) {
                row = sheet.createRow(entry.getKey());
            }
            row.setHeightInPoints(Px.toPoints(entry.getValue()));
        }

        for (MergeRangeDto merge : sheetDto.mergeRanges()) {
            sheet.addMergedRegion(new CellRangeAddress(
                    merge.fromRowNo(), merge.toRowNo(),
                    merge.fromColumnNo(), merge.toColumnNo()));
        }

        writePictures(wb, sheet, sheetDto.pictures());

        // 얼린 자리와 필터. 이것을 흘리면 내려받은 파일에서 머리줄 고정과 걸러
        // 보기가 사라진다(2026-08-25 지적).
        if (sheetDto.freezeRowNo() > 0 || sheetDto.freezeColumnNo() > 0) {
            sheet.createFreezePane(sheetDto.freezeColumnNo(), sheetDto.freezeRowNo());
        }
        if (sheetDto.filterRange() != null && !sheetDto.filterRange().isBlank()) {
            sheet.setAutoFilter(CellRangeAddress.valueOf(sheetDto.filterRange()));
        }
    }

    /**
     * 그림을 쓴다. SXSSF 도 그림을 지원한다 - 행 윈도우 제약은 셀에만 걸린다.
     *
     * <p>두 셀 앵커(from + to)를 쓴다. Univer 의 sheetTransform 이 같은 모양이라
     * 변환 손실이 없다. 한 셀 앵커로 쓰면 크기 정보가 xlsx 에서 사라진다.
     *
     * <p>{@code addPicture} 는 같은 바이트에 같은 인덱스를 되돌려주므로 중복
     * 그림이 파일 안에서 한 벌로 유지된다. 우리가 따로 캐시할 필요가 없다.
     *
     * <p>그림이 없으면 {@code createDrawingPatriarch()} 를 부르지 않는다.
     * 부르면 빈 drawing XML 이 붙는다.
     */
    private void writePictures(SXSSFWorkbook wb, Sheet sheet, List<PictureDto> pictures) {
        if (pictures.isEmpty()) {
            return;
        }
        Drawing<?> patriarch = sheet.createDrawingPatriarch();
        CreationHelper helper = wb.getCreationHelper();

        for (PictureDto picture : pictures) {
            byte[] bytes = toPoiSupported(picture.bytes());
            int index = wb.addPicture(bytes, pictureTypeOf(bytes));

            ClientAnchor anchor = helper.createClientAnchor();
            anchor.setRow1(picture.fromRowNo());
            anchor.setDy1(Emu.toEmu(picture.fromRowOffset()));
            anchor.setCol1(picture.fromColumnNo());
            anchor.setDx1(Emu.toEmu(picture.fromColumnOffset()));
            anchor.setRow2(picture.toRowNo());
            anchor.setDy2(Emu.toEmu(picture.toRowOffset()));
            anchor.setCol2(picture.toColumnNo());
            anchor.setDx2(Emu.toEmu(picture.toColumnOffset()));

            patriarch.createPicture(anchor, index);
        }
    }

    /**
     * POI 가 xlsx 에 넣을 수 있는 형식으로 맞춘다.
     *
     * <p>POI 5.2.5 의 {@code Workbook} 은 EMF·WMF·PICT·JPEG·PNG·DIB 상수만 갖는다.
     * <b>GIF 상수가 아예 없다</b>(2026-08-05 실측). 우리는 업로드에서 GIF 와 BMP 도
     * 받으므로 그대로 두면 이 둘이 xlsx 에서 유실된다.
     *
     * <p>PNG 로 다시 인코딩해 넘긴다. 범위를 줄여 GIF·BMP 업로드를 막는 쪽보다
     * 낫다 - 사용자가 넣을 수 있던 그림이 내려받기에서만 사라지는 것이 가장 나쁘다.
     * 애니메이션 GIF 는 첫 프레임만 남지만 엑셀은 어차피 움직이지 않는다.
     *
     * <p>변환에 실패하면 원본을 그대로 넘긴다. POI 가 거부하면 그때 예외가 나는데,
     * 여기서 조용히 그림을 버리는 것보다 드러나는 편이 낫다.
     */
    private byte[] toPoiSupported(byte[] bytes) {
        if (isJpeg(bytes) || isPng(bytes)) {
            return bytes;
        }
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null) {
                return bytes;
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            return bytes;
        }
    }

    /**
     * 바이트에서 POI 그림 형식을 판별한다.
     *
     * <p>{@code ImageService} 와 같은 판별을 다시 하는 셈이지만 그쪽은 MIME
     * 문자열을, 이쪽은 POI 상수를 낸다. 이 경로는 xlsx import 로 들어온 바이트도
     * 다루는데 그때는 ImageService 를 거치지 않는다.
     *
     * <p>{@code toPoiSupported} 를 지난 바이트만 들어오므로 JPEG 아니면 PNG 다.
     */
    private int pictureTypeOf(byte[] bytes) {
        return isJpeg(bytes) ? Workbook.PICTURE_TYPE_JPEG : Workbook.PICTURE_TYPE_PNG;
    }

    private boolean isJpeg(byte[] bytes) {
        return bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF;
    }

    private boolean isPng(byte[] bytes) {
        return bytes.length >= 8
                && (bytes[0] & 0xFF) == 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4E && bytes[3] == 0x47;
    }

    /**
     * 데이터가 닿는 열의 너비를 쓴다.
     *
     * <p>손댄 열만 쓰지 않는다. SXSSF 는 기본 열 너비를 <b>정수 글자수</b>로만 받아
     * 엑셀 기본 8.43글자(64px)를 줄 수 없다(8글자=56px 이 최선이다). 그대로 두면
     * 손대지 않은 열이 화면보다 좁아져 글자가 잘린다(2026-08-07 사용자 실측).
     *
     * <p>데이터가 닿는 데까지만 적는다. 전부 적으면 시트마다 열 만 육천 개가 실린다.
     */
    private void writeColumnWidths(Sheet sheet, SheetDto sheetDto) {
        Map<Integer, Integer> custom = new HashMap<>();
        for (SheetSizeDto size : sheetDto.sizes()) {
            if ("COLUMN".equals(size.axisType()) && size.sizePx() != null) {
                custom.put(size.indexNo(), size.sizePx());
            }
        }

        int lastColumn = sheetDto.cells().stream()
                .mapToInt(CellDto::columnNo).max().orElse(-1);
        for (Integer indexNo : custom.keySet()) {
            lastColumn = Math.max(lastColumn, indexNo);
        }

        for (int columnNo = 0; columnNo <= lastColumn; columnNo++) {
            int width = custom.getOrDefault(columnNo, Px.DEFAULT_COLUMN_WIDTH);
            sheet.setColumnWidth(columnNo, Px.toColumnUnits(width));
        }
    }

    private Map<Integer, Integer> rowHeightsOf(List<SheetSizeDto> sizes) {
        Map<Integer, Integer> heights = new HashMap<>();
        for (SheetSizeDto size : sizes) {
            if ("ROW".equals(size.axisType()) && size.sizePx() != null) {
                heights.put(size.indexNo(), size.sizePx());
            }
        }
        return heights;
    }

    private void writeCell(SXSSFWorkbook wb, Sheet sheet, CellDto dto,
                           CellStyleMapper styles, Map<Integer, Integer> rowHeights) {
        Row row = sheet.getRow(dto.rowNo());
        if (row == null) {
            row = sheet.createRow(dto.rowNo());
            // 높이를 안 적으면 엑셀이 글꼴에 맞춰 스스로 높인다. 맑은 고딕 11pt 는
            // 기본 15pt 보다 높아 화면보다 성기게 보였다(2026-08-07 사용자 실측).
            // 그래서 손대지 않은 행에도 기본 높이를 적는다 - 열과 같은 이유다.
            // 손댄 줄만 적는다. 손대지 않은 줄까지 적으면 엑셀이 그 줄을 "높이를
            // 지정한 줄" 로 표시하고, 그 파일을 다시 읽을 때 온 줄이 크기 레코드가
            // 된다(2026-08-18 실측: 시트 하나에 49,862줄). 손대지 않은 줄은 시트
            // 기본 높이로 그려진다 - 그 값은 시트를 만들 때 못 박는다.
            Integer height = rowHeights.remove(dto.rowNo());
            if (height != null) {
                row.setHeightInPoints(Px.toPoints(height));
            }
        }
        Cell cell = row.createCell(dto.columnNo());

        // 수식 셀은 수식 문자열을 먼저 쓰고, 이어서 캐시 결과 값을 쓴다.
        // 캐시 값을 쓰지 않으면 xlsx 에 결과가 없어 다시 읽을 때
        // 결과 타입이 항상 NUMERIC 으로 잡히고, 문자열 수식의 분류가 깨진다.
        if (dto.formulaValue() != null) {
            cell.setCellFormula(dto.formulaValue());
        }
        writeValue(cell, dto);

        // 서식 전체를 옮긴다. 2026-08-07 이전에는 숫자 서식만 실어 배경색·글자색·
        // 굵기·테두리가 내려받기에서 사라졌다. 매핑은 CellStyleMapper 에 있다.
        CellStyle style = styles.toPoi(wb, dto.styleValue());
        if (style != null) {
            cell.setCellStyle(style);
        }
    }

    /** valueType 에 따라 값을 쓴다. 수식 셀에서는 이것이 캐시 결과가 된다. */
    private void writeValue(Cell cell, CellDto dto) {
        switch (dto.valueType()) {
            case STRING -> cell.setCellValue(dto.textValue());
            case NUMBER -> {
                if (dto.numberValue() != null) {
                    cell.setCellValue(dto.numberValue().doubleValue());
                }
            }
            case DATETIME -> cell.setCellValue(dto.datetimeValue());
            case BOOLEAN -> cell.setCellValue("Y".equals(dto.booleanYn()));
            case ERROR -> cell.setCellErrorValue(
                    FormulaError.forString(dto.textValue()).getCode());
        }
    }
}
