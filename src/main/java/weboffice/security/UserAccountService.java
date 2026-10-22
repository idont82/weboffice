package weboffice.security;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 등록된 사용자를 들이고 막는다.
 *
 * <p>비밀번호는 <b>사용자가 직접 정한다.</b> 관리자는 이메일만 적고 1회용 초대
 * 링크를 받는다 - 관리자가 남의 비밀번호를 정하거나 볼 수 있으면 그것은 그
 * 사람의 비밀번호가 아니다.
 *
 * <p>평문 비밀번호는 이 클래스 안에서만 지나간다. 로그에 찍지 않고, 어떤
 * 응답에도 담지 않고, 표에는 BCrypt 해시만 적는다.
 */
@Service
public class UserAccountService {

    /**
     * 이메일 모양. 완전한 RFC 검사는 하지 않는다 - 여기서 막아야 하는 것은
     * 오타가 아니라 "이메일이 아닌 것"이다. 진짜 검사는 그 주소로 보내 보는 것이다.
     */
    private static final Pattern EMAIL = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");

    /**
     * 비밀번호 최소 길이. NIST SP 800-63B 가 사람이 정하는 비밀번호에 요구하는
     * 하한과 같다.
     *
     * <p>복잡도 규칙(대문자·숫자·기호)을 걸지 않는다. 그 규칙은 사람을
     * "Password1!" 로 몰아가고 길이보다 약하게 만든다. 길이만 본다.
     *
     * <p>글자 수는 <b>코드 포인트</b>로 센다. {@code String.length()} 로 세면
     * 한글 여덟 자가 여덟이고 이모지 한 자가 둘이다 - 둘 다 세는 사람의 눈과
     * 다르다. 특히 한글·한자 사용자에게 글자당 한 칸씩 더 요구하면 같은 규칙이
     * 더 엄해진다.
     */
    private static final int MIN_PASSWORD_LENGTH = 8;

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder KEYS = Base64.getUrlEncoder().withoutPadding();

    private final UserAccountMapper mapper;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final int inviteHours;

    public UserAccountService(UserAccountMapper mapper,
                              @Value("${weboffice.auth.invite-hours:72}") int inviteHours) {
        this.mapper = mapper;
        this.inviteHours = inviteHours;
    }

    /** 발급한 초대. 평문 토큰이 나가는 자리는 여기 하나뿐이다. */
    public record Invited(String userKey, String email, String token, LocalDateTime expiresAt) {
    }

    public List<UserAccountRow> list() {
        return mapper.selectAll();
    }

    /**
     * 사람을 등록하고 초대를 발급한다.
     *
     * <p>이미 있는 이메일이면 새로 만들지 않고 <b>초대만 다시 낸다</b>. 관리자가
     * 같은 주소를 두 번 적는 가장 흔한 까닭이 "링크를 잃었다" 이기 때문이다.
     */
    @Transactional
    public Invited register(String rawEmail, String displayName, String role, String actor) {
        String email = normalizeEmail(rawEmail);
        String name = displayName == null || displayName.isBlank()
                ? email.substring(0, email.indexOf('@'))
                : displayName.trim();
        String roleCode = "ADMIN".equals(role) ? "ADMIN" : "USER";

        UserAccountRow found = mapper.selectByEmail(email);
        if (found == null) {
            found = new UserAccountRow(newUserKey(), email, name, null, roleCode, null);
            mapper.insertUser(found, actor);
        }
        return invite(found.userKey(), actor);
    }

    /**
     * 초대를 다시 낸다. 앞선 초대는 그 자리에서 못 쓰게 된다.
     *
     * <p>버리는 이유: 링크가 샌 것 같아 다시 내는 경우가 있다. 앞선 것이 살아
     * 있으면 다시 내는 일에 뜻이 없다.
     */
    @Transactional
    public Invited invite(String userKey, String actor) {
        UserAccountRow found = mapper.selectByKey(userKey);
        if (found == null) {
            throw new IllegalArgumentException("없는 사용자입니다");
        }
        LocalDateTime now = LocalDateTime.now();
        mapper.expireInvitesOf(userKey, now);

        String token = InviteToken.make();
        LocalDateTime expiresAt = now.plusHours(inviteHours);
        mapper.insertInvite(InviteToken.hash(token), userKey, expiresAt, actor);
        return new Invited(userKey, found.email(), token, expiresAt);
    }

