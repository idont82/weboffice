package weboffice.security;

/**
 * 알림에 실을 행위자. <b>사번이 없다.</b>
 *
 * <p>{@code name} 은 "홍길동 책임" 처럼 이름과 직급을 붙인 것이다. 화면이 뒤에
 * "님이 ..." 를 붙여 쓴다. 부서는 넣지 않는다 - 상태줄 한 줄에 다 들어가지 않고,
 * 누가 고쳤는지 아는 데 부서까지는 필요 없다.
 *
 * <p>이름을 모를 수 있다. 인명부가 없는 환경이거나 사라진 계정이다. 그때는 화면이
 * "다른 사용자" 로 물러선다 - 알림이 아예 안 뜨는 것보다 낫다.
 */
public record Actor(String userId, String name) {

    private static final Actor UNKNOWN = new Actor(null, null);

    public static Actor unknown() {
        return UNKNOWN;
    }

    public static Actor of(LoginUser user) {
        if (user == null) {
            return UNKNOWN;
        }
        return new Actor(user.userId(), display(user.displayName(), user.rank()));
    }

    /** 이름과 직급을 붙인다. 하나만 있으면 그것만, 둘 다 없으면 null. */
    public static String display(String displayName, String rank) {
        String trimmedName = displayName == null ? "" : displayName.trim();
        String trimmedRank = rank == null ? "" : rank.trim();
        String joined = (trimmedName + " " + trimmedRank).trim();
        return joined.isEmpty() ? null : joined;
    }
}
