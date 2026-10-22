package weboffice.workbook;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import weboffice.security.UserContext;

/**
 * 구성원 REST.
 *
 * <p>바꾸는 것은 <b>소유자만</b>이다. 편집자가 남을 초대할 수 있으면 소유자가
 * 통제를 잃는다(설계 9절). 보는 것은 읽기 권한이면 된다 - 누가 함께 보는지는
 * 같이 쓰는 사람이 알아야 한다.
 */
@RestController
@RequestMapping("/api/workbooks/{workbookSeq}/members")
public class MemberController {

    public record MemberRequest(String userId, String role) {
    }

    private final MemberService memberService;
    private final WorkbookPermissionService permissionService;

    public MemberController(MemberService memberService,
                            WorkbookPermissionService permissionService) {
        this.memberService = memberService;
        this.permissionService = permissionService;
    }

    @GetMapping
    public List<WorkbookMemberDto> list(@PathVariable Long workbookSeq) {
        permissionService.requireReadWorkbook(workbookSeq);
        return memberService.list(workbookSeq);
    }

    /** 추가와 역할 변경을 겸한다. 이미 있는 사람을 다시 넣는 것은 오류가 아니다. */
    @PutMapping
    public Map<String, String> put(@PathVariable Long workbookSeq,
                                   @RequestBody MemberRequest request) {
        permissionService.requireOwner(workbookSeq);
        memberService.put(workbookSeq, request.userId(), request.role(),
                UserContext.currentAuditUser());
        return Map.of("userId", request.userId(), "role", request.role());
    }

    @DeleteMapping("/{userId}")
    public Map<String, String> remove(@PathVariable Long workbookSeq,
                                      @PathVariable String userId) {
        permissionService.requireOwner(workbookSeq);
        memberService.remove(workbookSeq, userId, UserContext.currentAuditUser());
        return Map.of("userId", userId);
    }
}
