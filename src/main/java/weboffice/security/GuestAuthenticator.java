package weboffice.security;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.regex.Pattern;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 로그인 없는 모드({@code weboffice.auth.mode: open})의 인증기.
 *
 * <p>아무나 들어온다. 들어오면 쿠키에 담긴 열쇠로 같은 사람임을 알아보고,
 * 쿠키가 없으면 새 열쇠를 만들어 내려 준다.
 *
 * <p><b>이것은 보호가 아니다.</b> 쿠키를 지우면 다른 사람이 되고, 아무나
 * 새 신원을 만들 수 있다. 그럼에도 신원을 아예 없애지 않는 이유: 소유권·
 * 구성원·자리 표시·변경이력이 모두 신원을 요구한다. 없애면 공동편집이
 * 사라진다. 익명이되 식별되는 것이 가장 적은 비용이다.
 *
 * <p>그래서 이 모드는 신뢰된 망에서만 쓸 것을 문서에 적는다.
 */
public class GuestAuthenticator implements Authenticator {

    /** 손님 열쇠를 담는 쿠키. */
    public static final String COOKIE = "wo_guest";

    /** 쿠키 수명. 손님이 어제 만든 문서를 오늘도 자기 것으로 보려면 길어야 한다. */
    private static final int MAX_AGE_SECONDS = 400 * 24 * 60 * 60;

    /**
     * 열쇠 모양. 16바이트를 패딩 없는 base64url 로 적은 22글자다.
     *
     * <p>쿠키 값은 손님이 고쳐 보낼 수 있고, 그것이 그대로 소유자 열쇠와 화면에
     * 뿌릴 이름이 된다. 모양을 좁게 잡아 어긋나면 받지 않는다.
     */
    private static final Pattern KEY = Pattern.compile("[A-Za-z0-9_-]{22}");

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    /** 열쇠의 모양이 맞는가. 쿠키 값과 인명부 조회가 같은 자를 써야 한다. */
    public static boolean isKey(String value) {
        return value != null && KEY.matcher(value).matches();
    }

    /**
     * 열쇠로 지은 표시 이름.
     *
     * <p>{@link GuestDirectory} 도 이것을 쓴다. 규칙이 두 군데 있으면 자기 이름이
     * 자리 표시에서 다르게 보인다.
     */
    public static String nameOf(String key) {
        return "손님-" + key.substring(0, 4);
    }

    @Override
    public LoginUser authenticate(HttpServletRequest request, HttpServletResponse response) {
        String found = fromCookie(request);
        if (found != null) {
            // 이미 쥐고 있다. 매 요청마다 다시 내리면 Set-Cookie 가 응답마다 붙는다.
            return guest(found);
        }

        String made = newKey();
        response.addCookie(cookie(made));
        return guest(made);
    }

    private static String fromCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (COOKIE.equals(cookie.getName()) && isKey(cookie.getValue())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private static Cookie cookie(String key) {
        Cookie cookie = new Cookie(COOKIE, key);
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(MAX_AGE_SECONDS);
        // Lax 다. Strict 로 두면 바깥 시스템에서 보기 링크를 눌러 들어온 첫
        // 요청에 쿠키가 안 실려 그때마다 새 손님이 된다.
        cookie.setAttribute("SameSite", "Lax");
        return cookie;
    }

    private static String newKey() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return ENCODER.encodeToString(bytes);
    }

    /**
     * 열쇠 하나로 신원을 짓는다.
     *
     * <p>이름을 열쇠 앞자리에서 뽑으므로 저장소를 보지 않는다. 자리 표시와
     * 변경이력이 이름을 요구하는데, 손님마다 행을 만들지 않고도 "손님-ab12" 로
     * 서로를 가릴 수 있다.
     */
    private static LoginUser guest(String key) {
        return new LoginUser(key, key, nameOf(key), null, null, null, "ko");
    }
}
