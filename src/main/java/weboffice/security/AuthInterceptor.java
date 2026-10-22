package weboffice.security;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 모든 요청의 신원을 세운다.
 *
 * <p>세 갈래다. (1) 세션이 있으면 통과, (2) 없으면 {@link Authenticator} 에게
 * 물어 세션을 만들고, (3) 그것도 없으면 REST 는 401, 화면은 /login 으로 보낸다.
 *
 * <p>어느 방식으로 신원을 정하는지는 모른다. 로그인 없는 모드에서는
 * {@link GuestAuthenticator} 가 늘 신원을 만들어 주므로 (3)에 이르지 않는다.
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    /**
     * 인증 없이 여는 경로.
     *
     * <p>앱 셸(index.html)은 여기에 없다. 셸까지 열면 인증 없이 화면이 뜬 뒤 그
     * 안의 API 만 401 이 나서, 프론트가 스스로 로그인으로 튕기는 코드를 따로
     * 들고 있어야 한다. 셸을 막으면 그 일을 서버 리다이렉트 한 줄이 대신한다.
     * 번들 자원(/assets/)은 열어야 로그인 후 화면이 뜬다. 탭 아이콘(/favicon.)도
     * 연다 - 로그인 화면에도 아이콘이 있어야 하고, 막아도 얻는 것이 없다.
     */
    private static final List<String> OPEN_PREFIXES = List.of(
            "/login", "/logout", "/invite", "/assets/", "/favicon.");

    /**
     * 정확히 일치할 때만 여는 경로.
     *
     * <p>{@code /api/login} 을 여는 이유: 로그인 엔드포인트를 막으면 닭과 달걀이
     * 된다. 로그인하려면 로그인해야 한다. 위의 {@code "/login"} 접두사는 화면
     * 경로라 {@code startsWith} 로는 {@code /api/login} 과 맞지 않는다.
     *
     * <p>{@code /error} 를 여는 이유: 404·500 이 나면 톰캣이 이 경로로 포워드한다.
     * 막아 두면 인증 안 된 사용자의 모든 오류가 "로그인하세요" 로 둔갑한다.
     *
     * <p>접두사가 아니라 완전 일치로 두는 것이 중요하다. {@code /api/login} 을
     * 접두사로 열면 {@code /api/login-history} 같은 경로가 딸려 열린다.
     *
     * <p>{@code /api/view/documents} 를 여는 이유: 이 길을 부르는 것은 사람이
     * 아니라 다른 시스템이다. 대신 {@code ViewApiController} 가
     * {@code X-WO-API-KEY} 와 허용 대역으로 부르는 쪽을 가린다.
     *
     * <p>{@code /api/invite/accept} 를 여는 이유: 초대를 받은 사람은 아직
     * 비밀번호가 없어 들어올 수 없다. 토큰 자체가 그 사람의 증거다.
     */
    private static final List<String> OPEN_PATHS =
            List.of("/api/health", "/api/login", "/api/invite/accept", "/error",
                    "/api/view/documents");

    /**
     * 접두사로 여는 경로.
     *
     * <p>외부 뷰어는 로그인이 아니라 <b>API 키와 보기 토큰</b>으로 판정한다.
     * 그 판정은 각 컨트롤러가 하므로 여기서는 통과시킨다. 관리자 화면(/admin)은
     * 열지 않는다.
     */
    private static final List<String> OPEN_PREFIXES_VIEW = List.of("/v/");

    /** 창을 가리키는 머리. 브라우저가 탭마다 다른 값을 붙인다. */
    static final String CLIENT_HEADER = "X-WO-Client";

    private final Authenticator authenticator;

    public AuthInterceptor(Authenticator authenticator) {
        this.authenticator = authenticator;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws IOException {
        // FORWARD(등 REQUEST 가 아닌) 디스패치는 다시 보지 않는다. 판정은 첫
        // REQUEST 에서 이미 끝났다 - ViewPageController.open 이 "forward:/viewer.html"
        // 을 돌려주면 톰캣이 이 인터셉터를 다시 태우는데, /viewer.html 은 열린
        // 목록 어디에도 없어 로그인으로 튕겼다(2026-08-20 실측, 브라우저로 확인됨).
        // 정적 파일 이름을 목록에 넣고 다니는 대신, 애초에 두 번째 통과 자체를
        // 건너뛴다 - 앞으로 다른 forward 를 더해도 같은 함정에 안 빠진다.
        if (request.getDispatcherType() != DispatcherType.REQUEST) {
            return true;
        }
        String uri = request.getRequestURI();
        if (isOpen(uri)) {
            return true;
        }

        HttpSession session = request.getSession();
        LoginUser user = UserContext.find(session);

        if (user == null) {
            user = authenticator.authenticate(request, response);
            if (user != null) {
                UserContext.set(session, user);
            }
        }

        if (user == null) {
            return reject(request, response);
        }

        UserContext.bindGrants(UserContext.findGrants(session));
        UserContext.bindToRequest(user);
        // 어느 창이 보냈는지. 실시간 알림에 실어 그 창이 자기 것을 건너뛰게 한다.
        UserContext.bindClient(request.getHeader(CLIENT_HEADER));
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        // 톰캣이 스레드를 재사용한다. 비우지 않으면 다음 요청이 이전 사용자로 처리된다.
        UserContext.clearRequest();
    }

    private boolean isOpen(String uri) {
        if (OPEN_PATHS.contains(uri)) {
            return true;
        }
        if (OPEN_PREFIXES_VIEW.stream().anyMatch(uri::startsWith)) {
            return true;
        }
        // 스냅샷은 보기 토큰·쿠키로 판정한다. 관리자 API(/api/view/admin/**)는
        // 여기서 열지 않는다 - 접두사로 열면 감사 화면이 통째로 열린다.
        if (uri.startsWith("/api/view/") && uri.endsWith("/snapshot")) {
            return true;
        }
        return OPEN_PREFIXES.stream().anyMatch(uri::startsWith);
    }

    /** REST 는 401, 화면은 로그인으로 보낸다. 화면 요청에 401 을 주면 빈 페이지만 보인다. */
    private boolean reject(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        if (request.getRequestURI().startsWith("/api/")) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"message\":\"인증이 필요합니다\"}");
        } else {
            response.sendRedirect("/login" + returnToQuery(request));
        }
        return false;
    }

    /** 로그인 후 원래 가려던 곳으로 돌려보내기 위해 현재 URL 을 넘긴다. */
    private String returnToQuery(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String query = request.getQueryString();
        String target = query == null ? uri : uri + "?" + query;
        return "?returnTo=" + URLEncoder.encode(target, StandardCharsets.UTF_8);
    }
}
