package weboffice.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 등록 모드로 서버가 실제로 뜨는지 본다.
 *
 * <p>왜 따로 두는가: 다른 모든 시험은 {@code open} 기본값으로 돈다. 등록
 * 모드는 빈을 다르게 끼우는데({@link PasswordAuthenticator},
 * {@link AccountDirectory}) 그 배선이 틀려도 기본값으로 도는 시험들은 전부
 * 통과한다 - {@code WO_AUTH_MODE=registered} 로 띄운 사람에게서만 터진다.
 *
 * <p>{@code admin-email} 은 비워 둔다. 채우면 기동할 때마다 첫 관리자를
 * 만들려 하고, 그 초대 링크가 로그에 찍힌다 - 시험이 남길 것이 아니다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "weboffice.auth.mode=registered",
        "weboffice.auth.admin-email=",
})
class RegisteredModeBootTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthConfig authConfig;

    @Autowired
    private Authenticator authenticator;

    @Autowired
    private UserDirectory userDirectory;

    @Test
    void 등록_모드의_빈이_끼워진다() {
        assertThat(authConfig.isRegistered()).isTrue();
        assertThat(authenticator).isInstanceOf(PasswordAuthenticator.class);
        assertThat(userDirectory).isInstanceOf(AccountDirectory.class);
    }

    /**
     * 손님 신원을 만들어 주지 않는다. 이것이 등록 모드의 요점이다 - 만들어
     * 주면 로그인을 둔 뜻이 없다.
     */
    @Test
    void 로그인하지_않으면_들어오지_못한다() throws Exception {
        mockMvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void 로그인_화면과_초대_화면은_열려_있다() throws Exception {
        // 닭과 달걀을 피하는 자리다. 막으면 로그인하려고 로그인해야 한다.
        mockMvc.perform(get("/login.html")).andExpect(status().isOk());
        mockMvc.perform(get("/invite.html")).andExpect(status().isOk());
    }

    @Test
    void 없는_계정으로_로그인하면_401() throws Exception {
        mockMvc.perform(post("/api/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody@example.com\",\"password\":\"아무비밀번호나\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 엉뚱한_초대_토큰은_받지_않는다() throws Exception {
        mockMvc.perform(post("/api/invite/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"없는토큰\",\"password\":\"충분히긴비밀번호\"}"))
                .andExpect(status().isBadRequest());
    }

    /**
     * 관리자 화면은 로그인 없이 열리지 않는다. 감사 화면이 열람 기록·문서
     * 이름·접속 IP 를 다 보여 준다.
     */
    @Test
    void 사용자_관리는_로그인_없이_열리지_않는다() throws Exception {
        mockMvc.perform(get("/api/admin/users")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/admin/users")).andExpect(status().is3xxRedirection());
    }
}
