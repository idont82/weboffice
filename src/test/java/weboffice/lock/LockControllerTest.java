package weboffice.lock;

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
import weboffice.sheet.SheetMapper;
import weboffice.sheet.SheetSummaryDto;
import weboffice.support.DbTestSupport;
import weboffice.workbook.MemberMapper;
import weboffice.workbook.WorkbookDto;
import weboffice.workbook.WorkbookMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LockControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private WorkbookMapper workbookMapper;

    @Autowired
    private SheetMapper sheetMapper;

    @Autowired
    private MemberMapper memberMapper;

    @Autowired
    private LockMapper lockMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private Authenticator authenticator;

    private Long sheetSeq;

    @BeforeEach
    void 문서와_시트와_구성원을_만든다() {
        DbTestSupport.deleteAll(jdbcTemplate);
        WorkbookDto workbook = new WorkbookDto(null, "잠금 API 문서", "owner1", null, null);
        workbookMapper.insertWorkbook(workbook, "owner1");
        SheetSummaryDto sheet = new SheetSummaryDto(null, workbook.workbookSeq(), "시트1", 0, 0, 0);
        sheetMapper.insertSheet(sheet, "owner1");
        sheetSeq = sheet.sheetSeq();
        memberMapper.insertMember(workbook.workbookSeq(), "editor1", "EDITOR", "owner1");
        memberMapper.insertMember(workbook.workbookSeq(), "viewer1", "VIEWER", "owner1");
    }

    @Test
    void 잠금을_얻으면_잠금_정보를_돌려준다() throws Exception {
        loggedInAs("owner1");

        mockMvc.perform(post("/api/locks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(acquireBody(0, 0, 9, 9)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lockSeq").isNumber())
                .andExpect(jsonPath("$.userId").value("owner1-id"))
                // 사번을 담는 자리가 아예 없다.
                .andExpect(jsonPath("$.lockUser").doesNotExist());
    }

    @Test
    void 겹쳐도_막지_않는다() throws Exception {
        // 잠금이 아니라 표시다. 겹친다고 남의 편집을 막으면 협업이 아니다.
        loggedInAs("owner1");
        mockMvc.perform(post("/api/locks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(acquireBody(0, 0, 9, 9)))
                .andExpect(status().isOk());

        loggedInAs("editor1");
        mockMvc.perform(post("/api/locks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(acquireBody(5, 5, 14, 14)))
                .andExpect(status().isOk());
    }

    @Test
    void VIEWER_는_자리를_잡지_못한다() throws Exception {
        loggedInAs("viewer1");

        mockMvc.perform(post("/api/locks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(acquireBody(0, 0, 9, 9)))
                .andExpect(status().isForbidden());
    }

    @Test
    void 해제하면_204_이고_범위가_풀린다() throws Exception {
        loggedInAs("owner1");
        String body = mockMvc.perform(post("/api/locks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(acquireBody(0, 0, 9, 9)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Long lockSeq = lockSeqOf(body);

        mockMvc.perform(delete("/api/locks/{lockSeq}", lockSeq))
                .andExpect(status().isNoContent());

        assertThat(lockMapper.selectActiveLocks(sheetSeq)).isEmpty();
    }

    @Test
    void 하트비트는_204_다() throws Exception {
        loggedInAs("owner1");
        String body = mockMvc.perform(post("/api/locks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(acquireBody(0, 0, 9, 9)))
                .andReturn().getResponse().getContentAsString();

        mockMvc.perform(post("/api/locks/{lockSeq}/heartbeat", lockSeqOf(body)))
                .andExpect(status().isNoContent());
    }

    private String acquireBody(int fromRowNo, int fromColumnNo, int toRowNo, int toColumnNo) {
        return """
                {"sheetSeq":%d,"fromRowNo":%d,"fromColumnNo":%d,"toRowNo":%d,"toColumnNo":%d}
                """.formatted(sheetSeq, fromRowNo, fromColumnNo, toRowNo, toColumnNo);
    }

    private Long lockSeqOf(String json) {
        int start = json.indexOf("\"lockSeq\":") + "\"lockSeq\":".length();
        int end = start;
        while (end < json.length() && Character.isDigit(json.charAt(end))) {
            end++;
        }
        return Long.valueOf(json.substring(start, end));
    }

    private void loggedInAs(String userKey) {
        when(authenticator.authenticate(any(), any()))
                // 아이디와 사번을 다르게 둔다. 같으면 사번이 새어도 눈치채지 못한다.
                .thenReturn(new LoginUser(userKey + "-id", userKey, userKey + " 사원",
                        "사원", "D100", "전산팀", "ko"));
    }
}
