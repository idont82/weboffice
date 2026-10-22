package weboffice.snapshot;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import weboffice.common.AuditUser;
import weboffice.doc.DocParagraphDto;
import weboffice.doc.DocParagraphMapper;
import weboffice.doc.DocParagraphService;
import weboffice.drawing.DrawingDto;
import weboffice.drawing.DrawingService;
import weboffice.excel.CellDto;
import weboffice.sheet.CellRange;
import weboffice.sheet.MergeService;
import weboffice.sheet.SheetMapper;
import weboffice.sheet.SheetService;
import weboffice.sheet.SheetSizeDto;
import weboffice.sheet.SheetSizeService;
import weboffice.sheet.SheetSummaryDto;
import weboffice.workbook.WorkbookMapper;
import weboffice.workbook.WorkbookResourceDto;
import weboffice.workbook.WorkbookResourceMapper;

/**
 * 문서를 스냅샷 본문의 모습으로 만든다.
 *
 * <p>덮어쓰기가 아니라 <b>맞추기</b>다. 조각마다 "지금 있는데 스냅샷에 없는 것"
 * 을 지우고 스냅샷 것을 넣는다. 넣기만 하면 스냅샷 뒤에 생긴 시트·그림·병합이
 * 그대로 남아 되돌린 문서가 아니게 된다.
 *
 * <p>시트는 <b>있는 줄을 다시 쓴다</b>. 지우고 새로 넣으면 시트 번호가 바뀌어
 * 열려 있는 화면과 개인 보기 기록이 남의 시트를 가리킨다. 차례대로 짝지어 앞에서부터
 * 덮고, 남는 시트는 지우고, 모자라면 더한다.
 */
@Service
public class DocumentWriter {

    private final WorkbookMapper workbookMapper;
    private final SheetMapper sheetMapper;
    private final SheetService sheetService;
    private final MergeService mergeService;
    private final SheetSizeService sheetSizeService;
    private final DrawingService drawingService;
    private final DocParagraphService docParagraphService;
    private final DocParagraphMapper docParagraphMapper;
    private final WorkbookResourceMapper resourceMapper;

    public DocumentWriter(WorkbookMapper workbookMapper, SheetMapper sheetMapper,
                          SheetService sheetService, MergeService mergeService,
                          SheetSizeService sheetSizeService, DrawingService drawingService,
                          DocParagraphService docParagraphService,
                          DocParagraphMapper docParagraphMapper,
                          WorkbookResourceMapper resourceMapper) {
        this.workbookMapper = workbookMapper;
        this.sheetMapper = sheetMapper;
        this.sheetService = sheetService;
        this.mergeService = mergeService;
        this.sheetSizeService = sheetSizeService;
        this.drawingService = drawingService;
        this.docParagraphService = docParagraphService;
        this.docParagraphMapper = docParagraphMapper;
        this.resourceMapper = resourceMapper;
    }

    @Transactional
    public void apply(Long workbookSeq, SnapshotBody body, AuditUser user) {
        workbookMapper.updateWorkbookName(workbookSeq, body.workbookName(), user.userKey());
        applySheets(workbookSeq, body.sheets(), user);
        applyParagraphs(workbookSeq, body.paragraphs(), user);
        applyResources(workbookSeq, body.resources(), user);
    }

    /**
     * 시트를 맞춘다.
     *
     * <p>순번을 한 번 음수로 비켜 두고 0부터 다시 매긴다. 표의 (문서, 순번) 짝이
     * 하나여야 하는데 <b>지운 줄도 그 짝을 쥐고 있어</b>, 안 비키면 되돌리기가
     * 순번 충돌로 막힌다(2026-08-31 실측). 시트 순서 바꾸기가 쓰는 것과 같은 수다.
     *
     * <p>지운 시트가 있으면 새로 넣지 않고 되살려 쓴다. 시트 번호가 그대로라
     * 열려 있는 화면과 개인 보기 기록이 남의 시트를 가리키지 않는다.
     */
    private void applySheets(Long workbookSeq, List<SnapshotBody.SheetPart> wanted,
                             AuditUser user) {
        String userKey = user.userKey();
        List<SheetSummaryDto> pool = new ArrayList<>(sheetMapper.selectSheetsByWorkbook(workbookSeq));
        for (SheetSummaryDto dead : sheetMapper.selectDeletedSheetsByWorkbook(workbookSeq)) {
            // 순번을 비키려면 먼저 살아 있어야 한다 - 순번 고치기가 지운 줄은 안 건드린다.
            sheetMapper.reviveSheet(dead.sheetSeq(), userKey);
            pool.add(dead);
        }
        for (SheetSummaryDto one : pool) {
            sheetMapper.updateSheetNo(one.sheetSeq(), -Math.toIntExact(one.sheetSeq()), userKey);
        }

        for (int at = 0; at < wanted.size(); at += 1) {
            SnapshotBody.SheetPart part = wanted.get(at);
            SheetSummaryDto sheet = at < pool.size() ? pool.get(at)
                    : addSheet(workbookSeq, part, at, user);
            applySheet(sheet, part, at, user);
        }

        // 스냅샷보다 많은 시트는 지운다. 순번은 음수인 채로 둔다.
        for (int at = wanted.size(); at < pool.size(); at += 1) {
            sheetMapper.softDeleteSheet(pool.get(at).sheetSeq(), userKey);
        }
    }

    private SheetSummaryDto addSheet(Long workbookSeq, SnapshotBody.SheetPart part, int sheetNo,
                                     AuditUser user) {
        SheetSummaryDto sheet = new SheetSummaryDto(null, workbookSeq, part.sheetName(), sheetNo,
                part.maxRowNo(), part.maxColumnNo());
        sheetMapper.insertSheet(sheet, user.userKey());
        return sheet;
    }

