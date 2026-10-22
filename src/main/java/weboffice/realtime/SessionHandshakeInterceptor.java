package weboffice.realtime;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import jakarta.servlet.http.HttpSession;
import weboffice.security.LoginUser;
import weboffice.security.UserContext;

/**
 * WebSocket 핸드셰이크 인증.
 *
 * <p>{@code AuthInterceptor} 는 이 경로를 막지 못한다. STOMP 엔드포인트는 별도의
 * HandlerMapping 에 등록되어 {@code WebMvcConfigurer.addInterceptors} 로 붙인
 * 인터셉터가 걸리지 않기 때문이다. 그대로 두면 아무나 연결해
 * {@code /topic/sheets/*} 를 구독하고 모든 문서의 셀 변경을 실시간으로 받아간다.
 *
 * <p>세션만 본다. 여기서 SSO 를 새로 태우지 않는 이유는 핸드셰이크가 브라우저의
 * 리다이렉트를 따라갈 수 없어서다. 화면을 여는 시점에 이미 세션이 만들어져 있다.
 */
@Component
public class SessionHandshakeInterceptor implements HandshakeInterceptor {

    private static final Logger log = LoggerFactory.getLogger(SessionHandshakeInterceptor.class);

    public static final String LOGIN_USER = "weboffice.ws.loginUser";

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        LoginUser user = findUser(request);
        if (user == null) {
            log.debug("인증되지 않은 WebSocket 연결을 거부했다");
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        attributes.put(LOGIN_USER, user);
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // 할 일 없음
    }

    private LoginUser findUser(ServerHttpRequest request) {
        if (!(request instanceof ServletServerHttpRequest servletRequest)) {
            return null;
        }
        // 세션을 새로 만들지 않는다. 없으면 인증되지 않은 연결이다.
        HttpSession session = servletRequest.getServletRequest().getSession(false);
        return UserContext.find(session);
    }
}
