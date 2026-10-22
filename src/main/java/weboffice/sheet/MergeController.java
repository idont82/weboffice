package weboffice.sheet;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import weboffice.excel.MergeRangeDto;
import weboffice.realtime.RealtimeHub;
import weboffice.security.UserContext;
import weboffice.workbook.WorkbookPermissionService;

/**
 * 병합 범위.
 *
 * <p>표와 매퍼는 처음부터 있었는데 xlsx 입출력에만 이어져 있었다. 이 경로가 없어
 * 엑셀에서 병합된 파일을 올려도 화면에 안 나오고, 화면에서 병합해도 새로고침하면
 * 풀렸다(2026-08-07 실측).
 */
@RestController
@RequestMapping("/api/sheets/{sheetSeq}/merges")
public class MergeController {

    public record SaveRequest(List<MergeRangeDto> merges) {
    }

    private final MergeService mergeService;
    private final WorkbookPermissionService permissionService;
    private final RealtimeHub realtimeHub;

    public MergeController(MergeService mergeService,
                           WorkbookPermissionService permissionService,
                           RealtimeHub realtimeHub) {
        this.mergeService = mergeService;
        this.permissionService = permissionService;
        this.realtimeHub = realtimeHub;
    }

    @GetMapping
    public List<MergeRangeDto> read(@PathVariable Long sheetSeq) {
        permissionService.requireRead(sheetSeq);
        return mergeService.read(sheetSeq);
    }

    @PutMapping
    public Map<String, Integer> replaceAll(@PathVariable Long sheetSeq,
                                           @RequestBody SaveRequest request) {
        permissionService.requireWrite(sheetSeq);
        List<MergeRangeDto> merges = request.merges() == null ? List.of() : request.merges();
        int saved = mergeService.replaceAll(sheetSeq, merges, UserContext.currentAuditUser());
        realtimeHub.mergesChanged(sheetSeq, UserContext.currentActor(), merges);
        return Map.of("savedCount", saved);
    }
}
