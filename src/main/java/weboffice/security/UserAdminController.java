package weboffice.security;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

import weboffice.view.AdminGate;

/**
 * 사용자 관리 화면.
 *
 * <p>관리자가 이메일을 적으면 계정을 만들고 <b>1회용 초대 링크</b>를 돌려준다.
 * 그 링크로 사용자가 자기 비밀번호를 직접 정한다 - 관리자는 남의 비밀번호를
 * 정하거나 볼 수 없다.
 *
 * <p>메일로 보내지 않는다. 셀프호스팅에서 메일 서버 설정은 가장 흔한 설치
 * 실패 지점이라, 링크를 화면에 띄우고 관리자가 전달하게 한다.
 */
@Controller
public class UserAdminController {

    private final AdminGate adminGate;
    private final UserAccountService accounts;
    private final AuthConfig authConfig;

    public UserAdminController(AdminGate adminGate, UserAccountService accounts,
                               AuthConfig authConfig) {
        this.adminGate = adminGate;
        this.accounts = accounts;
        this.authConfig = authConfig;
    }

    public record RegisterRequest(String email, String displayName, String role) {
    }

    /** 화면에 내보내는 사용자. 비밀번호 해시는 나가지 않는다. */
    public record UserView(String userKey, String email, String displayName, String role,
                           boolean canSignIn, boolean disabled) {

        static UserView of(UserAccountRow row) {
            return new UserView(row.userKey(), row.email(), row.displayName(), row.roleCode(),
                    row.canSignIn(), row.disabledAt() != null);
        }
    }

    /** 발급 결과. 평문 토큰이 나가는 자리는 여기 하나뿐이다. */
    public record InviteView(String email, String inviteUrl, LocalDateTime expiresAt) {
    }

    @GetMapping("/admin/users")
    public String page(HttpServletRequest request) {
        String userId = adminGate.requireAdmin();
        adminGate.logAccess(request, userId, "사용자 관리");
        return "forward:/admin-users.html";
    }

    @GetMapping("/api/admin/users")
    @ResponseBody
    public List<UserView> list() {
        adminGate.requireAdmin();
        return accounts.list().stream().map(UserView::of).toList();
    }

    @PostMapping("/api/admin/users")
    @ResponseBody
    public InviteView register(@RequestBody RegisterRequest body, HttpServletRequest request) {
        String actor = adminGate.requireAdmin();
        requireRegisteredMode();
        UserAccountService.Invited invited =
                accounts.register(body.email(), body.displayName(), body.role(), actor);
        adminGate.logAccess(request, actor, "사용자 등록: " + invited.email());
        return viewOf(invited, request);
    }

    @PostMapping("/api/admin/users/{userKey}/invite")
    @ResponseBody
    public InviteView reinvite(@PathVariable String userKey, HttpServletRequest request) {
        String actor = adminGate.requireAdmin();
        requireRegisteredMode();
        UserAccountService.Invited invited = accounts.invite(userKey, actor);
        adminGate.logAccess(request, actor, "초대 재발급: " + invited.email());
        return viewOf(invited, request);
    }

    @PostMapping("/api/admin/users/{userKey}/disabled")
    @ResponseBody
    public Map<String, Object> setDisabled(@PathVariable String userKey,
                                           @RequestBody Map<String, Boolean> body,
                                           HttpServletRequest request) {
        String actor = adminGate.requireAdmin();
        boolean disabled = Boolean.TRUE.equals(body.get("disabled"));
        accounts.setDisabled(userKey, disabled, actor);
        adminGate.logAccess(request, actor, (disabled ? "사용자 막기: " : "사용자 풀기: ") + userKey);
        return Map.of("disabled", disabled);
    }

    /**
     * 로그인 없는 모드에서는 등록이 뜻이 없다. 만들어 두면 모드를 바꿀 때
     * 쓸 수 있지만, 지금 등록해도 아무도 그 계정으로 들어오지 못한다 - 그
     * 사실을 말해 주는 것이 조용히 만들어 두는 것보다 낫다.
     */
    private void requireRegisteredMode() {
        if (!authConfig.isRegistered()) {
            throw new IllegalArgumentException(
                    "이 서버는 로그인 없이 쓰는 모드입니다."
                    + " 사용자를 등록하려면 weboffice.auth.mode 를 registered 로 두세요.");
        }
    }

    /**
     * 초대 링크를 지금 요청이 온 주소로 만든다.
     *
     * <p>설정에 바깥 주소를 적게 하지 않는 이유: 적어 두면 틀린 채로 남고,
     * 관리자가 보고 있는 그 주소가 사용자도 닿을 수 있는 주소일 가능성이
     * 가장 높다.
     */
    private static InviteView viewOf(UserAccountService.Invited invited,
                                     HttpServletRequest request) {
        String base = request.getScheme() + "://" + request.getHeader("Host");
        return new InviteView(invited.email(),
                base + "/invite?token=" + invited.token(), invited.expiresAt());
    }
}
