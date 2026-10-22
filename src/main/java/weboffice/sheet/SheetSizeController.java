package weboffice.sheet;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import weboffice.security.UserContext;
import weboffice.workbook.WorkbookPermissionService;

/** 행 높이·열 너비. 셀과 같은 델타 구조다. */
@RestController
@RequestMapping("/api/sheets/{sheetSeq}/sizes")
public class SheetSizeController {

    public record SaveRequest(List<SheetSizeDto> changed, List<SheetSizeDto> deleted) {
    }

    private final SheetSizeService sheetSizeService;
    private final WorkbookPermissionService permissionService;

    public SheetSizeController(SheetSizeService sheetSizeService,
                               WorkbookPermissionService permissionService) {
        this.sheetSizeService = sheetSizeService;
        this.permissionService = permissionService;
    }

    @GetMapping
    public List<SheetSizeDto> read(@PathVariable Long sheetSeq) {
        permissionService.requireRead(sheetSeq);
        return sheetSizeService.read(sheetSeq);
    }

    @PutMapping
    public Map<String, Integer> saveDelta(@PathVariable Long sheetSeq,
                                          @RequestBody SaveRequest request) {
        permissionService.requireWrite(sheetSeq);
        int saved = sheetSizeService.saveDelta(sheetSeq,
                request.changed() == null ? List.of() : request.changed(),
                request.deleted() == null ? List.of() : request.deleted(),
                UserContext.currentAuditUser());
        return Map.of("savedCount", saved);
    }
}
