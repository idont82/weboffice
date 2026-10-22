package weboffice.security;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class GuestAuthenticatorTest {

    private final GuestAuthenticator authenticator = new GuestAuthenticator();

    @Test
    void 쿠키가_없으면_새_손님을_만들고_쿠키를_내린다() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        LoginUser user = authenticator.authenticate(request, response);

        assertThat(user).isNotNull();
        assertThat(user.userKey()).isNotBlank();
        Cookie sent = response.getCookie(GuestAuthenticator.COOKIE);
        assertThat(sent).isNotNull();
        assertThat(sent.getValue()).isEqualTo(user.userKey());
    }

    /**
     * 쿠키는 브라우저가 쥐고 있으니 자바스크립트가 읽을 이유가 없고, 읽을 수
     * 있으면 끼워넣기 한 번으로 신원이 샌다.
     */
    @Test
    void 내리는_쿠키는_스크립트가_못_읽는다() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        authenticator.authenticate(new MockHttpServletRequest(), response);

        assertThat(response.getCookie(GuestAuthenticator.COOKIE).isHttpOnly()).isTrue();
    }

    @Test
    void 쿠키가_있으면_같은_손님으로_돌아온다() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse first = new MockHttpServletResponse();
        LoginUser made = authenticator.authenticate(request, first);

        request.setCookies(new Cookie(GuestAuthenticator.COOKIE, made.userKey()));
        MockHttpServletResponse second = new MockHttpServletResponse();
        LoginUser again = authenticator.authenticate(request, second);

        assertThat(again.userKey()).isEqualTo(made.userKey());
        // 이미 쥐고 있는 쿠키를 매 요청마다 다시 내리지 않는다.
        assertThat(second.getCookie(GuestAuthenticator.COOKIE)).isNull();
    }

    @Test
    void 손님_이름은_열쇠_앞자리에서_나온다() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(GuestAuthenticator.COOKIE, "abcdefghijklmnopqrstuv"));

        LoginUser user = authenticator.authenticate(request, new MockHttpServletResponse());

        assertThat(user.displayName()).isEqualTo("손님-abcd");
    }

    /**
     * 쿠키 값은 손님이 고쳐 보낼 수 있다. 이것이 그대로 소유자 열쇠와 화면에
     * 뿌릴 이름이 되므로, 모양이 어긋나면 받지 않고 새로 발급한다.
     */
    @Test
    void 모양이_어긋난_쿠키는_버리고_새로_발급한다() {
        for (String bad : new String[] {"", "짧다", "abc/def+ghi=jklmnopqrs",
                "abcdefghijklmnopqrstuvwxyz0123456789abcdefghij", "<script>aaaaaaaaaaaaaaaa"}) {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setCookies(new Cookie(GuestAuthenticator.COOKIE, bad));
            MockHttpServletResponse response = new MockHttpServletResponse();

            LoginUser user = authenticator.authenticate(request, response);

            assertThat(user.userKey()).as("버려야 한다: %s", bad).isNotEqualTo(bad);
            assertThat(response.getCookie(GuestAuthenticator.COOKIE)).isNotNull();
        }
    }

    @Test
    void 손님마다_다른_열쇠를_받는다() {
        LoginUser one = authenticator.authenticate(new MockHttpServletRequest(),
                new MockHttpServletResponse());
        LoginUser two = authenticator.authenticate(new MockHttpServletRequest(),
                new MockHttpServletResponse());

        assertThat(one.userKey()).isNotEqualTo(two.userKey());
    }
}
