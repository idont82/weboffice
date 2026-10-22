package weboffice.security;

import java.time.LocalDateTime;

/**
 * {@code wo_user_account} 한 줄.
 *
 * <p>{@code passwordHash} 가 밖으로 나가는 자리는 없다. 비밀번호를 맞춰 보는
 * 일은 {@link PasswordAuthenticator} 안에서만 한다.
 */
public record UserAccountRow(
        String userKey,
        String email,
        String displayName,
        String passwordHash,
        String roleCode,
        LocalDateTime disabledAt
) {

    /** 막혔거나 아직 비밀번호를 정하지 않았으면 들어올 수 없다. */
    public boolean canSignIn() {
        return disabledAt == null && passwordHash != null && !passwordHash.isBlank();
    }

    public boolean isAdmin() {
        return "ADMIN".equals(roleCode);
    }

    public DirectoryUser toDirectoryUser() {
        return new DirectoryUser(userKey, email, displayName, null, null, null);
    }
}
