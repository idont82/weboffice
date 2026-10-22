package weboffice.view;

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

import weboffice.security.DirectoryUser;
import weboffice.security.UserDirectory;
import weboffice.security.LoginUser;
import weboffice.security.Authenticator;
import weboffice.support.DbTestSupport;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 관리자 화면.
 *
 * <p>application-test.yml 의 admins 에 admin1 하나만 있다. 목록 밖 계정은 막힌다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ViewAdminTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ViewEventMapper eventMapper;

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
        when(userDirectory.findByKeys(any())).thenReturn(List.of(
                new DirectoryUser("12345", "admin1", "홍길동", "책임", "D1", "전산팀")));
        eventMapper.insertEvent(new ViewEventRow("ISSUE", null, "전자결재", "보고서.xlsx",
                1234L, "N", "홍길동 책임", 250, "OK", null, "192.0.2.92", "agent"), "system");
        eventMapper.insertEvent(new ViewEventRow("ERROR", null, "전자결재", "사진.png",
                10L, "N", "홍길동 책임", 5, "UNSUPPORTED_TYPE", "볼 수 없는 형식입니다",
                "192.0.2.92", "agent"), "system");
    }

    @Test
    void 관리자는_이력을_본다() throws Exception {
        mockMvc.perform(get("/api/view/admin/events").param("limit", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void 종류로_추린다() throws Exception {
        mockMvc.perform(get("/api/view/admin/events").param("type", "ERROR"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].resultCode").value("UNSUPPORTED_TYPE"));
    }

    @Test
    void 통계를_준다() throws Exception {
        mockMvc.perform(get("/api/view/admin/stats").param("days", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.issued").value(1))
                .andExpect(jsonPath("$.failed").value(1));
    }

    @Test
    void 목록_밖_계정은_막는다() throws Exception {
        loggedInAs("stranger");

        mockMvc.perform(get("/api/view/admin/events"))
                .andExpect(status().isForbidden());
    }

    @Test
    void 관리자_접근도_이력에_남는다() throws Exception {
        mockMvc.perform(get("/api/view/admin/events")).andExpect(status().isOk());

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM wo_view_event_log WHERE EVENT_TYPE = 'ADMIN'",
                Integer.class);
        org.assertj.core.api.Assertions.assertThat(count).isEqualTo(1);
    }

    @Test
    void 관리자_첫_화면이_있다() throws Exception {
        // /admin 만 치고 들어오는 사람에게 무엇이 있는지 보여 준다(2026-08-20 지적).
        mockMvc.perform(get("/admin")).andExpect(status().isOk());
        mockMvc.perform(get("/admin/")).andExpect(status().isOk());
    }

    @Test
    void 관리자_첫_화면도_목록_밖_계정은_막는다() throws Exception {
        loggedInAs("stranger");

        mockMvc.perform(get("/admin")).andExpect(status().isForbidden());
    }

    private void loggedInAs(String userId) {
        // LoginUser 의 첫 인자가 userId 다(사번이 아니다) - 관리자 판정은 계정으로 한다.
        when(authenticator.authenticate(any(), any()))
                .thenReturn(new LoginUser(userId, "12345", "홍길동", "책임", "D1",
                        "전산팀", "ko"));
    }
}
