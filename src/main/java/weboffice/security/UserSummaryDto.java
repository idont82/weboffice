package weboffice.security;

/**
 * 브라우저로 내보내는 사용자. <b>신원 열쇠가 없다.</b>
 *
 * <p>{@link DirectoryUser} 를 그대로 내보내면 열쇠와 부서코드가 함께 나간다.
 * 화면에는 필요 없는 값이고, 인명부를 여는 경로라 더 조심한다.
 *
 * <p>항목 이름은 {@link DirectoryUser} 와 같게 둔다. 사원·사번을 전제하는
 * 이름을 쓰지 않는다 - 혼자 쓰는 사람에게도, 조직이 없는 설치에도 맞지 않는다.
 * {@code rank} 와 {@code deptName} 은 그런 설치에서는 null 이다.
 */
public record UserSummaryDto(String userId, String displayName, String rank, String deptName) {

    public static UserSummaryDto of(DirectoryUser user) {
        return new UserSummaryDto(user.userId(), user.displayName(), user.rank(),
                user.deptName());
    }
}
