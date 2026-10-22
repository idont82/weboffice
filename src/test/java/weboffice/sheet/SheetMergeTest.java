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
 * 병합 범위.
 *
 * <p>표({@code wo_sheet_merge_line})와 매퍼는 처음부터 있었는데 <b>xlsx 입출력에만
 * 이어져 있었다.</b> 화면 쪽 읽기·쓰기 경로가 없어 두 방향이 다 끊겼다 - 엑셀에서
 * 병합된 파일을 올려도 화면에 안 나오고, 화면에서 병합해도 새로고침하면 풀렸다
 * (2026-08-07 실측).
 *
 * <p>크기와 달리 델타가 아니라 통째로 바꾼다. 병합은 시트당 많아야 수백 개고,
 * 하나를 풀면 그 자리에 다른 병합이 생기는 식이라 "바뀐 것" 을 가려내는 쪽이
 * 오히려 어긋나기 쉽다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SheetMergeTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private WorkbookMapper workbookMapper;

    @Autowired
    private MemberMapper memberMapper;

    @Autowired
    private SheetMapper sheetMapper;

    @Autowired
    private MergeMapper mergeMapper;

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
    void 병합을_저장하고_돌려준다() throws Exception {
        저장한다("""
                {"merges":[{"fromRowNo":7,"fromColumnNo":0,"toRowNo":7,"toColumnNo":2},
                           {"fromRowNo":0,"fromColumnNo":0,"toRowNo":2,"toColumnNo":0}]}""");

        mockMvc.perform(get("/api/sheets/{seq}/merges", sheetSeq))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                // FROM_ROW_NO, FROM_COLUMN_NO 오름차순이다.
                .andExpect(jsonPath("$[0].fromRowNo").value(0))
                .andExpect(jsonPath("$[0].toRowNo").value(2))
                .andExpect(jsonPath("$[1].fromRowNo").value(7))
                .andExpect(jsonPath("$[1].toColumnNo").value(2));
    }

    @Test
    void 다시_저장하면_통째로_갈아치운다() throws Exception {
        // 쌓이면 푼 병합이 되살아난다.
        저장한다("""
                {"merges":[{"fromRowNo":0,"fromColumnNo":0,"toRowNo":0,"toColumnNo":3}]}""");

        저장한다("""
                {"merges":[{"fromRowNo":5,"fromColumnNo":1,"toRowNo":5,"toColumnNo":2}]}""");

        mockMvc.perform(get("/api/sheets/{seq}/merges", sheetSeq))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].fromRowNo").value(5));
    }

    @Test
    void 전부_풀면_빈_목록이_된다() throws Exception {
        저장한다("""
                {"merges":[{"fromRowNo":0,"fromColumnNo":0,"toRowNo":0,"toColumnNo":3}]}""");

        저장한다("{\"merges\":[]}");

        mockMvc.perform(get("/api/sheets/{seq}/merges", sheetSeq))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void 뒤집힌_범위는_거부한다() throws Exception {
        // to 가 from 보다 작으면 그리는 쪽이 음수 크기를 받는다.
        mockMvc.perform(put("/api/sheets/{seq}/merges", sheetSeq)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"merges":[{"fromRowNo":5,"fromColumnNo":0,
                                            "toRowNo":2,"toColumnNo":3}]}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 한_칸짜리_범위는_거부한다() throws Exception {
        // 한 칸은 병합이 아니다. 남겨 두면 푼 자리가 표에 계속 쌓인다.
        mockMvc.perform(put("/api/sheets/{seq}/merges", sheetSeq)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"merges":[{"fromRowNo":1,"fromColumnNo":1,
                                            "toRowNo":1,"toColumnNo":1}]}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 읽기_전용_사용자는_바꾸지_못한다() throws Exception {
        loggedInAs("viewer1");

        mockMvc.perform(put("/api/sheets/{seq}/merges", sheetSeq)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"merges":[{"fromRowNo":0,"fromColumnNo":0,
                                            "toRowNo":0,"toColumnNo":2}]}"""))
                .andExpect(status().isForbidden());
    }

    @Test
    void 읽기_전용_사용자도_병합은_볼_수_있다() throws Exception {
        저장한다("""
                {"merges":[{"fromRowNo":0,"fromColumnNo":0,"toRowNo":0,"toColumnNo":2}]}""");

        loggedInAs("viewer1");

        mockMvc.perform(get("/api/sheets/{seq}/merges", sheetSeq))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void 엑셀에서_가져온_병합이_화면으로_나간다() throws Exception {
        // 이것이 끊겨 있던 방향이다. xlsx 가져오기는 처음부터 표에 넣고 있었는데
        // 화면이 읽을 길이 없어 병합이 없는 것처럼 보였다(2026-08-07 실측).
        mergeMapper.insertMerges(sheetSeq,
                java.util.List.of(new weboffice.excel.MergeRangeDto(3, 1, 3, 4)),
                "me");

        mockMvc.perform(get("/api/sheets/{seq}/merges", sheetSeq))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].fromColumnNo").value(1))
                .andExpect(jsonPath("$[0].toColumnNo").value(4));
    }

    @Test
    void 갈아치워도_지운_행이_쌓이지_않고_되살아난다() throws Exception {
        // 소프트 삭제라 같은 범위를 다시 넣으면 행이 둘이 된다. 조회가 중복을 주면
        // 그리는 쪽이 같은 자리를 두 번 그린다.
        저장한다("""
                {"merges":[{"fromRowNo":0,"fromColumnNo":0,"toRowNo":0,"toColumnNo":2}]}""");
        저장한다("""
                {"merges":[{"fromRowNo":0,"fromColumnNo":0,"toRowNo":0,"toColumnNo":2}]}""");

        mockMvc.perform(get("/api/sheets/{seq}/merges", sheetSeq))
                .andExpect(jsonPath("$.length()").value(1));

        assertThat(mergeMapper.selectMerges(sheetSeq)).hasSize(1);
    }

    private void 저장한다(String body) throws Exception {
        mockMvc.perform(put("/api/sheets/{seq}/merges", sheetSeq)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    private void loggedInAs(String userKey) {
        when(authenticator.authenticate(any(), any()))
                .thenReturn(new LoginUser(userKey + "-id", userKey, userKey, "사원", "D1", "전산팀", "ko"));
    }
}
