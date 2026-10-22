package weboffice.workbook;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import weboffice.security.UserContext;
import weboffice.sheet.SheetMapper;
import weboffice.sheet.SheetSummaryDto;
import weboffice.sheet.SheetVersion;
import weboffice.sheet.VersionChangeDto;
import weboffice.sheet.VersionService;

/** 문서를 다시 열 때 시트 목록을 준다. 화면이 workbookSeq 만 들고 들어오기 때문이다. */
@RestController
@RequestMapping("/api/workbooks")
public class WorkbookController {

    private final SheetMapper sheetMapper;
    private final WorkbookService workbookService;
    private final WorkbookPermissionService permissionService;
    private final VersionService versionService;
    private final weboffice.snapshot.SnapshotService snapshotService;
    private final TrashPurgeService trashPurgeService;

    public WorkbookController(SheetMapper sheetMapper, WorkbookService workbookService,
                              WorkbookPermissionService permissionService,
                              VersionService versionService,
                              weboffice.snapshot.SnapshotService snapshotService,
                              TrashPurgeService trashPurgeService) {
        this.sheetMapper = sheetMapper;
        this.workbookService = workbookService;
        this.permissionService = permissionService;
        this.versionService = versionService;
        this.snapshotService = snapshotService;
        this.trashPurgeService = trashPurgeService;
    }

    /** 내 문서와 공유받은 문서. 주소를 잃어도 여기서 다시 찾는다. */
    @GetMapping
    public Map<String, List<WorkbookSummaryDto>> list() {
        return workbookService.listFor(UserContext.current().userKey());
    }

    /**
     * 이름이나 내용으로 문서를 찾는다.
     *
     * <p>목록과 같은 모양으로 준다. 찾는 말이 비면 빈 목록이다 - 온 문서를 훑지 않는다.
     */
    @GetMapping("/search")
    public Map<String, List<WorkbookHitDto>> search(@RequestParam("q") String q) {
        return workbookService.search(UserContext.current().userKey(), q);
    }

    /**
     * 버전 기록.
     *
     * <p>따로 저장한 판이 아니라 편집 기록을 덩어리로 묶은 것이다. 구글도 같다.
     */
    @GetMapping("/{workbookSeq}/versions")
    public List<SheetVersion> versions(@PathVariable Long workbookSeq) {
        permissionService.requireReadWorkbook(workbookSeq);
        return versionService.listVersions(workbookSeq);
    }

    /** 그 버전에서 무엇이 바뀌었는가. 목록을 펼칠 때 부른다. */
    @GetMapping("/{workbookSeq}/versions/{versionId}/changes")
    public List<VersionChangeDto> versionChanges(@PathVariable Long workbookSeq,
                                                 @PathVariable long versionId) {
        permissionService.requireReadWorkbook(workbookSeq);
        return versionService.changesIn(workbookSeq, versionId);
    }

    /**
     * 그 버전으로 되돌리기.
     *
     * <p>덮어쓰기가 아니라 새 편집이다 - 되돌린 것도 기록에 남아 다시 앞으로 갈 수
     * 있다. 볼 수만 있는 사람은 못 한다.
     */
    @PostMapping("/{workbookSeq}/versions/{versionId}/restore")
    public Map<String, Integer> restoreVersion(@PathVariable Long workbookSeq,
                                               @PathVariable long versionId) {
        permissionService.requireWriteWorkbook(workbookSeq);
        // 되돌리기 직전 모습을 복구 지점으로 남긴다. 버전 되돌리기는 셀과 문단만
        // 되돌려 시트·그림 같은 얼개는 못 되돌리는데, 그 사실을 누른 뒤에 알게 되면
        // 손쓸 것이 없다(2026-08-31 설계).
        snapshotService.take(workbookSeq, "BEFORE_RESTORE", null,
                UserContext.currentAuditUser());
        return Map.of("restoredCount", versionService.restoreTo(
                workbookSeq, versionId, UserContext.currentAuditUser()));
    }

    /**
     * 휴지통.
     *
     * <p>{@code /{workbookSeq}} 보다 먼저 적는다 - 스프링은 고정된 길을 먼저 보지만
     * 읽는 사람이 헷갈리지 않게 자리도 앞에 둔다.
     */
    @GetMapping("/trash")
    public List<TrashRow> trash() {
        return workbookService.listTrash(UserContext.current().userKey());
    }

