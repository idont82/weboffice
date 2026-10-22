package weboffice.snapshot;

import java.util.List;

import weboffice.doc.DocParagraphDto;
import weboffice.drawing.DrawingDto;
import weboffice.excel.CellDto;
import weboffice.excel.MergeRangeDto;
import weboffice.sheet.SheetSizeDto;
import weboffice.workbook.WorkbookResourceDto;

/**
 * 스냅샷에 담기는 문서 한 벌.
 *
 * <p>문서를 이루는 조각을 <b>전부</b> 담는다. 하나라도 빠지면 "통째로 되돌린다"
 * 가 거짓이 된다 - 지금 버전 기록이 셀과 문단만 남겨 시트를 지운 것은 못 되돌리는
 * 것이 그 예다.
 *
 * <p>시트 번호(sheetSeq)는 안 담는다. 되돌릴 때 그 번호가 남아 있으리라는 보장이
 * 없고, 새 문서로 복원하면 아예 다른 번호가 된다. 차례가 곧 시트를 가리킨다.
 *
 * <p>이미지 바이트도 안 담는다. 이미지 표가 해시로 중복을 지워 이미 한 벌만 갖고
 * 있고, 이미지를 지우는 길이 없어 참조가 끊기지 않는다.
 */
public record SnapshotBody(
        String workbookName,
        String docType,
        List<SheetPart> sheets,
        List<DocParagraphDto> paragraphs,
        List<WorkbookResourceDto> resources
) {

    /** 시트 하나와 그 안에 든 것 전부. */
    public record SheetPart(
            String sheetName,
            int sheetNo,
            int maxRowNo,
            int maxColumnNo,
            boolean showGridlines,
            int freezeRowNo,
            int freezeColumnNo,
            String tabColor,
            String filterRange,
            List<CellDto> cells,
            List<MergeRangeDto> merges,
            List<SheetSizeDto> sizes,
            List<DrawingDto> drawings
    ) {
    }
}
