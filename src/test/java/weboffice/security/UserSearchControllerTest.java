package weboffice.security;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserSearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private Authenticator authenticator;

    /** 인명부가 무엇이든 끝점의 규칙은 같아야 한다. 대역을 넣고 그 규칙만 본다. */
    @MockitoBean
    private UserDirectory userDirectory;

    @BeforeEach
    void 들어온다() {
        when(authenticator.authenticate(any(), any()))
                .thenReturn(new LoginUser("hong", "12345", "홍길동", "과장", "D100", "전산팀", "ko"));
    }

    @Test
    void 이름으로_찾으면_아이디와_표시할_것만_준다() throws Exception {
        when(userDirectory.searchByName(eq("홍길"), anyInt())).thenReturn(List.of(
                new DirectoryUser("12345678", "hong", "홍길동", "과장", "D100123", "전산팀")));

        String body = mockMvc.perform(get("/api/users").param("q", "홍길"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userId").value("hong"))
                .andExpect(jsonPath("$[0].displayName").value("홍길동"))
                .andExpect(jsonPath("$[0].rank").value("과장"))
                .andExpect(jsonPath("$[0].deptName").value("전산팀"))
                .andReturn().getResponse().getContentAsString();

        // 신원 열쇠와 부서코드는 화면에 필요 없고 내보낼 이유도 없다.
        assertThat(body).doesNotContain("12345678");
        assertThat(body).doesNotContain("D100123");
    }

    @Test
    void 한_글자로는_찾지_않는다() throws Exception {
        // 빈 검색어나 한 글자를 허용하면 인명부를 통째로 훑을 수 있다.
        mockMvc.perform(get("/api/users").param("q", "홍")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/users").param("q", " ")).andExpect(status().isBadRequest());

        verify(userDirectory, never()).searchByName(any(), anyInt());
    }

    @Test
    void 결과_수를_제한한다() throws Exception {
        mockMvc.perform(get("/api/users").param("q", "홍길")).andExpect(status().isOk());

        verify(userDirectory).searchByName("홍길", 20);
    }

    @Test
    void 신원이_없으면_인명부를_못_본다() throws Exception {
        when(authenticator.authenticate(any(), any())).thenReturn(null);

        mockMvc.perform(get("/api/users").param("q", "홍길"))
                .andExpect(status().isUnauthorized());
    }
}
