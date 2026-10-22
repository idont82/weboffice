package weboffice.snapshot;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import weboffice.security.UserContext;
import weboffice.workbook.WorkbookPermissionService;

/**
 * 복구 지점.
 *
 * <p>찍기와 제자리 복원은 쓰기 권한이다 - 둘 다 문서를 바꾼다. 새 문서로 복원은
 * 읽기 권한이면 된다. 원본을 안 건드리고 부른 사람 것으로 만들기 때문이다.
 */
@RestController
public class SnapshotController {

    private final SnapshotService snapshotService;
    private final WorkbookPermissionService permissionService;

    public SnapshotController(SnapshotService snapshotService,
                              WorkbookPermissionService permissionService) {
        this.snapshotService = snapshotService;
        this.permissionService = permissionService;
    }

    @GetMapping("/api/workbooks/{workbookSeq}/snapshots")
    public List<SnapshotSummaryDto> list(@PathVariable Long workbookSeq) {
        permissionService.requireReadWorkbook(workbookSeq);
        return snapshotService.list(workbookSeq);
    }

    /**
     * 지금 모습을 찍는다.
     *
     * @param reason 없으면 손으로 찍은 것으로 본다. 서버가 쓰는 이유는 못 부른다
     */
    public record TakeRequest(String label, String reason) {
    }

    @PostMapping("/api/workbooks/{workbookSeq}/snapshots")
    public Map<String, Object> take(@PathVariable Long workbookSeq,
                                    @RequestBody(required = false) TakeRequest request) {
        permissionService.requireWriteWorkbook(workbookSeq);
        Long snapshotSeq = snapshotService.takeAsked(workbookSeq,
                request == null ? null : request.reason(),
                request == null ? null : request.label(),
                UserContext.currentAuditUser());
        return Map.of("snapshotSeq", snapshotSeq);
    }

    @PostMapping("/api/snapshots/{snapshotSeq}/restore")
    public Map<String, Object> restore(@PathVariable Long snapshotSeq) {
        permissionService.requireWriteWorkbook(snapshotService.workbookOf(snapshotSeq));
        snapshotService.restore(snapshotSeq, UserContext.currentAuditUser());
        return Map.of("restored", true);
    }

    @PostMapping("/api/snapshots/{snapshotSeq}/copy")
    public Map<String, Object> copy(@PathVariable Long snapshotSeq) {
        permissionService.requireReadWorkbook(snapshotService.workbookOf(snapshotSeq));
        return Map.of("workbookSeq",
                snapshotService.copyToNewWorkbook(snapshotSeq, UserContext.currentAuditUser()));
    }
}
