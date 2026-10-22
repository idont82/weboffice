package weboffice.sheet;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import weboffice.excel.CellDto;
import weboffice.security.UserContext;
import weboffice.workbook.WorkbookPermissionService;

@RestController
@RequestMapping("/api/sheets/{sheetSeq}/cells")
public class SheetController {

    private final SheetService sheetService;
    private final WorkbookPermissionService permissionService;
    private final CellHistoryService cellHistoryService;

    public SheetController(SheetService sheetService,
                           WorkbookPermissionService permissionService,
                           CellHistoryService cellHistoryService) {
        this.sheetService = sheetService;
        this.permissionService = permissionService;
        this.cellHistoryService = cellHistoryService;
    }

    @GetMapping
    public CellPage readCells(@PathVariable Long sheetSeq,
                                   @RequestParam int fromRowNo,
                                   @RequestParam int fromColumnNo,
                                   @RequestParam int toRowNo,
                                   @RequestParam int toColumnNo) {
        permissionService.requireRead(sheetSeq);
        return CellPage.of(sheetService.readCells(sheetSeq,
                new CellRange(fromRowNo, fromColumnNo, toRowNo, toColumnNo)));
    }

    /**
     * 칸 하나의 수정 기록.
     *
     * <p>구글 시트의 "수정 기록 표시" 와 같은 것이다. 되돌리기는 없다 - 그쪽도
     * 칸 하나만 되돌리지는 못한다.
     */
    @GetMapping("/{rowNo}/{columnNo}/history")
    public List<CellHistoryDto> cellHistory(@PathVariable Long sheetSeq,
                                            @PathVariable int rowNo,
                                            @PathVariable int columnNo) {
        permissionService.requireRead(sheetSeq);
        return cellHistoryService.historyOf(sheetSeq, rowNo, columnNo);
    }

    @PatchMapping
    public Map<String, Integer> saveDelta(@PathVariable Long sheetSeq,
                                          @RequestBody CellDeltaRequest request) {
        permissionService.requireWrite(sheetSeq);
        int saved = sheetService.saveDelta(sheetSeq, request.changed(), request.deleted(),
                UserContext.currentAuditUser());
        return Map.of("savedCount", saved);
    }
}
