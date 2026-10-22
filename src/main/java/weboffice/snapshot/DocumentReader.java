package weboffice.snapshot;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import weboffice.doc.DocParagraphMapper;
import weboffice.drawing.DrawingService;
import weboffice.sheet.CellRange;
import weboffice.sheet.MergeService;
import weboffice.sheet.SheetMapper;
import weboffice.sheet.SheetService;
import weboffice.sheet.SheetSizeService;
import weboffice.sheet.SheetSummaryDto;
import weboffice.workbook.WorkbookDto;
import weboffice.workbook.WorkbookMapper;
import weboffice.workbook.WorkbookResourceMapper;

/**
 * 문서를 통째로 읽어 스냅샷 본문으로 만든다.
 *
 * <p>내려받기(xlsx)가 하는 일과 닮았지만 다른 길이다. 내려받기는 엑셀이 아는
 * 것만 담고 이미지 바이트를 실어 나른다 - 여기는 <b>우리 표에 있는 그대로</b>
 * 담아야 되돌렸을 때 원래대로 선다.
 */
@Service
public class DocumentReader {

    private final WorkbookMapper workbookMapper;
    private final SheetMapper sheetMapper;
    private final SheetService sheetService;
    private final MergeService mergeService;
    private final SheetSizeService sheetSizeService;
    private final DrawingService drawingService;
    private final DocParagraphMapper docParagraphMapper;
    private final WorkbookResourceMapper resourceMapper;

    public DocumentReader(WorkbookMapper workbookMapper, SheetMapper sheetMapper,
                          SheetService sheetService, MergeService mergeService,
                          SheetSizeService sheetSizeService, DrawingService drawingService,
                          DocParagraphMapper docParagraphMapper,
                          WorkbookResourceMapper resourceMapper) {
        this.workbookMapper = workbookMapper;
        this.sheetMapper = sheetMapper;
        this.sheetService = sheetService;
        this.mergeService = mergeService;
        this.sheetSizeService = sheetSizeService;
        this.drawingService = drawingService;
        this.docParagraphMapper = docParagraphMapper;
        this.resourceMapper = resourceMapper;
    }

    public SnapshotBody read(Long workbookSeq) {
        WorkbookDto workbook = workbookMapper.selectWorkbook(workbookSeq);
        if (workbook == null) {
            throw new IllegalArgumentException("없는 문서입니다: " + workbookSeq);
        }

        List<SnapshotBody.SheetPart> sheets = new ArrayList<>();
        for (SheetSummaryDto sheet : sheetMapper.selectSheetsByWorkbook(workbookSeq)) {
            sheets.add(partOf(sheet));
        }

        return new SnapshotBody(workbook.workbookName(), workbook.docType(), sheets,
                docParagraphMapper.selectParagraphs(workbookSeq),
                resourceMapper.selectResources(workbookSeq));
    }

    private SnapshotBody.SheetPart partOf(SheetSummaryDto sheet) {
        Long sheetSeq = sheet.sheetSeq();
        // 경계까지 통째로 읽는다. 화면이 보는 범위만 읽으면 스크롤 밖의 값이
        // 스냅샷에서 빠지고, 되돌리는 순간 그 자리가 지워진다.
        CellRange all = new CellRange(0, 0, sheet.maxRowNo(), sheet.maxColumnNo());
        return new SnapshotBody.SheetPart(
                sheet.sheetName(), sheet.sheetNo(),
                sheet.maxRowNo(), sheet.maxColumnNo(),
                sheet.showGridlines(), sheet.freezeRowNo(), sheet.freezeColumnNo(),
                sheet.tabColor(), sheet.filterRange(),
                sheetService.readCells(sheetSeq, all),
                mergeService.read(sheetSeq),
                sheetSizeService.read(sheetSeq),
                drawingService.readDrawings(sheetSeq));
    }
}
