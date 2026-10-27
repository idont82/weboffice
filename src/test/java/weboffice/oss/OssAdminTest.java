package weboffice.oss;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import weboffice.security.UserDirectory;
import weboffice.security.LoginUser;
import weboffice.security.Authenticator;
import weboffice.support.DbTestSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 오픈소스 현황 화면.
 *
 * <p>관리자만 본다. 어떤 라이브러리를 어느 버전으로 쓰는지는 공격면을 그대로
 * 알려 주는 정보다 - 아무에게나 열어 둘 것이 아니다.
 *
 * <p>application-test.yml 의 admins 에 admin1 하나만 있다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OssAdminTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private Authenticator authenticator;

    @MockitoBean
    private UserDirectory userDirectory;

    @BeforeEach
    void 비우고_로그인한다() {
        DbTestSupport.deleteAll(jdbcTemplate);
        loggedInAs("admin1");
    }

    @Test
    void 관리자는_목록을_본다() throws Exception {
        mockMvc.perform(get("/api/admin/oss"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].name").value(hasItems("Univer", "Apache POI")))
                .andExpect(jsonPath("$.items[*].license").value(hasItem("Apache-2.0")))
                .andExpect(jsonPath("$.items[*].version").value(hasItem("5.5.1")))
                .andExpect(jsonPath("$.items[*].what").value(hasItem(
                        containsString("편집기 본체"))));
    }

    @Test
    void 요약이_함께_온다() throws Exception {
        mockMvc.perform(get("/api/admin/oss"))
                .andExpect(jsonPath("$.overview.open").isNumber())
                .andExpect(jsonPath("$.overview.closed").isNumber())
                .andExpect(jsonPath("$.overview.licenses[0].license").isString());
    }

    @Test
    void 목록_밖_계정은_막는다() throws Exception {
        loggedInAs("stranger");

        mockMvc.perform(get("/api/admin/oss"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_ADMIN"));
    }

    @Test
    void 화면도_관리자만_연다() throws Exception {
        mockMvc.perform(get("/admin/oss"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/oss.html"));

        loggedInAs("stranger");
        mockMvc.perform(get("/admin/oss")).andExpect(status().isForbidden());
    }

    @Test
    void 고지문을_내려준다() throws Exception {
        String notice = mockMvc.perform(get("/api/admin/oss/notice"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/plain"))
                .andReturn().getResponse().getContentAsString();

        assertThat(notice).contains("Web Office").contains("Univer").contains("Apache POI");
        // 개발 전용은 고지 대상이 아니다.
        assertThat(notice).doesNotContain("Vitest");
    }

    @Test
    void 고지문도_관리자만_받는다() throws Exception {
        loggedInAs("stranger");

        mockMvc.perform(get("/api/admin/oss/notice")).andExpect(status().isForbidden());
    }

    @Test
    void 열람이_이력에_남는다() throws Exception {
        // 감사 화면을 누가 봤는지도 감사 대상이다.
        mockMvc.perform(get("/api/admin/oss")).andExpect(status().isOk());

        Integer logged = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM wo_view_event_log WHERE EVENT_TYPE = 'ADMIN'",
                Integer.class);
        assertThat(logged).isPositive();
    }

    @Test
    void 사번은_내보내지_않는다() throws Exception {
        // 목록에 사람 정보가 들어갈 자리가 아니다. 들어가면 그 자체가 사고다.
        String body = mockMvc.perform(get("/api/admin/oss"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("userKey");
    }

    private void loggedInAs(String userId) {
        when(authenticator.authenticate(any(), any()))
                .thenReturn(new LoginUser(userId, "12345", "홍길동", "책임", "D1",
                        "전산팀", "ko"));
    }
}
