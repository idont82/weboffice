package weboffice.sheet;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import weboffice.realtime.RealtimeHub;
import weboffice.security.UserContext;
import weboffice.workbook.WorkbookPermissionService;

/**
 * 시트를 더하고 이름을 바꾸고 지운다.
 *
 * <p>경로가 둘로 갈린다. 더하기는 문서에 거는 일이라 {@code /api/workbooks/{seq}/sheets}
 * 이고, 이름·지우기는 시트 자신이라 {@code /api/sheets/{seq}} 다. 셀은
 * {@code /api/sheets/{seq}/cells} 로 이미 그 아래에 있다.
 */
@RestController
public class SheetLifecycleController {

    public record AddRequest(String sheetName) {
    }

    /**
     * 시트 자신을 고치는 요청. 보낸 항목만 바뀐다.
     *
     * <p>둘을 한 엔드포인트에 둔 이유: 둘 다 "시트 한 개의 속성" 이다. 격자선에만
     * 따로 경로를 파면 앞으로 시트 속성이 하나 늘 때마다 경로가 하나씩 는다.
     */
    public record PatchRequest(String sheetName, Boolean showGridlines,
                               Integer freezeRowNo, Integer freezeColumnNo,
                               String tabColor, String filterRange) {
    }

    public record ReorderRequest(List<Long> sheetSeqs) {
    }

    private final SheetLifecycleService lifecycleService;
    private final WorkbookPermissionService permissionService;
    private final SheetMapper sheetMapper;
    private final SheetCopyService copyService;
    private final RealtimeHub realtimeHub;

    public SheetLifecycleController(SheetLifecycleService lifecycleService,
                                    WorkbookPermissionService permissionService,
                                    SheetMapper sheetMapper, RealtimeHub realtimeHub,
                                    SheetCopyService copyService) {
        this.lifecycleService = lifecycleService;
        this.permissionService = permissionService;
        this.sheetMapper = sheetMapper;
        this.realtimeHub = realtimeHub;
        this.copyService = copyService;
    }

    @PostMapping("/api/workbooks/{workbookSeq}/sheets")
    public SheetSummaryDto add(@PathVariable Long workbookSeq,
                               @RequestBody(required = false) AddRequest request) {
        permissionService.requireWriteWorkbook(workbookSeq);
        SheetSummaryDto sheet = lifecycleService.add(workbookSeq,
                request == null ? null : request.sheetName(), UserContext.currentAuditUser());
        announce(workbookSeq);
        return sheet;
    }

    @PutMapping("/api/workbooks/{workbookSeq}/sheets/order")
    public Map<String, Integer> reorder(@PathVariable Long workbookSeq,
                                        @RequestBody ReorderRequest request) {
        permissionService.requireWriteWorkbook(workbookSeq);
        List<Long> sheetSeqs = request.sheetSeqs() == null ? List.of() : request.sheetSeqs();
        lifecycleService.reorder(workbookSeq, sheetSeqs, UserContext.currentAuditUser());
        announce(workbookSeq);
        return Map.of("sheetCount", sheetSeqs.size());
    }

