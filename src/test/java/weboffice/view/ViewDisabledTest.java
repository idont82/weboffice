package weboffice.view;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 비상 차단기(설계 9절 {@code weboffice.view.enabled}).
 *
 * <p>운영자가 문을 닫았다고 믿는데 그대로 열려 있으면 그 믿음이 곧 사고다.
 * 끄면 발급도 보기도 막힌다.
 */
@SpringBootTest(properties = "weboffice.view.enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ViewDisabledTest {

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
        when(authenticator.authenticate(any(), any()))
                .thenReturn(new LoginUser("admin1", "12345", "홍길동", "책임", "D1",
                        "전산팀", "ko"));
        when(userDirectory.findByKeys(any())).thenReturn(List.of(
                new DirectoryUser("12345", "admin1", "홍길동", "책임", "D1", "전산팀")));
    }

    @Test
    void 꺼져_있으면_발급을_막는다() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "보고서.xlsx",
                "application/octet-stream", new byte[] {1, 2, 3});

        mockMvc.perform(multipart("/api/view/documents").file(file)
                        .param("fileName", "보고서.xlsx")
                        .param("viewerName", "홍길동 책임")
                        .header("X-WO-API-KEY", "test-key"))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void 꺼져_있으면_보기_화면도_막는다() throws Exception {
        mockMvc.perform(get("/v/{token}", "0".repeat(32)))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void 꺼져_있으면_스냅샷도_막는다() throws Exception {
        mockMvc.perform(get("/api/view/{token}/snapshot", "0".repeat(32)))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void 꺼져_있어도_관리자_이력은_본다() throws Exception {
        // 관리자 화면은 이력을 보는 자리다. 문을 닫는 순간이야말로 "무슨 일이
        // 있었나" 를 봐야 할 때라 차단기에 함께 걸지 않는다.
        mockMvc.perform(get("/api/view/admin/events"))
                .andExpect(status().isOk());
    }
}
