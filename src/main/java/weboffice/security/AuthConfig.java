package weboffice.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

/**
 * 인증 방식을 고른다.
 *
 * <pre>
 * weboffice:
 *   auth:
 *     mode: open        # open | registered
 *     admin-email: ''   # registered 의 첫 관리자
 * </pre>
 *
 * <p>{@code open} 이 기본이다. 로그인 화면이 없고 들어오는 사람마다 손님 신원을
 * 받는다. 설치하자마자 쓸 수 있는 것이 이 설정의 목적이다.
 *
 * <p>{@code registered} 는 관리자가 이메일로 등록한 사람만 들어온다.
 */
@Configuration
public class AuthConfig {

    private static final Logger log = LoggerFactory.getLogger(AuthConfig.class);

    static final String OPEN = "open";
    static final String REGISTERED = "registered";

    private final String mode;
    private final String adminEmail;
    private final UserAccountService accounts;

    public AuthConfig(@Value("${weboffice.auth.mode:open}") String mode,
                      @Value("${weboffice.auth.admin-email:}") String adminEmail,
                      UserAccountService accounts) {
        this.mode = requireKnown(mode);
        this.adminEmail = adminEmail == null ? "" : adminEmail.trim();
        this.accounts = accounts;
    }

    public boolean isRegistered() {
        return REGISTERED.equals(mode);
    }

    @Bean
    public Authenticator authenticator() {
        if (isRegistered()) {
            return new PasswordAuthenticator();
        }
        log.warn("로그인 없는 모드다. 들어오는 누구나 문서를 만들고 공유할 수 있다."
                + " 신뢰된 망에서만 쓸 것.");
        return new GuestAuthenticator();
    }

    @Bean
    public UserDirectory userDirectory(UserAccountMapper mapper) {
        return isRegistered() ? new AccountDirectory(mapper) : new GuestDirectory();
    }

    /**
     * 첫 관리자를 만든다.
     *
     * <p>초대 링크를 <b>기동 로그에 한 번 찍는다</b>. 메일을 보낼 길이 없는
     * 설치에서도 관리자가 들어올 수 있어야 하고, 기동 로그를 볼 수 있는 사람이
     * 곧 서버 주인이므로 이것이 가장 적은 가정이다.
     *
     * <p>이미 그 이메일이 있으면 아무것도 하지 않는다 - 다시 뜰 때마다 초대가
     * 새로 나면 앞서 받은 링크가 매번 죽는다.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void bootstrapAdmin() {
        if (!isRegistered() || adminEmail.isEmpty()) {
            return;
        }
        UserAccountService.Invited invited = accounts.ensureAdmin(adminEmail);
        if (invited == null) {
            log.info("첫 관리자({})는 이미 있다. 초대를 새로 내지 않는다.", adminEmail);
            return;
        }
        log.warn("첫 관리자 초대: {} 로 들어와 비밀번호를 정하라 (유효기간 {})",
                "/invite?token=" + invited.token(), invited.expiresAt());
    }

    private static String requireKnown(String mode) {
        if (OPEN.equals(mode) || REGISTERED.equals(mode)) {
            return mode;
        }
        // 모르는 값을 open 으로 떨어뜨리지 않는다. 잠긴 줄 알았는데 열려 있는
        // 것이 가장 나쁘다.
        throw new IllegalStateException("weboffice.auth.mode 는 open 또는 registered 다: " + mode);
    }
}
