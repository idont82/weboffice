package weboffice.security;

import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * 이메일·비밀번호 로그인과 초대 수락. 등록 모드에서만 쓴다.
 *
 * <p>비밀번호는 POST 본문으로만 받는다. URL 에 실으면 접속 기록·브라우저
 * 이력·중간 서버 로그에 남는다.
 *
 * <p>맞춰 보는 일은 {@link UserAccountService} 가 하고 여기서는 세션을 만든다.
 */
@Controller
public class LoginController {

    private static final Logger log = LoggerFactory.getLogger(LoginController.class);

    private final UserAccountService accounts;
    private final AuthConfig authConfig;

    public LoginController(UserAccountService accounts, AuthConfig authConfig) {
        this.accounts = accounts;
        this.authConfig = authConfig;
    }

    public record LoginRequest(String email, String password) {
    }

    public record AcceptRequest(String token, String password) {
    }

    @GetMapping("/login")
    public String loginPage() {
        return "forward:/login.html";
    }

    @GetMapping("/invite")
    public String invitePage() {
        return "forward:/invite.html";
    }

    /**
     * 로그아웃. 세션을 버린다.
     *
     * <p>로그인 없는 모드에서도 부를 수는 있다. 그때는 손님 쿠키가 그대로 있어
     * 같은 사람으로 다시 들어온다 - 쿠키까지 지우면 앞서 만든 문서를 제 것으로
     * 못 보게 되므로 그렇게 하지 않는다.
     */
    @GetMapping("/logout")
    public String logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return "redirect:/login";
    }

    @PostMapping("/api/login")
    @ResponseBody
    public ResponseEntity<?> login(@RequestBody LoginRequest body, HttpServletRequest request) {
        if (!authConfig.isRegistered()) {
            // 로그인 없는 모드에서 이 길을 열어 두면 "되는 것 같은데 안 되는"
            // 상태가 된다. 왜 없는지 말해 준다.
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "message", "이 서버는 로그인 없이 쓰는 모드입니다"));
        }
        UserAccountRow found = accounts.signIn(body.email(), body.password());
        if (found == null) {
            // 어느 쪽이 틀렸는지 말하지 않는다 - 말해 주면 어느 주소가 등록돼
            // 있는지 하나씩 물어 알아낼 수 있다.
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "message", "이메일 또는 비밀번호가 맞지 않습니다"));
        }
        LoginUser user = userOf(found);
        UserContext.set(newSession(request), user);
        log.info("로그인: {}", found.email());
        return ResponseEntity.ok(CurrentUserDto.of(user));
    }

    /**
     * 초대를 써서 비밀번호를 정하고 그대로 들어온다.
     *
     * <p>정한 뒤에 다시 로그인하게 하지 않는다. 방금 비밀번호를 정한 사람에게
     * 그것을 한 번 더 치게 할 이유가 없다.
     */
    @PostMapping("/api/invite/accept")
    @ResponseBody
    public ResponseEntity<?> accept(@RequestBody AcceptRequest body, HttpServletRequest request) {
        UserAccountRow found = accounts.acceptInvite(body.token(), body.password());
        LoginUser user = userOf(found);
        UserContext.set(newSession(request), user);
        log.info("초대 수락: {}", found.email());
        return ResponseEntity.ok(CurrentUserDto.of(user));
    }

    /**
     * 세션을 버리고 새로 연다.
     *
     * <p>앞 신원의 공개 링크 허가가 남으면 "공유를 해제해도 이미 열려 있던
     * 세션이 막히는가" 가 거짓으로 통과한다. 로그인은 신원 교체이지 권한
     * 누적이 아니다.
     */
    private static HttpSession newSession(HttpServletRequest request) {
        HttpSession previous = request.getSession(false);
        if (previous != null) {
            previous.invalidate();
        }
        return request.getSession(true);
    }

    private static LoginUser userOf(UserAccountRow row) {
        return new LoginUser(row.email(), row.userKey(), row.displayName(),
                row.isAdmin() ? "관리자" : null, null, null, "ko");
    }
}
