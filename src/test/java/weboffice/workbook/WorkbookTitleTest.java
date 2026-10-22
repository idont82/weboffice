package weboffice.workbook;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import weboffice.security.LoginUser;
import weboffice.security.Authenticator;
import weboffice.support.DbTestSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WorkbookTitleTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private WorkbookMapper workbookMapper;

    @Autowired
    private MemberMapper memberMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private Authenticator authenticator;

    @BeforeEach
    void 데이터를_비우고_로그인한다() {
        DbTestSupport.deleteAll(jdbcTemplate);
        loggedInAs("me");
    }

    @Test
    void 제목을_고친다() throws Exception {
        Long seq = 문서를_만든다("옛 제목", "me");

        mockMvc.perform(patch("/api/workbooks/{seq}", seq)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"workbookName\":\"2분기 영업실적\"}"))
                .andExpect(status().isOk());

        assertThat(workbookMapper.selectWorkbook(seq).workbookName())
                .isEqualTo("2분기 영업실적");
    }

    @Test
    void 앞뒤_공백은_정리해_저장한다() {
        Long seq = 문서를_만든다("옛 제목", "me");

        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() ->
                mockMvc.perform(patch("/api/workbooks/{seq}", seq)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"workbookName\":\"  제목  \"}"))
                        .andExpect(status().isOk()));

        assertThat(workbookMapper.selectWorkbook(seq).workbookName()).isEqualTo("제목");
    }

    @Test
    void 편집자도_제목을_고칠_수_있다() throws Exception {
        // 오타를 고치자고 소유자를 부르는 것은 번거롭다(설계 9절).
        Long seq = 문서를_만든다("옛 제목", "other");
        memberMapper.insertMember(seq, "me", "EDITOR", "other");

        mockMvc.perform(patch("/api/workbooks/{seq}", seq)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"workbookName\":\"고친 제목\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void 열람자는_제목을_고치지_못한다() throws Exception {
        Long seq = 문서를_만든다("옛 제목", "other");
        memberMapper.insertMember(seq, "me", "VIEWER", "other");

        mockMvc.perform(patch("/api/workbooks/{seq}", seq)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"workbookName\":\"고친 제목\"}"))
                .andExpect(status().isForbidden());

        assertThat(workbookMapper.selectWorkbook(seq).workbookName()).isEqualTo("옛 제목");
    }

    @Test
    void 빈_제목은_거부한다() throws Exception {
        // 제목이 비면 목록에서 어느 문서인지 알 수 없다.
        Long seq = 문서를_만든다("옛 제목", "me");

        mockMvc.perform(patch("/api/workbooks/{seq}", seq)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"workbookName\":\"   \"}"))
                .andExpect(status().isBadRequest());

        assertThat(workbookMapper.selectWorkbook(seq).workbookName()).isEqualTo("옛 제목");
    }

    private Long 문서를_만든다(String name, String ownerUser) {
        WorkbookDto workbook = new WorkbookDto(null, name, ownerUser, null, null);
        workbookMapper.insertWorkbook(workbook, ownerUser);
        return workbook.workbookSeq();
    }

    private void loggedInAs(String userKey) {
        when(authenticator.authenticate(any(), any()))
                .thenReturn(new LoginUser(userKey + "-id", userKey, userKey, "사원", "D1", "전산팀", "ko"));
    }
}
