package weboffice.drawing;

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

@RestController
@RequestMapping("/api/sheets/{sheetSeq}/drawings")
public class DrawingController {

    private final DrawingService drawingService;
    private final WorkbookPermissionService permissionService;

    public DrawingController(DrawingService drawingService,
                             WorkbookPermissionService permissionService) {
        this.drawingService = drawingService;
        this.permissionService = permissionService;
    }

    @GetMapping
    public List<DrawingDto> readDrawings(@PathVariable Long sheetSeq) {
        permissionService.requireRead(sheetSeq);
        return drawingService.readDrawings(sheetSeq);
    }

    @PutMapping
    public Map<String, Integer> saveDelta(@PathVariable Long sheetSeq,
                                          @RequestBody DrawingDeltaRequest request) {
        permissionService.requireWrite(sheetSeq);
        int saved = drawingService.saveDelta(sheetSeq, request.upserted(),
                request.deletedIds(), UserContext.currentAuditUser());
        return Map.of("savedCount", saved);
    }
}
