package weboffice.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthInterceptorTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private Authenticator authenticator;

    @Test
    void 신원을_모르는_REST_요청은_401() throws Exception {
        when(authenticator.authenticate(any(), any())).thenReturn(null);

        mockMvc.perform(get("/api/sheets/1/cells")
                        .param("fromRowNo", "0").param("fromColumnNo", "0")
                        .param("toRowNo", "9").param("toColumnNo", "9"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 신원을_모르는_화면_요청은_로그인으로_보낸다() throws Exception {
        when(authenticator.authenticate(any(), any())).thenReturn(null);

        mockMvc.perform(get("/edit").param("workbookSeq", "7"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/login*"));
    }

    @Test
    void 신원이_있으면_통과한다() throws Exception {
        when(authenticator.authenticate(any(), any()))
                .thenReturn(new LoginUser("hong", "12345", "홍길동", "과장", "D100", "전산팀", "ko"));

        // 403 은 권한에서 막혔다는 뜻이다(그런 시트가 없다). 확인 대상은
        // "401 이 아니다" — 인증 단계는 통과했다.
        mockMvc.perform(get("/api/sheets/1/cells")
                        .param("fromRowNo", "0").param("fromColumnNo", "0")
                        .param("toRowNo", "9").param("toColumnNo", "9"))
                .andExpect(status().isForbidden());
    }

    @Test
    void 헬스와_로그인_화면은_인증_없이_열린다() throws Exception {
        when(authenticator.authenticate(any(), any())).thenReturn(null);

        mockMvc.perform(get("/api/health")).andExpect(status().isOk());
        mockMvc.perform(get("/login.html")).andExpect(status().isOk());
    }

    @Test
    void 지금_들어와_있는_사람을_알려준다() throws Exception {
        // 화면 오른쪽 위에 누구인지 띄우려면 새로고침 뒤에도 물어볼 곳이 있어야
        // 한다. 들어올 때 한 번만 주면 그 순간에만 알 수 있다.
        when(authenticator.authenticate(any(), any()))
                .thenReturn(new LoginUser("hong", "12345", "홍길동", "과장", "D100", "전산팀", "ko"));

        mockMvc.perform(get("/api/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("hong"))
                .andExpect(jsonPath("$.displayName").value("홍길동"));
    }

    @Test
    void 신원_열쇠는_브라우저로_내려보내지_않는다() throws Exception {
        // 열쇠는 소유권·잠금·권한 판정에 쓰는 내부 식별자다. 화면에 띄우면
        // 어깨너머로 읽히고 스크린샷에 남는다.
        when(authenticator.authenticate(any(), any()))
                .thenReturn(new LoginUser("hong", "12345", "홍길동", "과장", "D100", "전산팀", "ko"));

        String body = mockMvc.perform(get("/api/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userKey").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        // 필드명만 막으면 다른 이름으로 새어 나갈 수 있다. 값 자체가 없어야 한다.
        assertThat(body).doesNotContain("12345");
    }

    @Test
    void 신원을_모르면_누구인지도_알려주지_않는다() throws Exception {
        // 열어 두면 안 된다. 신원을 확인하는 경로가 인증을 건너뛰면 그 자체로
        // 뜻이 없다.
        when(authenticator.authenticate(any(), any())).thenReturn(null);

        mockMvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void 오류_페이지는_로그인으로_돌리지_않는다() throws Exception {
        // 404·500 이 나면 톰캣이 /error 로 포워드한다. 이 경로까지 막으면
        // 인증 안 된 사용자의 모든 오류가 "로그인하세요" 로 둔갑한다.
        // 실제로 /assets/ 를 요청하면 404 -> /error -> /login?returnTo=/error
        // 가 나왔다(2026-08-05 실측).
        when(authenticator.authenticate(any(), any())).thenReturn(null);

        mockMvc.perform(get("/error"))
                .andExpect(status().is(not(302)));
    }

    @Test
    void 정적_자원은_인증_없이_통과한다() throws Exception {
        // 404 를 기대하는 이유: 프론트 번들은 prepare-package 에서 만들어지므로
        // test 단계에 파일이 없다. 확인 대상은 파일 존재가 아니라 인터셉터가
        // 이 경로를 가로채 로그인으로 돌리지 않는다는 것이다.
        when(authenticator.authenticate(any(), any())).thenReturn(null);

        mockMvc.perform(get("/assets/index-abc123.js")).andExpect(status().isNotFound());
    }
}
