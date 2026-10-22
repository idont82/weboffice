package weboffice.workbook;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 휴지통.
 *
 * <p>지우기는 예전부터 소프트 삭제였지만 되살릴 길이 화면에 없었다. 구글도 지운
 * 문서를 30일 동안 들고 있다가 버린다(2026-08-20 지적).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WorkbookTrashTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private WorkbookMapper workbookMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private Authenticator authenticator;

    @MockitoBean
    private UserDirectory userDirectory;

    @BeforeEach
    void 데이터를_비우고_로그인한다() {
        DbTestSupport.deleteAll(jdbcTemplate);
        loggedInAs("me");
        when(userDirectory.findByKeys(any())).thenReturn(List.of(
                new DirectoryUser("me", "me-id", "나", "사원", "D1", "전산팀")));
    }

    @Test
    void 지운_문서가_휴지통에_보인다() throws Exception {
        Long mine = 문서를_만든다("버릴 문서", "me");
        mockMvc.perform(delete("/api/workbooks/{seq}", mine))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/workbooks/trash"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].workbookSeq").value(mine))
                .andExpect(jsonPath("$[0].workbookName").value("버릴 문서"))
                .andExpect(jsonPath("$[0].deletedAt").isNotEmpty());
    }

    @Test
    void 살아_있는_문서는_휴지통에_없다() throws Exception {
        문서를_만든다("멀쩡한 문서", "me");

        mockMvc.perform(get("/api/workbooks/trash"))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void 복구하면_목록으로_돌아온다() throws Exception {
        Long mine = 문서를_만든다("되살릴 문서", "me");
        mockMvc.perform(delete("/api/workbooks/{seq}", mine));

        mockMvc.perform(post("/api/workbooks/{seq}/restore", mine))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/workbooks"))
                .andExpect(jsonPath("$.mine.length()").value(1))
                .andExpect(jsonPath("$.mine[0].workbookSeq").value(mine));
        mockMvc.perform(get("/api/workbooks/trash"))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void 남의_문서는_휴지통에_안_보이고_복구도_못_한다() throws Exception {
        Long other = 문서를_만든다("남의 문서", "other");
        jdbcTemplate.update("""
                UPDATE wo_workbook_master
                   SET DEL_DATETIME = CURRENT_TIMESTAMP, DEL_USER = 'other'
                 WHERE WORKBOOK_SEQ = ?""", other);

        mockMvc.perform(get("/api/workbooks/trash"))
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(post("/api/workbooks/{seq}/restore", other))
                .andExpect(status().isForbidden());
    }

    @Test
    void 서른_날이_지난_것은_휴지통에_없다() throws Exception {
        // 구글도 30일까지만 들고 있다. 그 뒤에는 화면에서 되살릴 길이 없다.
        Long old = 문서를_만든다("오래된 문서", "me");
        jdbcTemplate.update("""
                UPDATE wo_workbook_master
                   SET DEL_DATETIME = ?, DEL_USER = 'me'
                 WHERE WORKBOOK_SEQ = ?""",
                java.sql.Timestamp.valueOf(java.time.LocalDateTime.now().minusDays(31)), old);

        mockMvc.perform(get("/api/workbooks/trash"))
                .andExpect(jsonPath("$.length()").value(0));
    }

    private Long 문서를_만든다(String name, String ownerUser) {
        WorkbookDto workbook = new WorkbookDto(null, name, ownerUser, null, null);
        workbookMapper.insertWorkbook(workbook, ownerUser);
        return workbook.workbookSeq();
    }

    private void loggedInAs(String userKey) {
        when(authenticator.authenticate(any(), any()))
                .thenReturn(new LoginUser(userKey, userKey, userKey, "사원", "D100", "전산팀", "ko"));
    }
}