    private void applySheet(SheetSummaryDto sheet, SnapshotBody.SheetPart part, int sheetNo,
                            AuditUser user) {
        Long sheetSeq = sheet.sheetSeq();
        String userKey = user.userKey();

        sheetMapper.updateSheetName(sheetSeq, part.sheetName(), userKey);
        sheetMapper.updateSheetNo(sheetSeq, sheetNo, userKey);
        sheetMapper.updateFreeze(sheetSeq, part.freezeRowNo(), part.freezeColumnNo(), userKey);
        sheetMapper.updateTabColor(sheetSeq, part.tabColor(), userKey);
        sheetMapper.updateFilterRange(sheetSeq, part.filterRange(), userKey);
        sheetMapper.updateGridline(sheetSeq, part.showGridlines() ? "Y" : "N", userKey);
        sheetMapper.updateSheetBounds(sheetSeq, part.maxRowNo(), part.maxColumnNo(), userKey);

        applyCells(sheet, part, user);
        mergeService.replaceAll(sheetSeq, part.merges(), user);
        applySizes(sheetSeq, part.sizes(), user);
        applyDrawings(sheetSeq, part.drawings(), user);
    }

    /**
     * 셀을 맞춘다.
     *
     * <p>지금 있는 자리 중 스냅샷에 없는 것을 지운다. 되돌린 뒤에 "그때는 없던 값"
     * 이 남아 있으면 그것은 되돌린 것이 아니다.
     *
     * <p>일반 저장 경로로 보낸다 - 되돌리기도 편집이라 이력에 남아야 하고, 열려
     * 있는 남의 화면에도 알려야 한다.
     */
    private void applyCells(SheetSummaryDto sheet, SnapshotBody.SheetPart part, AuditUser user) {
        int rowBound = Math.max(sheet.maxRowNo(), part.maxRowNo());
        int columnBound = Math.max(sheet.maxColumnNo(), part.maxColumnNo());
        List<CellDto> before = sheetService.readCells(sheet.sheetSeq(),
                new CellRange(0, 0, rowBound, columnBound));

        Set<Long> wantedSpots = new LinkedHashSet<>();
        for (CellDto cell : part.cells()) {
            wantedSpots.add(spotOf(cell.rowNo(), cell.columnNo()));
        }

        List<CellDto> deleted = new ArrayList<>();
        for (CellDto cell : before) {
            if (!wantedSpots.contains(spotOf(cell.rowNo(), cell.columnNo()))) {
                deleted.add(cell);
            }
        }
        sheetService.saveDelta(sheet.sheetSeq(), part.cells(), deleted, user);
    }

    /** 행·열 자리를 하나의 수로 접는다. 열은 만 육천 개를 안 넘는다. */
    private static long spotOf(int rowNo, int columnNo) {
        return ((long) rowNo << 20) | columnNo;
    }

    private void applySizes(Long sheetSeq, List<SheetSizeDto> wanted, AuditUser user) {
        Set<String> wantedKeys = new LinkedHashSet<>();
        for (SheetSizeDto size : wanted) {
            wantedKeys.add(size.axisType() + ":" + size.indexNo());
        }
        List<SheetSizeDto> deleted = sheetSizeService.read(sheetSeq).stream()
                .filter(size -> !wantedKeys.contains(size.axisType() + ":" + size.indexNo()))
                .toList();
        sheetSizeService.saveDelta(sheetSeq, wanted, deleted, user);
    }

    private void applyDrawings(Long sheetSeq, List<DrawingDto> wanted, AuditUser user) {
        Set<String> wantedIds = new LinkedHashSet<>();
        for (DrawingDto drawing : wanted) {
            wantedIds.add(drawing.drawingId());
        }
        List<String> deleted = drawingService.readDrawings(sheetSeq).stream()
                .map(DrawingDto::drawingId)
                .filter(id -> !wantedIds.contains(id))
                .toList();
        drawingService.saveDelta(sheetSeq, wanted, deleted, user);
    }

    private void applyParagraphs(Long workbookSeq, List<DocParagraphDto> wanted, AuditUser user) {
        Set<Integer> wantedNos = new LinkedHashSet<>();
        for (DocParagraphDto paragraph : wanted) {
            wantedNos.add(paragraph.paragraphNo());
        }
        List<Integer> deleted = docParagraphMapper.selectParagraphs(workbookSeq).stream()
                .map(DocParagraphDto::paragraphNo)
                .filter(no -> !wantedNos.contains(no))
                .toList();
        if (wanted.isEmpty() && deleted.isEmpty()) {
            return;
        }
        docParagraphService.save(workbookSeq, wanted, deleted, user);
    }

    /**
     * 문서 자원을 맞춘다.
     *
     * <p>자원은 지우고 다시 넣는 것이 곧 고치는 길이다(컨트롤러와 같은 규칙).
     */
    private void applyResources(Long workbookSeq, List<WorkbookResourceDto> wanted,
                                AuditUser user) {
        Set<String> wantedNames = new LinkedHashSet<>();
        for (WorkbookResourceDto resource : wanted) {
            wantedNames.add(resource.name());
        }
        for (WorkbookResourceDto now : resourceMapper.selectResources(workbookSeq)) {
            if (!wantedNames.contains(now.name())) {
                resourceMapper.deleteResource(workbookSeq, now.name());
            }
        }
        for (WorkbookResourceDto resource : wanted) {
            resourceMapper.deleteResource(workbookSeq, resource.name());
            resourceMapper.insertResource(workbookSeq, resource.name(), resource.data(),
                    user.userKey());
        }
    }
}
