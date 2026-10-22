package weboffice.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 정적 파일 캐시.
 *
 * <p>브라우저가 {@code index.html} 을 붙잡고 있어 새 화면을 배포해도 옛 번들을 계속
 * 썼다. 고친 것이 안 고쳐진 것처럼 보였다(2026-08-07 실측: 디스크는 새 번들인데
 * 브라우저는 옛 번들을 로드하고 있었다).
 *
 * <p>파일마다 다뤄야 한다. {@code index.html} 은 이름이 늘 같으니 캐시하면 안 되고,
 * {@code /assets/} 아래는 이름에 내용 해시가 붙어 있어 영구 캐시해도 안전하다 -
 * 내용이 바뀌면 이름이 바뀐다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StaticCacheTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private Authenticator authenticator;

    @Test
    void 로그인_화면은_캐시하지_않는다() throws Exception {
        // 인증 없이 받을 수 있는 html 이라 여기서 확인한다. index.html 도 같은
        // 처리를 받는다 - 둘 다 이름이 늘 같다.
        String cache = mockMvc.perform(get("/login.html"))
                .andReturn().getResponse().getHeader(HttpHeaders.CACHE_CONTROL);

        assertThat(cache).as("html 은 늘 새로 받아야 한다").contains("no-store");
    }

    @Test
    void 해시가_붙은_자원은_오래_캐시한다() throws Exception {
        // 이름에 내용 해시가 있어 바뀌면 주소가 바뀐다. 매번 다시 받으면 문서를 열
        // 때마다 6MB 를 내려받는다.
        // 실제 파일이 있어야 헤더가 붙는다. 404 에는 캐시 헤더가 없다.
        // src/test/resources/static/assets/probe-abc123.js 를 쓴다.
        String cache = mockMvc.perform(get("/assets/probe-abc123.js"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader(HttpHeaders.CACHE_CONTROL);

        assertThat(cache).as("해시 붙은 자원").contains("max-age=");
        assertThat(cache).doesNotContain("no-store");
    }
}
