package weboffice.workbook;

import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;

import weboffice.security.UserContext;

/**
 * 공개 링크 REST 와 교환 경로.
 *
 * <p>발급·해제는 소유자만 한다. 편집자가 문서를 공개할 수 있으면 소유자가 통제를
 * 잃는다(설계 9절).
 */
@Controller
public class ShareLinkController {

    private final ShareTokenService shareTokenService;
    private final WorkbookPermissionService permissionService;

    public ShareLinkController(ShareTokenService shareTokenService,
                               WorkbookPermissionService permissionService) {
        this.shareTokenService = shareTokenService;
        this.permissionService = permissionService;
    }

    @PostMapping("/api/workbooks/{workbookSeq}/share-link")
    @ResponseBody
    public Map<String, String> issue(@PathVariable Long workbookSeq) {
        permissionService.requireOwner(workbookSeq);
        String token = shareTokenService.issue(workbookSeq, UserContext.currentAuditUser());
        return Map.of("url", "/s/" + token);
    }

    @DeleteMapping("/api/workbooks/{workbookSeq}/share-link")
    @ResponseBody
    public Map<String, String> revoke(@PathVariable Long workbookSeq) {
        permissionService.requireOwner(workbookSeq);
        shareTokenService.revoke(workbookSeq, UserContext.currentAuditUser());
        return Map.of("message", "공개를 해제했습니다");
    }

    /**
     * 링크 교환. 인증을 건너뛰지 않는다 - 로그인하지 않았으면 인터셉터가
     * /login?returnTo=/s/... 로 보내고, 로그인 뒤 여기로 돌아온다.
     *
     * <p>교환은 한 번이다. 토큰을 매 API 요청에 싣지 않아 주소창·로그·리퍼러에
     * 반복해 남지 않는다.
     */
    @GetMapping("/s/{token}")
    public String redeem(@PathVariable String token, HttpServletRequest request) {
        Long workbookSeq = shareTokenService.redeem(token);
        if (workbookSeq == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "없는 링크입니다");
        }
        UserContext.grantShare(request.getSession(), workbookSeq, token);
        return "redirect:/?workbookSeq=" + workbookSeq;
    }
}
