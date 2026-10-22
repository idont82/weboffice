package weboffice.sheet;

import java.util.List;

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

import weboffice.security.DirectoryUser;
import weboffice.security.UserDirectory;
import weboffice.security.LoginUser;
import weboffice.security.Authenticator;
import weboffice.support.DbTestSupport;
import weboffice.workbook.WorkbookDto;
import weboffice.workbook.WorkbookMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 칸 하나의 수정 기록.
 *
 * <p>구글 시트는 칸을 우클릭해 "수정 기록 표시" 로 그 칸의 값 변화를 보여 준다.
 * 우리는 셀마다 변경 로그를 이미 쌓고 있어 읽어 주기만 하면 된다(2026-08-20).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CellHistoryTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private WorkbookMapper workbookMapper;

    @Autowired
    private SheetMapper sheetMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private Authenticator authenticator;

    @MockitoBean
    private UserDirectory userDirectory;

    private Long sheetSeq;

    @BeforeEach
    void 문서를_만들고_로그인한다() {
        DbTestSupport.deleteAll(jdbcTemplate);
        loggedInAs("me");
        when(userDirectory.findByKeys(any())).thenReturn(List.of(
                new DirectoryUser("me", "me-id", "나", "책임", "D1", "전산팀")));

        WorkbookDto workbook = new WorkbookDto(null, "문서", "me", null, null);
        workbookMapper.insertWorkbook(workbook, "me");
        SheetSummaryDto sheet =
                new SheetSummaryDto(null, workbook.workbookSeq(), "시트1", 0, 0, 0);
        sheetMapper.insertSheet(sheet, "me");
        sheetSeq = sheet.sheetSeq();
    }

    @Test
    void 그_칸의_값_변화를_최근_것부터_준다() throws Exception {
        저장한다("{\"rowNo\":0,\"columnNo\":0,\"valueType\":\"STRING\",\"textValue\":\"처음\"}");
        저장한다("{\"rowNo\":0,\"columnNo\":0,\"valueType\":\"STRING\",\"textValue\":\"고침\"}");

        mockMvc.perform(get("/api/sheets/{seq}/cells/{row}/{col}/history", sheetSeq, 0, 0))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].changeType").value("UPDATE"))
                .andExpect(jsonPath("$[0].before").value("처음"))
                .andExpect(jsonPath("$[0].after").value("고침"))
                .andExpect(jsonPath("$[0].changedBy").value("나 책임"))
                .andExpect(jsonPath("$[0].changedAt").isNotEmpty())
                .andExpect(jsonPath("$[1].changeType").value("INSERT"))
                .andExpect(jsonPath("$[1].after").value("처음"));
    }

    @Test
    void 다른_칸의_기록은_섞이지_않는다() throws Exception {
        저장한다("{\"rowNo\":0,\"columnNo\":0,\"valueType\":\"STRING\",\"textValue\":\"가\"}");
        저장한다("{\"rowNo\":5,\"columnNo\":5,\"valueType\":\"STRING\",\"textValue\":\"나\"}");

        mockMvc.perform(get("/api/sheets/{seq}/cells/{row}/{col}/history", sheetSeq, 5, 5))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].after").value("나"));
    }

    @Test
    void 수식은_등호를_붙여_보인다() throws Exception {
        저장한다("{\"rowNo\":1,\"columnNo\":1,\"valueType\":\"NUMBER\",\"numberValue\":3,"
                + "\"formulaValue\":\"A1+A2\"}");

        mockMvc.perform(get("/api/sheets/{seq}/cells/{row}/{col}/history", sheetSeq, 1, 1))
                .andExpect(jsonPath("$[0].after").value("=A1+A2"));
    }

    @Test
    void 사번은_기록에_나오지_않는다() throws Exception {
        저장한다("{\"rowNo\":0,\"columnNo\":0,\"valueType\":\"STRING\",\"textValue\":\"가\"}");

        String body = mockMvc.perform(
                        get("/api/sheets/{seq}/cells/{row}/{col}/history", sheetSeq, 0, 0))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("\"me\"");
    }

    @Test
    void 권한이_없으면_기록도_없다() throws Exception {
        저장한다("{\"rowNo\":0,\"columnNo\":0,\"valueType\":\"STRING\",\"textValue\":\"가\"}");
        loggedInAs("stranger");

        mockMvc.perform(get("/api/sheets/{seq}/cells/{row}/{col}/history", sheetSeq, 0, 0))
                .andExpect(status().isForbidden());
    }

    private void 저장한다(String cellJson) throws Exception {
        mockMvc.perform(patch("/api/sheets/{sheetSeq}/cells", sheetSeq)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"changed\":[" + cellJson + "]}"))
                .andExpect(status().isOk());
    }

    private void loggedInAs(String userKey) {
        when(authenticator.authenticate(any(), any()))
                .thenReturn(new LoginUser(userKey, userKey, userKey, "사원", "D100", "전산팀", "ko"));
    }
}
