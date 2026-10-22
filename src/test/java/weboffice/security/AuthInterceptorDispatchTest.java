package weboffice.security;

import jakarta.servlet.DispatcherType;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * FORWARD 디스패치는 인터셉터를 다시 타면 안 된다.
 *
 * <p>{@code ViewPageController.open} 이 {@code "forward:/viewer.html"} 을
 * 돌려주는데, 톰캣의 FORWARD 디스패치도 이 인터셉터를 다시 태운다. 처음
 * 요청(GET /v/{token})에서 이미 판정을 끝냈는데, {@code /viewer.html} 은
 * 열린 목록 어디에도 없어 두 번째 통과에서 로그인으로 튕겼다 - 실제로
 * 서버를 띄워 브라우저에서 확인됐다(2026-08-20 실측). MockMvc 는 forward 를
 * 실행하지 않아 이 결함을 못 잡는다 - 그래서 인터셉터를 직접 부른다.
 *
 * <p>신원을 모르는 인증기를 끼운다. 로그인 없는 모드의 인증기는 늘 신원을
 * 만들어 주므로 막히는 쪽을 볼 수 없다.
 */
class AuthInterceptorDispatchTest {

    private AuthInterceptor interceptor() {
        Authenticator authenticator = mock(Authenticator.class);
        when(authenticator.authenticate(any(), any())).thenReturn(null);
        return new AuthInterceptor(authenticator);
    }

    @Test
    void FORWARD_디스패치는_인터셉터를_그대로_통과한다() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/viewer.html");
        request.setDispatcherType(DispatcherType.FORWARD);

        boolean passed = interceptor().preHandle(request, new MockHttpServletResponse(), null);

        assertThat(passed).isTrue();
    }

    @Test
    void 같은_경로도_REQUEST_로_바로_오면_신원_없이는_막힌다() throws Exception {
        // FORWARD 만 봐주는 것이지, /viewer.html 자체를 연 것이 아니다 - 판정이
        // 느슨해지지 않았는지 확인한다.
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/viewer.html");
        request.setDispatcherType(DispatcherType.REQUEST);

        boolean passed = interceptor().preHandle(request, new MockHttpServletResponse(), null);

        assertThat(passed).isFalse();
    }

    @Test
    void 관리자_화면도_REQUEST_로_오면_신원_없이는_막힌다() throws Exception {
        // ViewAdminController.page 도 forward:/admin.html 을 쓰지만, 그 앞 관문인
        // /admin/views 자체(REQUEST)는 여전히 막혀야 한다.
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/admin/views");
        request.setDispatcherType(DispatcherType.REQUEST);

        boolean passed = interceptor().preHandle(request, new MockHttpServletResponse(), null);

        assertThat(passed).isFalse();
    }
}
