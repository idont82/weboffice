package weboffice.sheet;

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
import weboffice.workbook.MemberMapper;
import weboffice.workbook.WorkbookDto;
import weboffice.workbook.WorkbookMapper;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SheetControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private WorkbookMapper workbookMapper;

    @Autowired
    private SheetMapper sheetMapper;

    @Autowired
    private MemberMapper memberMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private Authenticator authenticator;

    private Long sheetSeq;

    @BeforeEach
    void 문서와_시트와_구성원을_만든다() {
        DbTestSupport.deleteAll(jdbcTemplate);
        WorkbookDto workbook = new WorkbookDto(null, "API 문서", "owner1", null, null);
        workbookMapper.insertWorkbook(workbook, "owner1");
        SheetSummaryDto sheet = new SheetSummaryDto(null, workbook.workbookSeq(), "시트1", 0, 0, 0);
        sheetMapper.insertSheet(sheet, "owner1");
        sheetSeq = sheet.sheetSeq();
        memberMapper.insertMember(workbook.workbookSeq(), "viewer1", "VIEWER", "owner1");
    }

    @Test
    void 소유자가_저장하면_저장한_셀_수를_돌려준다() throws Exception {
        loggedInAs("owner1");

        mockMvc.perform(patch("/api/sheets/{sheetSeq}/cells", sheetSeq)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"changed":[
                                  {"rowNo":0,"columnNo":0,"valueType":"NUMBER","numberValue":10},
                                  {"rowNo":0,"columnNo":1,"valueType":"STRING","textValue":"가"}
                                ]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.savedCount").value(2));
    }

    @Test
    void VIEWER_의_저장은_403() throws Exception {
        loggedInAs("viewer1");

        mockMvc.perform(patch("/api/sheets/{sheetSeq}/cells", sheetSeq)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"changed":[
                                  {"rowNo":0,"columnNo":0,"valueType":"NUMBER","numberValue":10}
                                ]}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void VIEWER_도_읽을_수_있다() throws Exception {
        loggedInAs("viewer1");

        mockMvc.perform(get("/api/sheets/{sheetSeq}/cells", sheetSeq)
                        .param("fromRowNo", "0").param("fromColumnNo", "0")
                        .param("toRowNo", "9").param("toColumnNo", "9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cells").isArray());
    }

    @Test
    void 저장한_셀이_그대로_되읽힌다() throws Exception {
        loggedInAs("owner1");

        mockMvc.perform(patch("/api/sheets/{sheetSeq}/cells", sheetSeq)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"changed":[
                                  {"rowNo":2,"columnNo":3,"valueType":"NUMBER",
                                   "numberValue":1234.56,"formulaValue":"A1+A2",
                                   "styleValue":"{\\"n\\":{\\"pattern\\":\\"#,##0.00\\"}}"}
                                ]}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/sheets/{sheetSeq}/cells", sheetSeq)
                        .param("fromRowNo", "0").param("fromColumnNo", "0")
                        .param("toRowNo", "9").param("toColumnNo", "9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cells.length()").value(1))
                .andExpect(jsonPath("$.cells[0].rowNo").value(2))
                .andExpect(jsonPath("$.cells[0].columnNo").value(3))
                .andExpect(jsonPath("$.cells[0].numberValue").value(1234.56))
                .andExpect(jsonPath("$.cells[0].formulaValue").value("A1+A2"))
                .andExpect(jsonPath("$.cells[0].styleNo").value(0))
                .andExpect(jsonPath("$.styles[0]")
                        .value("{\"n\":{\"pattern\":\"#,##0.00\"}}"));
    }

    @Test
    void 서식은_사전으로_한_번만_싣는다() throws Exception {
        // 같은 서식을 쓰는 셀이 수천 개다. 셀마다 서식 JSON 을 통째로 실으면
        // 한 시트가 6MB 를 넘어 여는 데만 몇 초가 걸렸다(2026-08-20 지적:
        // IT 업무 진행표 2026, 시트 9장에 41MB).
        loggedInAs("owner1");
        mockMvc.perform(patch("/api/sheets/{sheetSeq}/cells", sheetSeq)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"changed":[
                                  {"rowNo":0,"columnNo":0,"valueType":"STRING","textValue":"가",
                                   "styleValue":"{\\"bl\\":1}"},
                                  {"rowNo":0,"columnNo":1,"valueType":"STRING","textValue":"나",
                                   "styleValue":"{\\"bl\\":1}"},
                                  {"rowNo":0,"columnNo":2,"valueType":"STRING","textValue":"다"}
                                ]}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/sheets/{sheetSeq}/cells", sheetSeq)
                        .param("fromRowNo", "0").param("fromColumnNo", "0")
                        .param("toRowNo", "9").param("toColumnNo", "9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.styles.length()").value(1))
                .andExpect(jsonPath("$.styles[0]").value("{\"bl\":1}"))
                .andExpect(jsonPath("$.cells.length()").value(3))
                .andExpect(jsonPath("$.cells[0].styleNo").value(0))
                .andExpect(jsonPath("$.cells[1].styleNo").value(0))
                .andExpect(jsonPath("$.cells[2].styleNo").doesNotExist());
    }

    @Test
    void 빈_칸은_싣지_않는다() throws Exception {
        // 셀마다 numberValue·datetimeValue·booleanYn·formulaValue 가 null 로
        // 실려 덩치의 4분의 1을 차지했다.
        loggedInAs("owner1");
        mockMvc.perform(patch("/api/sheets/{sheetSeq}/cells", sheetSeq)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"changed":[
                                  {"rowNo":0,"columnNo":0,"valueType":"STRING","textValue":"가"}
                                ]}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/sheets/{sheetSeq}/cells", sheetSeq)
                        .param("fromRowNo", "0").param("fromColumnNo", "0")
                        .param("toRowNo", "9").param("toColumnNo", "9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cells[0].numberValue").doesNotExist())
                .andExpect(jsonPath("$.cells[0].formulaValue").doesNotExist());
    }

    /** 인터셉터가 SSO 로 얻는 사용자를 갈아끼운다. SSO 서버가 필요 없다. */
    private void loggedInAs(String userKey) {
        when(authenticator.authenticate(any(), any()))
                .thenReturn(new LoginUser(userKey, userKey, userKey, "사원", "D100", "전산팀", "ko"));
    }
}
