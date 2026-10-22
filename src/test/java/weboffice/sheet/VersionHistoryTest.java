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
import weboffice.workbook.MemberMapper;
import weboffice.workbook.WorkbookDto;
import weboffice.workbook.WorkbookMapper;

import static com.jayway.jsonpath.JsonPath.read;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 문서 버전 기록과 되돌리기.
 *
 * <p>구글처럼 저장 시점을 따로 두지 않는다. 쌓인 편집 기록을 묶어 버전으로 보이고,
 * 고른 버전 뒤의 변경을 물려 그때 값으로 되돌린다. 되돌린 것도 기록에 남아 다시
 * 앞으로 갈 수 있다(2026-08-20).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class VersionHistoryTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private WorkbookMapper workbookMapper;

    @Autowired
    private MemberMapper memberMapper;

    @Autowired
    private SheetMapper sheetMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private Authenticator authenticator;

    @MockitoBean
    private UserDirectory userDirectory;

    private Long workbookSeq;
    private Long sheetSeq;

    @BeforeEach
    void 문서를_만들고_로그인한다() {
        DbTestSupport.deleteAll(jdbcTemplate);
        loggedInAs("me");
        when(userDirectory.findByKeys(any())).thenReturn(List.of(
                new DirectoryUser("me", "me-id", "나", "책임", "D1", "전산팀")));

        WorkbookDto workbook = new WorkbookDto(null, "문서", "me", null, null);
        workbookMapper.insertWorkbook(workbook, "me");
        workbookSeq = workbook.workbookSeq();
        SheetSummaryDto sheet = new SheetSummaryDto(null, workbookSeq, "시트1", 0, 0, 0);
        sheetMapper.insertSheet(sheet, "me");
        sheetSeq = sheet.sheetSeq();
    }

    @Test
    void 고친_기록이_버전으로_묶여_나온다() throws Exception {
        저장한다("{\"rowNo\":0,\"columnNo\":0,\"valueType\":\"STRING\",\"textValue\":\"처음\"}");
        저장한다("{\"rowNo\":0,\"columnNo\":0,\"valueType\":\"STRING\",\"textValue\":\"고침\"}");

        mockMvc.perform(get("/api/workbooks/{seq}/versions", workbookSeq))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].changeCount").value(2))
                .andExpect(jsonPath("$[0].editors[0]").value("나 책임"))
                .andExpect(jsonPath("$[0].versionId").isNumber());
    }

    @Test
    void 고른_버전_뒤의_변경을_물린다() throws Exception {
        저장한다("{\"rowNo\":0,\"columnNo\":0,\"valueType\":\"STRING\",\"textValue\":\"처음\"}");
        long version = 지금_버전();
        저장한다("{\"rowNo\":0,\"columnNo\":0,\"valueType\":\"STRING\",\"textValue\":\"고침\"}");

        mockMvc.perform(post("/api/workbooks/{seq}/versions/{version}/restore",
                        workbookSeq, version))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.restoredCount").value(1));

        mockMvc.perform(get("/api/sheets/{seq}/cells", sheetSeq)
                        .param("fromRowNo", "0").param("fromColumnNo", "0")
                        .param("toRowNo", "9").param("toColumnNo", "9"))
                .andExpect(jsonPath("$.cells[0].textValue").value("처음"));
    }

    @Test
    void 되돌린_뒤에_새로_넣은_칸은_사라진다() throws Exception {
        저장한다("{\"rowNo\":0,\"columnNo\":0,\"valueType\":\"STRING\",\"textValue\":\"처음\"}");
        long version = 지금_버전();
        저장한다("{\"rowNo\":5,\"columnNo\":5,\"valueType\":\"STRING\",\"textValue\":\"나중에 넣음\"}");

        mockMvc.perform(post("/api/workbooks/{seq}/versions/{version}/restore",
                        workbookSeq, version))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/sheets/{seq}/cells", sheetSeq)
                        .param("fromRowNo", "0").param("fromColumnNo", "0")
                        .param("toRowNo", "9").param("toColumnNo", "9"))
                .andExpect(jsonPath("$.cells.length()").value(1))
                .andExpect(jsonPath("$.cells[0].textValue").value("처음"));
    }

    @Test
    void 되돌린_것도_기록에_남는다() throws Exception {
        // 구글도 되돌리기를 되돌릴 수 있다. 되돌리기가 기록을 지우면 그 길이 막힌다.
        저장한다("{\"rowNo\":0,\"columnNo\":0,\"valueType\":\"STRING\",\"textValue\":\"처음\"}");
        long version = 지금_버전();
        저장한다("{\"rowNo\":0,\"columnNo\":0,\"valueType\":\"STRING\",\"textValue\":\"고침\"}");

        mockMvc.perform(post("/api/workbooks/{seq}/versions/{version}/restore",
                workbookSeq, version));

        mockMvc.perform(get("/api/sheets/{seq}/cells/{row}/{col}/history", sheetSeq, 0, 0))
                .andExpect(jsonPath("$[0].before").value("고침"))
                .andExpect(jsonPath("$[0].after").value("처음"));
    }

    @Test
    void 볼_수만_있는_사람은_되돌리지_못한다() throws Exception {
        저장한다("{\"rowNo\":0,\"columnNo\":0,\"valueType\":\"STRING\",\"textValue\":\"처음\"}");
        long version = 지금_버전();
        memberMapper.insertMember(workbookSeq, "viewer", "VIEWER", "me");
        loggedInAs("viewer");

        mockMvc.perform(post("/api/workbooks/{seq}/versions/{version}/restore",
                        workbookSeq, version))
                .andExpect(status().isForbidden());
    }

    @Test
    void 권한이_없으면_버전_목록도_없다() throws Exception {
        loggedInAs("stranger");

        mockMvc.perform(get("/api/workbooks/{seq}/versions", workbookSeq))
                .andExpect(status().isForbidden());
    }

    @Test
    void 버전_안에서_무엇이_바뀌었는지_보여_준다() throws Exception {
        // "고친 칸 5개" 만으로는 무엇이 바뀐 판인지 알 수 없다(2026-08-20 지적).
        저장한다("{\"rowNo\":1,\"columnNo\":2,\"valueType\":\"STRING\",\"textValue\":\"처음\"}");
        저장한다("{\"rowNo\":1,\"columnNo\":2,\"valueType\":\"STRING\",\"textValue\":\"고침\"}");

        // 잇달아 고쳤으므로 한 버전이다. 그 안에 두 번의 변화가 담긴다.
        mockMvc.perform(get("/api/workbooks/{seq}/versions/{version}/changes",
                        workbookSeq, 지금_버전()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].where").value("시트1 · C2"))
                .andExpect(jsonPath("$[0].before").value("처음"))
                .andExpect(jsonPath("$[0].after").value("고침"))
                .andExpect(jsonPath("$[0].changedBy").value("나 책임"))
                .andExpect(jsonPath("$[1].after").value("처음"));
    }

    @Test
    void 값이_그대로인_저장은_펼쳐도_안_보인다() throws Exception {
        // 화면은 같은 값을 다시 보내기도 한다. 그것까지 "바뀐 자리" 로 세면
        // 무엇이 바뀌었는지 되레 안 보인다(2026-08-20 실측: 14건 중 절반이 제자리).
        저장한다("{\"rowNo\":0,\"columnNo\":0,\"valueType\":\"STRING\",\"textValue\":\"가\"}");
        저장한다("{\"rowNo\":0,\"columnNo\":0,\"valueType\":\"STRING\",\"textValue\":\"가\"}");

        mockMvc.perform(get("/api/workbooks/{seq}/versions/{version}/changes",
                        workbookSeq, 지금_버전()))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void 없는_버전을_물으면_빈_목록이다() throws Exception {
        저장한다("{\"rowNo\":0,\"columnNo\":0,\"valueType\":\"STRING\",\"textValue\":\"가\"}");

        mockMvc.perform(get("/api/workbooks/{seq}/versions/{version}/changes", workbookSeq, 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void 권한이_없으면_버전_속도_못_본다() throws Exception {
        저장한다("{\"rowNo\":0,\"columnNo\":0,\"valueType\":\"STRING\",\"textValue\":\"가\"}");
        long version = 지금_버전();
        loggedInAs("stranger");

        mockMvc.perform(get("/api/workbooks/{seq}/versions/{version}/changes",
                        workbookSeq, version))
                .andExpect(status().isForbidden());
    }

    private long 지금_버전() throws Exception {
        Thread.sleep(20);
        String body = mockMvc.perform(get("/api/workbooks/{seq}/versions", workbookSeq))
                .andReturn().getResponse().getContentAsString();
        Number id = read(body, "$[0].versionId");
        Thread.sleep(20);
        return id.longValue();
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
