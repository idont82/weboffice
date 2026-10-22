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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 문서 리소스.
 *
 * <p>Univer 의 기능 가운데 셀 값이 아닌 것 - 필터·조건부 서식·데이터 확인·메모 -
 * 은 모두 "리소스" 라는 이름의 JSON 덩어리로 오간다. 우리 표에는 그것을 담을 자리가
 * 없어서, 화면에서는 걸리는데 새로고침하면 사라졌다(2026-08-11 실측: 필터).
 *
 * <p>기능마다 표를 새로 파지 않고 이름표를 붙인 한 칸에 담는다. 다음에 조건부
 * 서식이 와도 같은 길로 간다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WorkbookResourceTest {

    private static final String FILTER = "SHEET_FILTER_PLUGIN";

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

    private Long workbookSeq;

    @BeforeEach
    void 문서를_만들고_로그인한다() {
        DbTestSupport.deleteAll(jdbcTemplate);
        WorkbookDto workbook = new WorkbookDto(null, "문서", "me", null, null);
        workbookMapper.insertWorkbook(workbook, "me");
        workbookSeq = workbook.workbookSeq();
        memberMapper.insertMember(workbookSeq, "viewer1", "VIEWER", "me");
        loggedInAs("me");
    }

    @Test
    void 저장한_것을_다시_읽는다() throws Exception {
        save(FILTER, "{\"sheet-1\":{\"ref\":{\"startRow\":0}}}");

        mockMvc.perform(get("/api/workbooks/{seq}/resources", workbookSeq))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value(FILTER))
                .andExpect(jsonPath("$[0].data").value("{\"sheet-1\":{\"ref\":{\"startRow\":0}}}"));
    }

    @Test
    void 같은_이름은_덮어쓴다() throws Exception {
        // 리소스는 늘 통째로 온다. 쌓이면 어느 것이 지금 것인지 알 수 없다.
        save(FILTER, "{\"a\":1}");
        save(FILTER, "{\"b\":2}");

        mockMvc.perform(get("/api/workbooks/{seq}/resources", workbookSeq))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].data").value("{\"b\":2}"));
    }

    @Test
    void 빈_값을_보내면_지운다() throws Exception {
        // 필터를 끄면 Univer 가 빈 것을 넘긴다. 그것을 담아 두면 다음에 열 때
        // 빈 필터가 되살아난다.
        save(FILTER, "{\"a\":1}");
        save(FILTER, "");

        mockMvc.perform(get("/api/workbooks/{seq}/resources", workbookSeq))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void 이름이_다르면_따로_쌓인다() throws Exception {
        save(FILTER, "{\"a\":1}");
        save("SHEET_CONDITION_FORMAT_PLUGIN", "{\"b\":2}");

        mockMvc.perform(get("/api/workbooks/{seq}/resources", workbookSeq))
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void 열람_권한은_저장하지_못한다() throws Exception {
        loggedInAs("viewer1");

        mockMvc.perform(put("/api/workbooks/{seq}/resources/{name}", workbookSeq, FILTER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"data\":\"{}\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void 남의_문서는_읽지_못한다() throws Exception {
        loggedInAs("stranger");

        mockMvc.perform(get("/api/workbooks/{seq}/resources", workbookSeq))
                .andExpect(status().isForbidden());
    }

    private void save(String name, String data) throws Exception {
        mockMvc.perform(put("/api/workbooks/{seq}/resources/{name}", workbookSeq, name)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"data\":" + quote(data) + "}"))
                .andExpect(status().isOk());
    }

    private String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private void loggedInAs(String userKey) {
        when(authenticator.authenticate(any(), any()))
                .thenReturn(new LoginUser(userKey, userKey, userKey, "사원", "D100", "전산팀", "ko"));
    }
}
