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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 시트 추가·이름변경·삭제.
 *
 * <p>화면은 시트를 여럿 보이고 오갈 수 있게 됐는데 <b>더하거나 지울 곳이 없었다</b>
 * (2026-08-07). Univer 의 시트 탭은 되는 것처럼 보이고 새로고침하면 사라졌다.
 *
 * <p>지우기는 소프트 삭제다. 시트 하나에 셀이 수만 개인데 그것까지 지우면 되돌릴
 * 방법이 없다 - 시트만 감추고 셀은 그대로 둔다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SheetLifecycleTest {

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

    private Long workbookSeq;
    private Long firstSheetSeq;

    @BeforeEach
    void 문서를_만들고_로그인한다() {
        DbTestSupport.deleteAll(jdbcTemplate);
        WorkbookDto workbook = new WorkbookDto(null, "문서", "me", null, null);
        workbookMapper.insertWorkbook(workbook, "me");
        workbookSeq = workbook.workbookSeq();
        memberMapper.insertMember(workbookSeq, "viewer1", "VIEWER", "me");

        SheetSummaryDto sheet = new SheetSummaryDto(null, workbookSeq, "Sheet1", 0, 100, 20);
        sheetMapper.insertSheet(sheet, "me");
        firstSheetSeq = sheet.sheetSeq();

        loggedInAs("me");
    }

    @Test
    void 시트를_더하면_목록_끝에_붙는다() throws Exception {
        mockMvc.perform(post("/api/workbooks/{seq}/sheets", workbookSeq)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sheetName\":\"둘째\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sheetName").value("둘째"))
                // 앞 시트가 0 이므로 1 이다. 순번이 겹치면 목록 순서가 흔들린다.
                .andExpect(jsonPath("$.sheetNo").value(1));

        mockMvc.perform(get("/api/workbooks/{seq}/sheets", workbookSeq))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[1].sheetName").value("둘째"));
    }

    @Test
    void 이름을_주지_않으면_비어_있지_않은_이름을_붙인다() throws Exception {
        // 이름 없는 탭은 누를 수는 있어도 무엇인지 알 수 없다.
        mockMvc.perform(post("/api/workbooks/{seq}/sheets", workbookSeq)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sheetName").value("Sheet2"));
    }

    @Test
    void 같은_이름은_거부한다() throws Exception {
        // 엑셀도 막는다. 허용하면 사용자가 어느 탭이 어느 것인지 모른다.
        mockMvc.perform(post("/api/workbooks/{seq}/sheets", workbookSeq)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sheetName\":\"Sheet1\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 이름을_바꾼다() throws Exception {
        mockMvc.perform(patch("/api/sheets/{seq}", firstSheetSeq)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sheetName\":\"바뀐이름\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/workbooks/{seq}/sheets", workbookSeq))
                .andExpect(jsonPath("$[0].sheetName").value("바뀐이름"));
    }

    @Test
    void 격자선은_기본으로_보인다() throws Exception {
        mockMvc.perform(get("/api/workbooks/{seq}/sheets", workbookSeq))
                .andExpect(jsonPath("$[0].showGridlines").value(true));
    }

    @Test
    void 격자선을_끄면_남는다() throws Exception {
        // 화면에서만 끄면 새로고침에 되살아난다. 엑셀도 문서에 담는 설정이다.
        mockMvc.perform(patch("/api/sheets/{seq}", firstSheetSeq)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"showGridlines\":false}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/workbooks/{seq}/sheets", workbookSeq))
                .andExpect(jsonPath("$[0].showGridlines").value(false))
                .andExpect(jsonPath("$[0].sheetName").value("Sheet1"));
    }

    @Test
    void 격자선만_보내면_이름은_그대로다() throws Exception {
        // 한 엔드포인트에 둘이 붙어 있다. 이름 없이 왔다고 이름을 지우면 안 된다.
        mockMvc.perform(patch("/api/sheets/{seq}", firstSheetSeq)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"showGridlines\":false}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/workbooks/{seq}/sheets", workbookSeq))
                .andExpect(jsonPath("$[0].sheetName").value("Sheet1"));
    }

    @Test
    void 열람_권한은_격자선을_바꾸지_못한다() throws Exception {
        loggedInAs("viewer1");

        mockMvc.perform(patch("/api/sheets/{seq}", firstSheetSeq)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"showGridlines\":false}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void 탭_색은_기본이_없다() throws Exception {
        mockMvc.perform(get("/api/workbooks/{seq}/sheets", workbookSeq))
                .andExpect(jsonPath("$[0].tabColor").doesNotExist());
    }

    @Test
    void 탭_색을_주면_남는다() throws Exception {
        // 화면에서만 칠하면 새로고침에 사라진다. 엑셀도 문서에 담는 설정이다.
        mockMvc.perform(patch("/api/sheets/{seq}", firstSheetSeq)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tabColor\":\"#c81e1e\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/workbooks/{seq}/sheets", workbookSeq))
                .andExpect(jsonPath("$[0].tabColor").value("#c81e1e"))
                .andExpect(jsonPath("$[0].sheetName").value("Sheet1"));
    }

    @Test
    void 탭_색을_빈_값으로_주면_지워진다() throws Exception {
        // 색을 지우는 길이 있어야 한다. null 은 "안 보냈다" 는 뜻이라 쓸 수 없다.
        mockMvc.perform(patch("/api/sheets/{seq}", firstSheetSeq)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tabColor\":\"#c81e1e\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/sheets/{seq}", firstSheetSeq)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tabColor\":\"\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/workbooks/{seq}/sheets", workbookSeq))
                .andExpect(jsonPath("$[0].tabColor").doesNotExist());
    }

    @Test
    void 열람_권한은_탭_색을_바꾸지_못한다() throws Exception {
        loggedInAs("viewer1");

        mockMvc.perform(patch("/api/sheets/{seq}", firstSheetSeq)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tabColor\":\"#c81e1e\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void 틀_고정은_기본이_없다() throws Exception {
        mockMvc.perform(get("/api/workbooks/{seq}/sheets", workbookSeq))
                .andExpect(jsonPath("$[0].freezeRowNo").value(0))
                .andExpect(jsonPath("$[0].freezeColumnNo").value(0));
    }

    @Test
    void 틀_고정을_걸면_남는다() throws Exception {
        // 화면에서만 걸면 새로고침에 풀린다. 엑셀도 문서에 담는 설정이다.
        mockMvc.perform(patch("/api/sheets/{seq}", firstSheetSeq)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"freezeRowNo\":2,\"freezeColumnNo\":1}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/workbooks/{seq}/sheets", workbookSeq))
                .andExpect(jsonPath("$[0].freezeRowNo").value(2))
                .andExpect(jsonPath("$[0].freezeColumnNo").value(1))
                .andExpect(jsonPath("$[0].sheetName").value("Sheet1"));
    }

    @Test
    void 틀_고정을_풀면_0이_된다() throws Exception {
        mockMvc.perform(patch("/api/sheets/{seq}", firstSheetSeq)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"freezeRowNo\":3,\"freezeColumnNo\":3}"))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/sheets/{seq}", firstSheetSeq)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"freezeRowNo\":0,\"freezeColumnNo\":0}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/workbooks/{seq}/sheets", workbookSeq))
                .andExpect(jsonPath("$[0].freezeRowNo").value(0));
    }

    @Test
    void 음수_틀_고정은_거절한다() throws Exception {
        // 음수를 담으면 화면이 Univer 에 그대로 넘겨 무엇이 될지 알 수 없다.
        mockMvc.perform(patch("/api/sheets/{seq}", firstSheetSeq)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"freezeRowNo\":-1,\"freezeColumnNo\":0}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 열람_권한은_틀_고정을_바꾸지_못한다() throws Exception {
        loggedInAs("viewer1");

        mockMvc.perform(patch("/api/sheets/{seq}", firstSheetSeq)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"freezeRowNo\":1,\"freezeColumnNo\":0}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void 빈_이름으로는_바꾸지_못한다() throws Exception {
        mockMvc.perform(patch("/api/sheets/{seq}", firstSheetSeq)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sheetName\":\"   \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 형제와_같은_이름으로는_바꾸지_못한다() throws Exception {
        Long second = 시트를_더한다("둘째");

        mockMvc.perform(patch("/api/sheets/{seq}", second)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sheetName\":\"Sheet1\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 자기_이름_그대로_바꾸는_것은_허용한다() throws Exception {
        // 이름 칸에서 고치다 말고 그대로 확정하는 일이 흔하다. 그것을 오류로 내면
        // 사용자는 무엇이 잘못됐는지 알 수 없다.
        mockMvc.perform(patch("/api/sheets/{seq}", firstSheetSeq)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sheetName\":\"Sheet1\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void 시트를_지운다() throws Exception {
        Long second = 시트를_더한다("둘째");

        mockMvc.perform(delete("/api/sheets/{seq}", second))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/workbooks/{seq}/sheets", workbookSeq))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].sheetName").value("Sheet1"));
    }

    @Test
    void 마지막_시트는_지우지_못한다() throws Exception {
        // 시트가 없는 문서는 열 수 없다. 목록에는 남는데 들어가면 빈 화면이다.
        mockMvc.perform(delete("/api/sheets/{seq}", firstSheetSeq))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 지운_시트의_셀은_남는다() throws Exception {
        // 소프트 삭제다. 시트 하나에 셀이 수만 개인데 그것까지 지우면 되돌릴 방법이
        // 없다 - 시트만 감춘다.
        Long second = 시트를_더한다("둘째");
        mockMvc.perform(patch("/api/sheets/{seq}/cells", second)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"changed":[{"rowNo":0,"columnNo":0,"valueType":"STRING",
                                             "textValue":"살아남을 값"}],"deleted":[]}"""))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/sheets/{seq}", second)).andExpect(status().isNoContent());

        Integer cells = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM wo_sheet_cell_line WHERE SHEET_SEQ = ?",
                Integer.class, second);
        assertThat(cells).isEqualTo(1);
    }

    @Test
    void 시트_순서를_바꾼다() throws Exception {
        // 탭을 끌어 옮겨도 서버에 보낼 곳이 없어 새로고침하면 되돌아왔다
        // (2026-08-07 지적).
        Long second = 시트를_더한다("둘째");
        Long third = 시트를_더한다("셋째");

        mockMvc.perform(put("/api/workbooks/{seq}/sheets/order", workbookSeq)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sheetSeqs\":[" + third + "," + firstSheetSeq
                                + "," + second + "]}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/workbooks/{seq}/sheets", workbookSeq))
                .andExpect(jsonPath("$[0].sheetName").value("셋째"))
                .andExpect(jsonPath("$[1].sheetName").value("Sheet1"))
                .andExpect(jsonPath("$[2].sheetName").value("둘째"))
                // 순번이 겹치면 목록 순서가 흔들린다. 0 부터 다시 매긴다.
                .andExpect(jsonPath("$[0].sheetNo").value(0))
                .andExpect(jsonPath("$[1].sheetNo").value(1))
                .andExpect(jsonPath("$[2].sheetNo").value(2));
    }

    @Test
    void 빠진_시트가_있으면_거부한다() throws Exception {
        // 일부만 주면 나머지 순번이 어떻게 되는지 아무도 모른다.
        시트를_더한다("둘째");

        mockMvc.perform(put("/api/workbooks/{seq}/sheets/order", workbookSeq)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sheetSeqs\":[" + firstSheetSeq + "]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 남의_시트가_섞이면_거부한다() throws Exception {
        // 다른 문서의 시트 순번을 이 문서 목록으로 덮으면 그 문서가 망가진다.
        WorkbookDto other = new WorkbookDto(null, "남의 문서", "me", null, null);
        workbookMapper.insertWorkbook(other, "me");
        SheetSummaryDto stranger =
                new SheetSummaryDto(null, other.workbookSeq(), "남의 시트", 0, 0, 0);
        sheetMapper.insertSheet(stranger, "me");

        mockMvc.perform(put("/api/workbooks/{seq}/sheets/order", workbookSeq)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sheetSeqs\":[" + stranger.sheetSeq() + "]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 읽기_전용_사용자는_순서를_바꾸지_못한다() throws Exception {
        Long second = 시트를_더한다("둘째");
        loggedInAs("viewer1");

        mockMvc.perform(put("/api/workbooks/{seq}/sheets/order", workbookSeq)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sheetSeqs\":[" + second + "," + firstSheetSeq + "]}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void 읽기_전용_사용자는_시트를_더하지_못한다() throws Exception {
        loggedInAs("viewer1");

        mockMvc.perform(post("/api/workbooks/{seq}/sheets", workbookSeq)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sheetName\":\"몰래\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void 읽기_전용_사용자는_시트를_지우지_못한다() throws Exception {
        Long second = 시트를_더한다("둘째");
        loggedInAs("viewer1");

        mockMvc.perform(delete("/api/sheets/{seq}", second))
                .andExpect(status().isForbidden());
    }

    @Test
    void 읽기_전용_사용자는_이름을_바꾸지_못한다() throws Exception {
        loggedInAs("viewer1");

        mockMvc.perform(patch("/api/sheets/{seq}", firstSheetSeq)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sheetName\":\"몰래\"}"))
                .andExpect(status().isForbidden());
    }

    private Long 시트를_더한다(String name) throws Exception {
        String body = mockMvc.perform(post("/api/workbooks/{seq}/sheets", workbookSeq)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sheetName\":\"" + name + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.parse(body).read("$.sheetSeq", Long.class);
    }

    private void loggedInAs(String userKey) {
        when(authenticator.authenticate(any(), any()))
                .thenReturn(new LoginUser(userKey + "-id", userKey, userKey, "사원", "D1", "전산팀", "ko"));
    }
    @Test
    void 걸러_보기_자리를_주면_남는다() throws Exception {
        // 화면에서만 걸러 보면 새로고침에 사라지고 내려받은 파일에도 안 남는다.
        mockMvc.perform(patch("/api/sheets/{seq}", firstSheetSeq)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"filterRange\":\"A1:D20\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/workbooks/{seq}/sheets", workbookSeq))
                .andExpect(jsonPath("$[0].filterRange").value("A1:D20"));
    }

    @Test
    void 걸러_보기를_빈_값으로_주면_지워진다() throws Exception {
        mockMvc.perform(patch("/api/sheets/{seq}", firstSheetSeq)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"filterRange\":\"A1:D20\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/sheets/{seq}", firstSheetSeq)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"filterRange\":\"\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/workbooks/{seq}/sheets", workbookSeq))
                .andExpect(jsonPath("$[0].filterRange").doesNotExist());
    }

}
