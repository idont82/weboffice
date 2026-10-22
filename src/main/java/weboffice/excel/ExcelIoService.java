package weboffice.excel;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import weboffice.common.AuditUser;
import weboffice.drawing.DrawingDto;
import weboffice.drawing.DrawingService;
import weboffice.drawing.ImageService;
import weboffice.sheet.CellRange;
import weboffice.sheet.MergeMapper;
import weboffice.sheet.SheetSizeService;
import weboffice.sheet.SheetMapper;
import weboffice.sheet.SheetService;
import weboffice.sheet.SheetSummaryDto;
import weboffice.workbook.WorkbookService;

/**
 * xlsx ↔ DB.
 *
 * <p>DB 에 직접 접근하지 않고 SheetService 를 경유한다(스펙 3절 모듈 책임).
 * 그래야 import 로 들어온 셀도 편집으로 들어온 셀과 똑같이 이력이 남는다.
 */
@Service
public class ExcelIoService {

    /**
     * xlsx 의 두 셀 앵커에 해당하는 Univer 값.
     *
     * <p>{@code SheetDrawingAnchorType.Both} 다. 숫자가 아니라 문자열 "1" 이며
     * 위치와 크기가 모두 셀을 따라간다는 뜻이다. "TWO_CELL" 같은 우리말을 넣으면
     * Univer 가 모르는 값을 받는다 - 지금은 무시하고 넘어가지만 언제 달라질지
     * 모르는 곳에 기대는 셈이다(2026-08-05 브라우저 실측에서 발견).
     */
    private static final String ANCHOR_BOTH = "1";

    private final ExcelImportService importService;
    private final ExcelExportService exportService;
    private final WorkbookService workbookService;
    private final SheetService sheetService;
    private final SheetMapper sheetMapper;
    private final MergeMapper mergeMapper;
    private final SheetSizeService sheetSizeService;
    private final ImageService imageService;
    private final DrawingService drawingService;
    private final weboffice.workbook.WorkbookResourceMapper resourceMapper;

    /** 시트 기본 크기를 담는 이름표. 표를 새로 파지 않고 문서 리소스에 둔다. */
    private static final String SHEET_DEFAULTS = "WO_SHEET_DEFAULTS";

    public ExcelIoService(ExcelImportService importService, ExcelExportService exportService,
                          WorkbookService workbookService, SheetService sheetService,
                          SheetMapper sheetMapper, MergeMapper mergeMapper,
                          ImageService imageService, DrawingService drawingService,
                          SheetSizeService sheetSizeService,
                          weboffice.workbook.WorkbookResourceMapper resourceMapper) {
        this.importService = importService;
        this.exportService = exportService;
        this.workbookService = workbookService;
        this.sheetService = sheetService;
        this.sheetMapper = sheetMapper;
        this.mergeMapper = mergeMapper;
        this.imageService = imageService;
        this.drawingService = drawingService;
        this.sheetSizeService = sheetSizeService;
        this.resourceMapper = resourceMapper;
    }

    /**
     * 파일 이름에서 문서 제목을 뽑는다. 확장자는 뗀다.
     *
     * <p>제목은 사람이 읽는 이름이다. 파일 이름을 그대로 쓰면 "영업.xlsx" 가 제목이
     * 되고, 내려받을 때 다시 확장자가 붙어 "영업.xlsx.xlsx" 가 된다(2026-08-10 지적).
     *
     * <p>마지막 점만 본다. "2026.7 영업.xlsx" 는 "2026.7 영업" 이 돼야 한다. 점으로
     * 시작하는 이름(".xlsx")은 확장자가 아니라 이름이므로 그대로 둔다.
     */
    static String titleOf(String fileName) {
        if (fileName == null) {
            return null;
        }
        int dot = fileName.lastIndexOf('.');
        return dot <= 0 ? fileName : fileName.substring(0, dot);
    }

