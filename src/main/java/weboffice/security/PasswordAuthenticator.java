package weboffice.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 등록 모드({@code weboffice.auth.mode: registered})의 인증기.
 *
 * <p>스스로 신원을 만들지 않는다. 세션에 든 것만 돌려주고 없으면 null 이다 -
 * 그러면 인터셉터가 REST 는 401, 화면은 로그인으로 보낸다. 세션을 만드는 일은
 * {@link LoginController} 가 비밀번호를 맞춰 본 뒤에 한다.
 *
 * <p>인터셉터가 세션을 먼저 보므로 이 클래스는 사실 "아무것도 못 한다" 를
 * 말하는 자리다. 그래도 두는 이유: 인증 방식이 {@link Authenticator} 하나로
 * 갈린다는 규칙을 깨지 않기 위해서다. 등록 모드에서 이 빈을 빼면 인터셉터가
 * 끼울 것이 없어 설정이 두 군데로 흩어진다.
 */
public class PasswordAuthenticator implements Authenticator {

    @Override
    public LoginUser authenticate(HttpServletRequest request, HttpServletResponse response) {
        return null;
    }
}