    /** 초대가 가리키는 사람. 지났거나 이미 썼으면 null. */
    public UserAccountRow findInvited(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        return mapper.selectInvitedUser(InviteToken.hash(token), LocalDateTime.now());
    }

    /**
     * 초대를 써서 비밀번호를 정한다.
     *
     * <p>토큰을 먼저 쓴 것으로 표시하고 그 다음에 해시를 넣는다. 거꾸로 하면
     * 두 요청이 같은 토큰으로 동시에 들어올 때 둘 다 통과한다.
     */
    @Transactional
    public UserAccountRow acceptInvite(String token, String password) {
        UserAccountRow found = findInvited(token);
        if (found == null) {
            throw new IllegalArgumentException("초대가 만료되었거나 이미 사용되었습니다");
        }
        requireUsablePassword(password);

        if (mapper.markInviteUsed(InviteToken.hash(token), LocalDateTime.now()) != 1) {
            throw new IllegalArgumentException("초대가 만료되었거나 이미 사용되었습니다");
        }
        mapper.updatePasswordHash(found.userKey(), encoder.encode(password), found.userKey());
        return mapper.selectByKey(found.userKey());
    }

    /**
     * 이메일과 비밀번호를 맞춰 본다. 아니면 null.
     *
     * <p>"없는 이메일" 과 "틀린 비밀번호" 를 가려 주지 않는다 - 가려 주면 어느
     * 주소가 등록돼 있는지 하나씩 물어 알아낼 수 있다.
     *
     * <p>이메일이 없을 때도 해시를 한 번 맞춰 본다. 안 하면 응답 시간이 "없는
     * 주소" 쪽이 눈에 띄게 빨라 같은 것을 알려 준다.
     */
    public UserAccountRow signIn(String rawEmail, String password) {
        UserAccountRow found = rawEmail == null ? null
                : mapper.selectByEmail(rawEmail.trim().toLowerCase(Locale.ROOT));
        if (found == null || !found.canSignIn()) {
            encoder.matches(password == null ? "" : password, DUMMY_HASH);
            return null;
        }
        return encoder.matches(password, found.passwordHash()) ? found : null;
    }

    @Transactional
    public void setDisabled(String userKey, boolean disabled, String actor) {
        if (mapper.selectByKey(userKey) == null) {
            throw new IllegalArgumentException("없는 사용자입니다");
        }
        mapper.updateDisabled(userKey, disabled, actor);
    }

    /** 첫 관리자. 이미 있으면 아무것도 하지 않는다. */
    @Transactional
    public Invited ensureAdmin(String rawEmail) {
        String email = normalizeEmail(rawEmail);
        UserAccountRow found = mapper.selectByEmail(email);
        if (found != null) {
            return null;
        }
        return register(email, null, "ADMIN", "system");
    }

    private void requireUsablePassword(String password) {
        if (password == null
                || password.codePointCount(0, password.length()) < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException(
                    "비밀번호는 " + MIN_PASSWORD_LENGTH + "자 이상이어야 합니다");
        }
    }

    private static String normalizeEmail(String rawEmail) {
        String email = rawEmail == null ? "" : rawEmail.trim().toLowerCase(Locale.ROOT);
        if (!EMAIL.matcher(email).matches()) {
            throw new IllegalArgumentException("이메일 모양이 아닙니다: " + rawEmail);
        }
        return email;
    }

    /** 신원 열쇠. 손님 열쇠와 같은 모양(22글자)이라 섞여도 컬럼 길이가 맞는다. */
    private static String newUserKey() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return KEYS.encodeToString(bytes);
    }

    /**
     * 없는 이메일에도 한 번 맞춰 보기 위한 해시. 어떤 비밀번호와도 안 맞는다.
     *
     * <p>$2a$10$ 로 시작하는 올바른 BCrypt 모양이어야 한다 - 모양이 틀리면
     * {@code matches} 가 바로 false 를 주고 시간을 쓰지 않아 뜻이 없어진다.
     */
    private static final String DUMMY_HASH =
            "$2a$10$ThisIsADummyHashUsedOnlyToSpendTheSameTimeAsARealOne.";
}
