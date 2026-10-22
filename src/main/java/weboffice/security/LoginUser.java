package weboffice.security;

import java.io.Serializable;

/**
 * 지금 들어와 있는 사용자.
 *
 * <p>{@link Authenticator} 가 만든다. {@code userKey} 가 소유권·잠금·변경이력이
 * 쓰는 <b>신원 열쇠</b>다 - 이름이 사번을 가리키는 것은 이 코드가 사내에서
 * 나왔기 때문이고, 공개판에서는 손님 쿠키나 등록된 사용자의 열쇠가 들어온다.
 * 이름을 {@code userKey} 로 바꾸는 일은 따로 한다.
 *
 * <p>{@code rank}·{@code deptCode}·{@code deptName} 은 조직이 있는 설치에서만
 * 채워진다. 로그인 없는 모드에서는 null 이다.
 *
 * <p>{@code Serializable} 인 이유: HttpSession 에 담긴다. 톰캣이 재시작 시 세션을
 * 직렬화해 보관하거나 클러스터에서 복제할 때 직렬화할 수 없으면 세션이 통째로 버려진다.
 *
 * <p>이 타입을 그대로 REST 응답에 쓰지 않는다. 신원 열쇠가 들어 있다 -
 * {@code CurrentUserDto} 를 쓴다.
 */
public record LoginUser(
        String userId,
        String userKey,
        String displayName,
        String rank,
        String deptCode,
        String deptName,
        String language
) implements Serializable {
}
