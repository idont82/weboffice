package weboffice.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 요청에서 신원을 알아낸다.
 *
 * <p>인증 방식을 가리는 자리다. 사내판은 여기에 사내 SSO 를 끼우고, 공개판은
 * 설정({@code weboffice.auth.mode})에 따라 손님 인증기나 비밀번호 인증기를
 * 끼운다. 인터셉터는 어느 것이 끼워졌는지 모른다.
 */
public interface Authenticator {

    /**
     * 신원을 알아낸다. 모르면 null.
     *
     * <p>null 을 돌려주면 인터셉터가 막는다 - REST 는 401, 화면은 로그인으로
     * 보낸다. 그러니 "누구나 들어오는" 방식은 여기서 null 을 주지 않고 새
     * 신원을 만들어 준다.
     *
     * <p>{@code response} 를 받는 이유: 신원을 만들면서 쿠키를 내려야 하는
     * 방식이 있다.
     */
    LoginUser authenticate(HttpServletRequest request, HttpServletResponse response);
}
