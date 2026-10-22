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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 행 높이·열 너비.
 *
 * <p>바꾼 크기가 어디에도 저장되지 않아 새로고침하면 사라지고 상대에게도 안 갔다
 * (2026-08-06 지적). 셀과 같은 델타 구조로 붙인다.
 *
 * <p>기본 크기인 줄은 저장하지 않는다. 시트 하나에 행이 백만 개일 수 있어 전부
 * 적으면 표가 터진다 - 손댄 것만 남긴다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SheetSizeTest {

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

    private Long sheetSeq;

    @BeforeEach
    void 문서를_만들고_로그인한다() {
        DbTestSupport.deleteAll(jdbcTemplate);
        WorkbookDto workbook = new WorkbookDto(null, "문서", "me", null, null);
        workbookMapper.insertWorkbook(workbook, "me");
        memberMapper.insertMember(workbook.workbookSeq(), "viewer1", "VIEWER", "me");

        SheetSummaryDto sheet =
                new SheetSummaryDto(null, workbook.workbookSeq(), "시트1", 0, 100, 20);
        sheetMapper.insertSheet(sheet, "me");
        sheetSeq = sheet.sheetSeq();

        loggedInAs("me");
    }

    @Test
    void 행_높이와_열_너비를_저장하고_돌려준다() throws Exception {
        저장한다("""
                {"changed":[{"axisType":"ROW","indexNo":3,"sizePx":48},
                            {"axisType":"COLUMN","indexNo":1,"sizePx":220}],
                 "deleted":[]}""");

        mockMvc.perform(get("/api/sheets/{seq}/sizes", sheetSeq))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                // AXIS_TYPE 오름차순이라 COLUMN 이 먼저다.
                .andExpect(jsonPath("$[0].axisType").value("COLUMN"))
                .andExpect(jsonPath("$[0].indexNo").value(1))
                .andExpect(jsonPath("$[0].sizePx").value(220))
                .andExpect(jsonPath("$[1].axisType").value("ROW"))
                .andExpect(jsonPath("$[1].indexNo").value(3))
                .andExpect(jsonPath("$[1].sizePx").value(48));
    }

    @Test
    void 같은_줄을_다시_바꾸면_덮어쓴다() throws Exception {
        // 줄마다 행이 쌓이면 어느 것이 지금 크기인지 알 수 없다.
        저장한다("""
                {"changed":[{"axisType":"ROW","indexNo":3,"sizePx":48}],"deleted":[]}""");

        저장한다("""
                {"changed":[{"axisType":"ROW","indexNo":3,"sizePx":90}],"deleted":[]}""");

        mockMvc.perform(get("/api/sheets/{seq}/sizes", sheetSeq))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].sizePx").value(90));
    }

    @Test
    void 기본으로_되돌리면_지운다() throws Exception {
        // 기본 크기인 줄까지 남기면 표가 손댄 적 없는 줄로 채워진다.
        저장한다("""
                {"changed":[{"axisType":"ROW","indexNo":3,"sizePx":48}],"deleted":[]}""");

        저장한다("""
                {"changed":[],"deleted":[{"axisType":"ROW","indexNo":3}]}""");

        mockMvc.perform(get("/api/sheets/{seq}/sizes", sheetSeq))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void 지웠다가_다시_바꾸면_되살아난다() throws Exception {
        // 소프트 삭제를 되돌리지 않으면 조회에서 사라진 채로 남는다.
        저장한다("""
                {"changed":[{"axisType":"ROW","indexNo":3,"sizePx":48}],"deleted":[]}""");
        저장한다("""
                {"changed":[],"deleted":[{"axisType":"ROW","indexNo":3}]}""");

        저장한다("""
                {"changed":[{"axisType":"ROW","indexNo":3,"sizePx":72}],"deleted":[]}""");

        mockMvc.perform(get("/api/sheets/{seq}/sizes", sheetSeq))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].sizePx").value(72));
    }

    @Test
    void 알_수_없는_축은_거부한다() throws Exception {
        // AXIS_TYPE 에 아무 문자열이나 들어가면 조회하는 쪽이 갈래를 못 나눈다.
        mockMvc.perform(put("/api/sheets/{seq}/sizes", sheetSeq)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"changed":[{"axisType":"DIAGONAL","indexNo":1,"sizePx":10}],
                                 "deleted":[]}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 크기_0_은_숨김으로_저장한다() throws Exception {
        // 0 은 "숨김" 이다. 표에 칸을 더하지 않으려고 크기 자리를 그 뜻으로 쓴다 -
        // 담을 자리가 없던 때에는 열을 숨겨도 새로고침에 되살아났다(2026-09-03 지적).
        저장한다("""
                {"changed":[{"axisType":"COLUMN","indexNo":1,"sizePx":0}],"deleted":[]}""");

        mockMvc.perform(get("/api/sheets/{seq}/sizes", sheetSeq))
                .andExpect(jsonPath("$[0].axisType").value("COLUMN"))
                .andExpect(jsonPath("$[0].indexNo").value(1))
                .andExpect(jsonPath("$[0].sizePx").value(0));
    }

    @Test
    void 숨김을_풀면_크기로_덮어쓴다() throws Exception {
        저장한다("""
                {"changed":[{"axisType":"COLUMN","indexNo":1,"sizePx":0}],"deleted":[]}""");

        저장한다("""
                {"changed":[{"axisType":"COLUMN","indexNo":1,"sizePx":120}],"deleted":[]}""");

        mockMvc.perform(get("/api/sheets/{seq}/sizes", sheetSeq))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].sizePx").value(120));
    }

    @Test
    void 크기가_음수면_거부한다() throws Exception {
        // 화면이 그릴 수 없는 크기다. 들어오면 어느 줄이 사라졌는지 찾기 어렵다.
        mockMvc.perform(put("/api/sheets/{seq}/sizes", sheetSeq)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"changed":[{"axisType":"ROW","indexNo":1,"sizePx":-5}],
                                 "deleted":[]}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 읽기_전용_사용자는_바꾸지_못한다() throws Exception {
        loggedInAs("viewer1");

        mockMvc.perform(put("/api/sheets/{seq}/sizes", sheetSeq)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"changed":[{"axisType":"ROW","indexNo":1,"sizePx":40}],
                                 "deleted":[]}"""))
                .andExpect(status().isForbidden());
    }

    @Test
    void 읽기_전용_사용자도_크기는_볼_수_있다() throws Exception {
        저장한다("""
                {"changed":[{"axisType":"COLUMN","indexNo":0,"sizePx":150}],"deleted":[]}""");

        loggedInAs("viewer1");

        mockMvc.perform(get("/api/sheets/{seq}/sizes", sheetSeq))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    private void 저장한다(String body) throws Exception {
        mockMvc.perform(put("/api/sheets/{seq}/sizes", sheetSeq)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    private void loggedInAs(String userKey) {
        when(authenticator.authenticate(any(), any()))
                .thenReturn(new LoginUser(userKey + "-id", userKey, userKey, "사원", "D1", "전산팀", "ko"));
    }

    @Test
    void 줄이_수천_개여도_한꺼번에_저장한다() throws Exception {
        // 한 문장에 다 밀어 넣으면 파라미터 상한(65,535)을 넘어 통째로 실패한다.
        // 큰 xlsx 를 올리다 여기서 터졌다(2026-08-18 지적: 파라미터 249,326개).
        StringBuilder rows = new StringBuilder();
        for (int at = 0; at < 5_000; at++) {
            rows.append(at == 0 ? "" : ",")
                .append("{\"axisType\":\"ROW\",\"indexNo\":").append(at)
                .append(",\"sizePx\":30}");
        }

        저장한다("{\"changed\":[" + rows + "],\"deleted\":[]}");

        mockMvc.perform(get("/api/sheets/{seq}/sizes", sheetSeq))
                .andExpect(jsonPath("$.length()").value(5_000));
    }
}
