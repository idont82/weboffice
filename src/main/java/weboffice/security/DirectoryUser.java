package weboffice.security;

/**
 * 인명부에서 찾은 사람.
 *
 * <p>화면에 이름을 띄우고 공유 창에서 사람을 고르는 데 쓴다. 신원 확인은
 * {@link Authenticator} 가 하고 여기는 조회만 한다.
 *
 * <p>{@code rank}·{@code deptCode}·{@code deptName} 은 조직이 있는 설치에서만
 * 채워진다. 로그인 없는 모드에서는 모두 null 이다 - 받는 쪽은 null 을 견뎌야 한다.
 */
public record DirectoryUser(
        String userKey,
        String userId,
        String displayName,
        String rank,
        String deptCode,
        String deptName
) {
}