    @PatchMapping("/api/sheets/{sheetSeq}")
    public Map<String, Object> patch(@PathVariable Long sheetSeq,
                                     @RequestBody PatchRequest request) {
        permissionService.requireWrite(sheetSeq);
        boolean freezing = request.freezeRowNo() != null || request.freezeColumnNo() != null;
        if (request.sheetName() == null && request.showGridlines() == null && !freezing
                && request.tabColor() == null && request.filterRange() == null) {
            throw new IllegalArgumentException("바꿀 것을 하나는 보내야 합니다");
        }

        Map<String, Object> changed = new LinkedHashMap<>();
        if (request.sheetName() != null) {
            lifecycleService.rename(sheetSeq, request.sheetName(), UserContext.currentAuditUser());
            changed.put("sheetName", request.sheetName().trim());
        }
        if (request.showGridlines() != null) {
            lifecycleService.setGridlines(sheetSeq, request.showGridlines(),
                    UserContext.currentAuditUser());
            changed.put("showGridlines", request.showGridlines());
        }
        if (request.tabColor() != null) {
            // 빈 값은 "색 지우기" 다. null 은 "안 보냈다" 라 지우기에 못 쓴다.
            lifecycleService.setTabColor(sheetSeq, request.tabColor(),
                    UserContext.currentAuditUser());
            changed.put("tabColor", request.tabColor());
        }
        if (request.filterRange() != null) {
            // 빈 값은 "걸러 보기 풀기" 다. null 은 "안 보냈다" 라 푸는 데 못 쓴다.
            lifecycleService.setFilterRange(sheetSeq, request.filterRange(),
                    UserContext.currentAuditUser());
            changed.put("filterRange", request.filterRange());
        }
        if (freezing) {
            // 둘 중 하나만 와도 나머지는 지금 값을 지킨다. 한쪽만 바꾸려다 다른 쪽이
            // 0 으로 풀리면 사용자는 왜 풀렸는지 모른다.
            SheetSummaryDto sheet = sheetMapper.selectSheet(sheetSeq);
            int rowNo = request.freezeRowNo() != null ? request.freezeRowNo() : sheet.freezeRowNo();
            int columnNo = request.freezeColumnNo() != null
                    ? request.freezeColumnNo() : sheet.freezeColumnNo();
            lifecycleService.setFreeze(sheetSeq, rowNo, columnNo, UserContext.currentAuditUser());
            changed.put("freezeRowNo", rowNo);
            changed.put("freezeColumnNo", columnNo);
        }
        announce(workbookSeqOf(sheetSeq));
        return changed;
    }

    /**
     * 시트 복사.
     *
     * <p>값만이 아니라 서식·병합·크기·그림·틀고정까지 함께 옮긴다. 표를 그대로
     * 두고 숫자만 바꿔 보려고 복사하기 때문이다(2026-09-01 지적: AI 에게 복사를
     * 시켰더니 빈 시트를 만들었다).
     */
    @PostMapping("/api/sheets/{sheetSeq}/copy")
    public SheetSummaryDto copy(@PathVariable Long sheetSeq,
                                @RequestBody(required = false) AddRequest request) {
        permissionService.requireWrite(sheetSeq);
        Long workbookSeq = workbookSeqOf(sheetSeq);
        SheetSummaryDto made = copyService.copy(sheetSeq,
                request == null ? null : request.sheetName(), UserContext.currentAuditUser());
        announce(workbookSeq);
        return made;
    }

    @DeleteMapping("/api/sheets/{sheetSeq}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable Long sheetSeq) {
        permissionService.requireWrite(sheetSeq);
        // 지우기 전에 읽는다. 지운 뒤에는 selectSheet 가 null 이라 어느 문서였는지 모른다.
        Long workbookSeq = workbookSeqOf(sheetSeq);
        lifecycleService.remove(sheetSeq, UserContext.currentAuditUser());
        // 지운 시트를 보고 있던 창에도 알려야 한다. 그 창은 그 토픽을 구독하고 있다.
        announce(workbookSeq, sheetSeq);
    }

    private Long workbookSeqOf(Long sheetSeq) {
        return sheetMapper.selectSheet(sheetSeq).workbookSeq();
    }

    /** 그 문서의 시트 전부에 알린다. 어느 시트를 보고 있든 탭 줄은 같이 바뀐다. */
    private void announce(Long workbookSeq, Long... alsoNotify) {
        List<Long> targets = new ArrayList<>(
                sheetMapper.selectSheetsByWorkbook(workbookSeq).stream()
                        .map(SheetSummaryDto::sheetSeq).toList());
        targets.addAll(List.of(alsoNotify));
        realtimeHub.sheetsChanged(targets, UserContext.currentActor());
    }
}
