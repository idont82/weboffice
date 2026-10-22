package weboffice.security;

import java.time.Duration;

import org.springframework.http.CacheControl;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;

    public WebMvcConfig(AuthInterceptor authInterceptor) {
        this.authInterceptor = authInterceptor;
    }

    /**
     * 정적 파일 캐시.
     *
     * <p>브라우저가 {@code index.html} 을 붙잡고 있어 새 화면을 배포해도 옛 번들을
     * 계속 썼다 - 고친 것이 안 고쳐진 것처럼 보인다(2026-08-07 실측).
     *
     * <p>파일마다 다르게 다룬다. html 은 이름이 늘 같으니 캐시하면 안 되고,
     * {@code /assets/} 아래는 이름에 내용 해시가 붙어 있어 영구 캐시해도 안전하다 -
     * 내용이 바뀌면 이름이 바뀐다. 매번 다시 받으면 문서를 열 때마다 6MB 를 내려받는다.
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/assets/**")
                .addResourceLocations("classpath:/static/assets/")
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).immutable());

        // 나머지는 손대지 않는다. "/**" 를 등록하면 Spring Boot 의 기본 핸들러를
        // 밀어내 "/" → index.html 매핑이 사라진다 - 첫 화면이 404 가 된다
        // (2026-08-07 실측). html 쪽 no-store 는 application.yml 의
        // spring.web.resources.cache 로 준다.
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 열어 둘 경로는 AuthInterceptor 안에서 판정한다. 여기서 excludePathPatterns 로
        // 나누면 판정 기준이 두 곳에 흩어진다.
        registry.addInterceptor(authInterceptor).addPathPatterns("/**");
    }
}
