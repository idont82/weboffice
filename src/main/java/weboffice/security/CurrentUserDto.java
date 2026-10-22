package weboffice.security;

/**
 * 브라우저로 내려보내는 사용자 정보.
 *
 * <p>{@code LoginUser} 를 그대로 내보내지 않는 이유는 <b>사번</b> 때문이다.
 * 사번은 개인을 특정하는 사내 식별자라 화면에 띄우면 어깨너머로 읽히고
 * 스크린샷에 남는다. 저장·잠금·권한 판정에는 계속 쓰지만 브라우저로는 보내지 않는다.
 *
 * <p>타입을 나눠 두는 것이 요점이다. 애노테이션으로 필드 하나를 가리면 나중에
 * 필드가 늘 때마다 판단해야 하고, 한 번 빠뜨리면 조용히 새어 나간다. 내보낼 것만
 * 적은 타입을 두면 새 필드는 기본이 "안 나감" 이 된다.
 */
public record CurrentUserDto(String userId, String displayName, String rank, String deptName) {

    public static CurrentUserDto of(LoginUser user) {
        return new CurrentUserDto(
                user.userId(), user.displayName(), user.rank(), user.deptName());
    }
}
