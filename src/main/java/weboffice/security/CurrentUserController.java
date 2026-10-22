package weboffice.security;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 지금 들어와 있는 사람이 누구인가.
 *
 * <p>화면 오른쪽 위에 누구인지 띄우기 위한 것이다. 들어올 때 한 번만 알려 주면
 * 새로고침하면 사라진다.
 *
 * <p>열린 경로가 아니다. 인증을 통과하지 못하면 인터셉터가 401 을 준다 - 누구인지
 * 확인하는 경로가 인증을 건너뛰면 그 자체로 뜻이 없다. 로그인 없는 모드에서는
 * {@link GuestAuthenticator} 가 늘 신원을 만들어 주므로 손님 이름이 나온다.
 */
@RestController
public class CurrentUserController {

    @GetMapping("/api/me")
    public CurrentUserDto me() {
        return CurrentUserDto.of(UserContext.current());
    }
}
