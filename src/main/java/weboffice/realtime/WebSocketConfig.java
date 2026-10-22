package weboffice.realtime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final SessionHandshakeInterceptor handshakeInterceptor;
    private final String[] allowedOriginPatterns;

    public WebSocketConfig(SessionHandshakeInterceptor handshakeInterceptor,
                           @Value("${weboffice.realtime.allowed-origin-patterns}")
                           String[] allowedOriginPatterns) {
        this.handshakeInterceptor = handshakeInterceptor;
        this.allowedOriginPatterns = allowedOriginPatterns;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 허용 origin 을 설정값으로 받는다. '*' 를 쓰면 어느 사이트에서든 사용자의
        // 세션 쿠키로 연결을 열 수 있다. 배포 시 인명부 도메인으로 바꾼다.
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(allowedOriginPatterns)
                .addInterceptors(handshakeInterceptor);
    }
}