    @Transactional
    public Long importToDatabase(InputStream in, String fileName, AuditUser user)
            throws IOException {
        WorkbookDataDto data = importService.importWorkbook(in, fileName);
        Long workbookSeq = workbookService.createWorkbook(titleOf(data.workbookName()), user);

        // 시트마다의 기본 행 높이. 손댄 줄만 담는 대신 이것을 알려 준다 - 없으면
        // 손대지 않은 줄이 우리 기본(20px)으로 그려져 원본보다 짧아진다.
        java.util.Map<String, Integer> defaults = new java.util.LinkedHashMap<>();

        int sheetNo = 0;
        for (SheetDto sheetDto : data.sheets()) {
            SheetSummaryDto sheet =
                    new SheetSummaryDto(null, workbookSeq, sheetDto.sheetName(), sheetNo++, 0, 0);
            sheetMapper.insertSheet(sheet, user.userKey());

            sheetService.saveImported(sheet.sheetSeq(), sheetDto.cells(), user);
            if (!sheetDto.mergeRanges().isEmpty()) {
                mergeMapper.insertMerges(sheet.sheetSeq(), sheetDto.mergeRanges(), user.userKey());
            }
            if (!sheetDto.sizes().isEmpty()) {
                sheetSizeService.saveDelta(sheet.sheetSeq(), sheetDto.sizes(), List.of(), user);
            }
            savePictures(workbookSeq, sheet.sheetSeq(), sheetDto.pictures(), user);
            // 얼려 둔 자리도 함께 담는다. 흘리면 머리줄 고정이 사라진다.
            if (sheetDto.freezeRowNo() > 0 || sheetDto.freezeColumnNo() > 0) {
                sheetMapper.updateFreeze(sheet.sheetSeq(), sheetDto.freezeRowNo(),
                        sheetDto.freezeColumnNo(), user.userKey());
            }
            // 걸러 보기(필터)를 건 자리도 담는다. 흘리면 내려받은 파일에서 사라진다.
            if (sheetDto.filterRange() != null && !sheetDto.filterRange().isBlank()) {
                sheetMapper.updateFilterRange(sheet.sheetSeq(), sheetDto.filterRange(),
                        user.userKey());
            }
            defaults.put(String.valueOf(sheet.sheetSeq()), sheetDto.defaultRowHeightPx());
        }
        saveSheetDefaults(workbookSeq, defaults, user);
        return workbookSeq;
    }

    /** 시트 기본 크기를 문서 리소스에 담는다. 표를 새로 파지 않는다. */
    private void saveSheetDefaults(Long workbookSeq, java.util.Map<String, Integer> defaults,
                                   AuditUser user) {
        if (defaults.isEmpty()) {
            return;
        }
        resourceMapper.deleteResource(workbookSeq, SHEET_DEFAULTS);
        resourceMapper.insertResource(workbookSeq, SHEET_DEFAULTS,
                tools.jackson.databind.json.JsonMapper.builder().build()
                        .writeValueAsString(defaults), user.userKey());
    }

    /**
     * xlsx 에서 읽은 그림을 DB 에 넣는다.
     *
     * <p>drawingId 를 서버가 만든다. Univer 가 만든 식별자가 없기 때문이다.
     * 시트 안에서만 유일하면 되므로 순번으로 충분하다 - UUID 를 쓰면 같은 파일을
     * 두 번 올렸을 때 서로 다른 값이 되는데 그게 도움이 되는 곳이 없다.
     */
    private void savePictures(Long workbookSeq, Long sheetSeq,
                              List<PictureDto> pictures, AuditUser user) {
        if (pictures.isEmpty()) {
            return;
        }
        List<DrawingDto> drawings = new ArrayList<>();
        for (PictureDto picture : pictures) {
            Long imageSeq = imageService.store(workbookSeq, picture.bytes(), user);
            drawings.add(new DrawingDto(
                    "xlsx-" + picture.zOrder(), imageSeq,
                    picture.fromRowNo(), picture.fromRowOffset(),
                    picture.fromColumnNo(), picture.fromColumnOffset(),
                    picture.toRowNo(), picture.toRowOffset(),
                    picture.toColumnNo(), picture.toColumnOffset(),
                    ANCHOR_BOTH, picture.zOrder(), null));
        }
        drawingService.saveDelta(sheetSeq, drawings, List.of(), user);
    }

    /** DB 의 그림 배치와 바이트를 합쳐 export 용 형태로 만든다. */
    private List<PictureDto> loadPictures(Long sheetSeq) {
        List<PictureDto> pictures = new ArrayList<>();
        for (DrawingDto drawing : drawingService.readDrawings(sheetSeq)) {
            pictures.add(new PictureDto(
                    imageService.load(drawing.imageSeq()).imageData(),
                    drawing.fromRowNo(), drawing.fromRowOffset(),
                    drawing.fromColumnNo(), drawing.fromColumnOffset(),
                    drawing.toRowNo(), drawing.toRowOffset(),
                    drawing.toColumnNo(), drawing.toColumnOffset(),
                    drawing.zOrder()));
        }
        return pictures;
    }

    public byte[] exportFromDatabase(Long workbookSeq) throws IOException {
        List<SheetDto> sheets = new ArrayList<>();
        for (SheetSummaryDto sheet : sheetMapper.selectSheetsByWorkbook(workbookSeq)) {
            CellRange whole = new CellRange(0, 0,
                    Math.max(sheet.maxRowNo(), 0), Math.max(sheet.maxColumnNo(), 0));
            sheets.add(new SheetDto(sheet.sheetName(),
                    sheetService.readCells(sheet.sheetSeq(), whole),
                    mergeMapper.selectMerges(sheet.sheetSeq()),
                    loadPictures(sheet.sheetSeq()),
                    sheetSizeService.read(sheet.sheetSeq()),
                    sheet.freezeRowNo(), sheet.freezeColumnNo(), sheet.filterRange()));
        }
        return exportService.export(new WorkbookDataDto(
                workbookService.getWorkbook(workbookSeq).workbookName(), sheets));
    }
}
