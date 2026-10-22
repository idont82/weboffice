package weboffice.excel;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FormulaError;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFClientAnchor;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.apache.poi.xssf.usermodel.XSSFPicture;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFShape;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import weboffice.sheet.SheetSizeDto;
import org.springframework.stereotype.Service;

/**
 * xlsx → SheetDto 변환.
 *
 * P0 범위는 XSSF DOM 방식(스펙 5절의 5천 셀 이하 경로)이다.
 * 대용량용 XSSF Event API(SAX) 경로는 P2에서 추가한다.
 */
@Service
public class ExcelImportService {

    public SheetDto importFirstSheet(InputStream in) throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook(in)) {
            return toSheetDto(wb.getSheetAt(0));
        }
    }

    /** 전 시트를 읽는다. 첫 시트만 저장하면 export 때 나머지가 소실된다. */
    public WorkbookDataDto importWorkbook(InputStream in, String workbookName)
            throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook(in)) {
            List<SheetDto> sheets = new ArrayList<>();
            for (int i = 0; i < wb.getNumberOfSheets(); i++) {
                sheets.add(toSheetDto(wb.getSheetAt(i)));
            }
            return new WorkbookDataDto(workbookName, sheets);
        }
    }

    private SheetDto toSheetDto(Sheet sheet) {
        // 서식 매퍼를 시트 하나 동안 들고 다닌다. 셀마다 새로 만들면 같은 서식을
        // 셀 수만큼 다시 만든다.
        CellStyleMapper styles = sheet.getWorkbook() instanceof XSSFWorkbook book
                ? CellStyleMapper.create(book.getStylesSource())
                : CellStyleMapper.create();

        List<CellDto> cells = new ArrayList<>();
        for (Row row : sheet) {
            for (Cell cell : row) {
                CellDto dto = toDto(cell, styles);
                if (dto != null) {
                    cells.add(dto);
                }
            }
        }

        List<MergeRangeDto> merges = new ArrayList<>();
        for (CellRangeAddress range : sheet.getMergedRegions()) {
            merges.add(new MergeRangeDto(
                    range.getFirstRow(), range.getFirstColumn(),
                    range.getLastRow(), range.getLastColumn()));
        }

        return new SheetDto(sheet.getSheetName(), cells, merges, toPictures(sheet),
                defaultRowHeightOf(sheet), toSizes(sheet),
                frozenRowsOf(sheet), frozenColumnsOf(sheet), filterRangeOf(sheet));
    }

    /** 얼려 둔 줄 수. 얼리지 않았으면 0 이다. */
    private int frozenRowsOf(Sheet sheet) {
        var pane = sheet instanceof XSSFSheet book ? book.getPaneInformation() : null;
        return pane == null || !pane.isFreezePane() ? 0 : pane.getHorizontalSplitPosition();
    }

    /** 얼려 둔 칸 수. 얼리지 않았으면 0 이다. */
    private int frozenColumnsOf(Sheet sheet) {
        var pane = sheet instanceof XSSFSheet book ? book.getPaneInformation() : null;
        return pane == null || !pane.isFreezePane() ? 0 : pane.getVerticalSplitPosition();
    }

    /**
     * 필터를 건 자리({@code A1:B2}). 없으면 null 이다.
     *
     * <p>POI 는 필터 범위를 읽는 문을 열어 주지 않아 시트 XML 을 바로 본다.
     */
    private String filterRangeOf(Sheet sheet) {
        if (!(sheet instanceof XSSFSheet book)) {
            return null;
        }
        var filter = book.getCTWorksheet().getAutoFilter();
        return filter == null || filter.getRef() == null || filter.getRef().isBlank()
                ? null : filter.getRef();
    }

    /**
     * 이 파일이 열 너비를 재는 잣대(기본 글꼴 숫자 한 글자의 폭, px).
     *
     * <p>기본 글꼴은 서식표의 첫 글꼴이다. 엑셀이 그 글꼴로 열 너비를 잰다.
     */
    private int digitWidthOf(Sheet sheet) {
        if (!(sheet.getWorkbook() instanceof XSSFWorkbook book)
                || book.getNumberOfFonts() == 0) {
            return DigitWidth.FALLBACK;
        }
        var font = book.getFontAt(0);
        return DigitWidth.of(font.getFontName(), font.getFontHeightInPoints());
    }

    /**
     * 그림을 읽는다. 그림이 없으면 {@code getDrawingPatriarch()} 가 null 이다.
     *
     * <p>XSSFPicture 만 골라낸다. 도형·차트·텍스트박스는 이번 범위가 아니다.
     * 걸러내지 않으면 캐스팅에서 터진다.
     *
     * <p>앵커의 dx/dy 는 EMU 다. 여기서 픽셀로 걷어내 밖으로 흘리지 않는다.
     */
    private List<PictureDto> toPictures(Sheet sheet) {
        List<PictureDto> pictures = new ArrayList<>();
        if (!(sheet instanceof XSSFSheet xssfSheet)) {
            return pictures;
        }
        XSSFDrawing patriarch = xssfSheet.getDrawingPatriarch();
        if (patriarch == null) {
            return pictures;
        }

        int zOrder = 0;
        for (XSSFShape shape : patriarch.getShapes()) {
            if (!(shape instanceof XSSFPicture picture)) {
                continue;
            }
            XSSFClientAnchor anchor = picture.getClientAnchor();
            if (anchor == null) {
                // 앵커가 없으면 어디에 놓을지 알 수 없다. 버린다.
                continue;
            }
            pictures.add(new PictureDto(
                    picture.getPictureData().getData(),
                    anchor.getRow1(), Emu.toPixels(anchor.getDy1()),
                    anchor.getCol1(), Emu.toPixels(anchor.getDx1()),
                    anchor.getRow2(), Emu.toPixels(anchor.getDy2()),
                    anchor.getCol2(), Emu.toPixels(anchor.getDx2()),
                    zOrder++));
        }
        return pictures;
    }

    /**
     * 기본과 다른 행 높이·열 너비만 읽는다.
     *
     * <p>기본과 같은 줄은 적지 않는다. 전부 적으면 표가 손댄 적 없는 줄로 채워진다
     * (화면 저장과 같은 규칙).
     *
     * <p>열은 셀을 훑어 찾지 않는다. 시트에 열이 만 육천 개고, 값이 없는 열의 너비를
     * 손댔을 수도 있어서다. xlsx 가 손댄 열만 {@code <col>} 로 적으므로 그것을 읽는다.
     */
    /** 이 시트의 기본 행 높이(px). 적혀 있지 않으면 우리 기본이다. */
    private int defaultRowHeightOf(Sheet sheet) {
        float points = sheet.getDefaultRowHeightInPoints();
        return points > 0 ? Px.fromPoints(points) : Px.DEFAULT_ROW_HEIGHT;
    }

    /**
     * 높이를 따로 적어 둔 줄인가.
     *
     * <p>손댐 표시(customHeight)만 보면 안 된다. 엑셀은 자동으로 맞춘 높이를 그
     * 표시 없이 적기도 하는데, 그 줄을 흘리면 원본보다 낮게 그려진다
     * (2026-08-25 지적: 308줄부터 높이가 안 맞았다).
     */
    private boolean customHeight(Row row, float defaultPoints) {
        if (!(row instanceof XSSFRow xssfRow)) {
            return false;
        }
        if (xssfRow.getCTRow().getCustomHeight()) {
            return true;
        }
        // 적어 둔 높이가 이 파일 기본과 다를 때만 담는다. 기본과 같은 줄까지
        // 담으면 시트 하나가 오만 줄이 된다(2026-08-18 실측).
        return xssfRow.getCTRow().isSetHt()
                && Math.abs(row.getHeightInPoints() - defaultPoints) > 0.01;
    }

    private List<SheetSizeDto> toSizes(Sheet sheet) {
        List<SheetSizeDto> sizes = new ArrayList<>();

        // 파일이 말하는 기본이 아니라 우리 기본과 견준다. 우리 기본과 같은 줄은
        // 적지 않아야 화면에서 다시 그릴 때 같은 모습이 된다 - 파일 기본을 쓰면
        // 우리가 쓴 64px 열이 "기본과 다르다" 로 잡혀 표가 쓸데없이 불어난다.
        // 손으로 높인 줄만 담는다. 파일 기본 높이와 견주면 "기본과 다르다" 로
        // 온 줄이 잡혀 시트 하나가 오만 줄이 된다(2026-08-18 실측: 49,862줄이
        // 전부 같은 22px). 기본 높이는 아래에서 따로 알려 준다.
        float defaultPoints = sheet.getDefaultRowHeightInPoints();
        for (Row row : sheet) {
            if (!customHeight(row, defaultPoints)) {
                continue;
            }
            sizes.add(new SheetSizeDto("ROW", row.getRowNum(),
                    Px.fromPoints(row.getHeightInPoints())));
        }

        if (!(sheet instanceof XSSFSheet xssfSheet)) {
            return sizes;
        }
        int defaultWidth = Px.DEFAULT_COLUMN_WIDTH;
        for (var cols : xssfSheet.getCTWorksheet().getColsList()) {
            for (var col : cols.getColList()) {
                if (!col.isSetCustomWidth() || !col.getCustomWidth()) {
                    continue;
                }
                // min/max 는 1부터 세는 열 번호고 범위를 뜻한다. 우리 표는 줄마다 한 행이다.
                for (long index = col.getMin(); index <= col.getMax(); index++) {
                    int columnNo = (int) index - 1;
                    // 파일에 적힌 소수를 그대로 쓴다. POI 가 1/256 정수로 깎아 주면
                    // 엑셀보다 1px 좁은 열이 생긴다(2026-08-18 지적).
                    int width = Px.fromColumnChars(col.getWidth(), digitWidthOf(sheet));
                    if (width != defaultWidth) {
                        sizes.add(new SheetSizeDto("COLUMN", columnNo, width));
                    }
                }
            }
        }
        return sizes;
    }

    /** 빈 셀은 null 을 반환해 레코드를 만들지 않는다. */
    private CellDto toDto(Cell cell, CellStyleMapper styles) {
        int rowNo = cell.getRowIndex();
        int columnNo = cell.getColumnIndex();
        String numberFormat = numberFormatOf(cell);

        CellType type = cell.getCellType();

        // 수식 셀은 수식 문자열과 캐시된 계산 결과를 함께 보존한다(스펙 4절 설계 판단 3항).
        // valueType 은 "수식이냐"가 아니라 "결과 값이 무엇이냐"로 정한다.
        String formula = type == CellType.FORMULA ? cell.getCellFormula() : null;
        CellType valueSource = type == CellType.FORMULA
                ? cell.getCachedFormulaResultType()
                : type;

        // 서식 전체를 화면(Univer)이 쓰는 모양으로 담는다. 2026-08-07 이전에는
        // 숫자 서식만 읽어 엑셀 파일의 색·테두리·정렬이 올리는 순간 사라졌다.
        String styleValue = styles.toUniver(cell.getCellStyle(), numberFormat, cellFont(cell));

        return switch (valueSource) {
            case STRING -> new CellDto(rowNo, columnNo, CellDto.ValueType.STRING,
                    cell.getStringCellValue(), null, null, null, formula, styleValue);

            case NUMERIC -> DateUtil.isCellDateFormatted(cell)
                    ? new CellDto(rowNo, columnNo, CellDto.ValueType.DATETIME,
                            null, null, cell.getLocalDateTimeCellValue(), null, formula, styleValue)
                    : new CellDto(rowNo, columnNo, CellDto.ValueType.NUMBER,
                            null, BigDecimal.valueOf(cell.getNumericCellValue()),
                            null, null, formula, styleValue);

            case BOOLEAN -> new CellDto(rowNo, columnNo, CellDto.ValueType.BOOLEAN,
                    null, null, null, cell.getBooleanCellValue() ? "Y" : "N", formula, styleValue);

            case ERROR -> new CellDto(rowNo, columnNo, CellDto.ValueType.ERROR,
                    FormulaError.forInt(cell.getErrorCellValue()).getString(),
                    null, null, null, formula, styleValue);

            // 값이 없는 셀은 레코드를 만들지 않는다. 단 수식이 있거나(계산 결과가
            // 빈 경우) 서식이 있으면 그것만이라도 보존한다 - 엑셀 서식표는 빈 칸에도
            // 테두리를 긋는데, 값만 보고 버리면 올린 순간 격자가 통째로 사라진다
            // (2026-08-13 지적).
            case BLANK, _NONE -> formula == null && styleValue == null
                    ? null
                    : new CellDto(rowNo, columnNo, CellDto.ValueType.STRING,
                            "", null, null, null, formula, styleValue);

            default -> null;
        };
    }

    /**
     * POI 는 서식을 지정하지 않은 셀에도 "General" 을 반환한다.
     * 그대로 저장하면 "서식 없음"과 "명시적 General"을 구분할 수 없고
     * 문자열·불린 레코드까지 무의미한 서식 문자열로 오염된다. null 로 정규화한다.
     */
    private String numberFormatOf(Cell cell) {
        String format = cell.getCellStyle().getDataFormatString();
        if (format == null || format.isEmpty() || "General".equals(format)) {
            return null;
        }
        return format;
    }

    /**
     * 셀의 글꼴. 통합 문서에서 꺼낸다. 기본 글꼴이면 {@code null}.
     *
     * <p>{@code CellStyle.getFontIndex()} 는 인덱스만 주고 글꼴 자체는 통합 문서가
     * 갖는다. 0번은 통합 문서의 기본 글꼴이다.
     *
     * <p>기본 글꼴을 넘기지 않는 이유: POI 는 색을 안 준 기본 글꼴에도 검정을
     * 돌려준다. 그것을 적으면 서식이 없는 셀까지 {@code {"cl":{"rgb":"#000000"}}}
     * 를 갖게 되고, 서식 사전이 셀마다 한 행씩 불어난다.
     */
    private org.apache.poi.ss.usermodel.Font cellFont(Cell cell) {
        int index = cell.getCellStyle().getFontIndex();
        return index == 0 ? null : cell.getSheet().getWorkbook().getFontAt(index);
    }
}