    /**
     * 휴지통 비우기.
     *
     * <p>되돌릴 수 없다. 그래서 "무엇을 남길지" 를 날 수로 받는다 - 전부(0),
     * 오늘 것 빼고(1), 사흘 지난 것(3), 이레 지난 것(7).
     *
     * @return 지운 문서 수
     */
    // 길을 /trash/purge 로 둔다. /trash 로 두면 문서 하나 지우기(/{workbookSeq})와
    // 겹쳐 "trash" 를 문서 번호로 읽는다(2026-09-03 실측: 400 For input string).
    @DeleteMapping("/trash/purge")
    public Map<String, Integer> emptyTrash(
            @RequestParam(name = "keepDays", defaultValue = "0") int keepDays) {
        // 시작만 알리고 곧바로 돌아온다. 다 지울 때까지 붙잡으면 화면이 멈춘 것으로
        // 보인다 - 휴지통 뒤에는 셀이 수백만 줄 딸려 있다(2026-09-04 지적).
        return Map.of("startedCount",
                trashPurgeService.start(UserContext.currentAuditUser(), keepDays));
    }

    /** 비우기가 어디까지 갔는가. 화면이 이것을 물어보며 기다린다. */
    @GetMapping("/trash/purge")
    public TrashPurgeProgress trashPurgeProgress() {
        return trashPurgeService.progressOf(UserContext.currentAuditUser());
    }

    /** 휴지통에서 되살리기. 소유자만 할 수 있고, 30일이 지났으면 없는 문서다. */
    @PostMapping("/{workbookSeq}/restore")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void restore(@PathVariable Long workbookSeq) {
        if (!workbookService.restore(workbookSeq, UserContext.currentAuditUser())) {
            throw new ForbiddenException("되살릴 수 없는 문서입니다");
        }
    }

    public record CreateRequest(String workbookName, String docType) {
    }

    /** 빈 문서. xlsx 를 올리지 않고 바로 시작하는 길이다. */
    @PostMapping
    public WorkbookService.CreatedWorkbook create(@RequestBody(required = false)
                                                  CreateRequest request) {
        return workbookService.createBlank(
                request == null ? null : request.workbookName(),
                DocType.of(request == null ? null : request.docType()),
                UserContext.currentAuditUser());
    }

    public record RenameRequest(String workbookName) {
    }

    /**
     * 제목 수정.
     *
     * <p>구성원·링크와 달리 <b>쓰기 권한이면 된다</b>. 제목은 문서 내용에 가깝고,
     * 오타를 고치자고 소유자를 부르는 것은 번거롭다(설계 9절).
     */
    @PatchMapping("/{workbookSeq}")
    public Map<String, String> rename(@PathVariable Long workbookSeq,
                                      @RequestBody RenameRequest request) {
        permissionService.requireWriteWorkbook(workbookSeq);
        workbookService.rename(workbookSeq, request.workbookName(),
                UserContext.currentAuditUser());
        return Map.of("workbookName", request.workbookName().trim());
    }

    /**
     * 문서 한 건. 제목과 내 역할을 준다.
     *
     * <p>공개 링크로 들어온 사람은 목록에 이 문서가 없다. 제목을 목록에서만 얻으면
     * 링크로 들어온 화면은 이름 없는 문서를 열게 된다.
     */
    @GetMapping("/{workbookSeq}")
    public WorkbookSummaryDto one(@PathVariable Long workbookSeq) {
        WorkbookRole role = permissionService.requireReadWorkbook(workbookSeq);
        return workbookService.summaryOf(workbookSeq, role);
    }

    /**
     * 문서 지우기. <b>소유자만</b> 한다.
     *
     * <p>되돌릴 수 없는 일이라 편집자에게 열지 않는다. 초대받은 사람이 남의 문서를
     * 없애면 안 된다(설계 9절과 같은 이유).
     */
    @DeleteMapping("/{workbookSeq}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long workbookSeq) {
        permissionService.requireOwner(workbookSeq);
        workbookService.delete(workbookSeq, UserContext.currentAuditUser());
    }

    @GetMapping("/{workbookSeq}/sheets")
    public List<SheetSummaryDto> sheets(@PathVariable Long workbookSeq) {
        permissionService.requireReadWorkbook(workbookSeq);
        return sheetMapper.selectSheetsByWorkbook(workbookSeq);
    }
}
