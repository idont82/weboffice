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

import weboffice.common.AuditUser;
import weboffice.doc.DocParagraphDto;
import weboffice.doc.DocParagraphMapper;
import weboffice.security.DirectoryUser;
import weboffice.security.UserDirectory;
import weboffice.security.LoginUser;
import weboffice.security.Authenticator;
import weboffice.excel.CellDto;
import weboffice.sheet.SheetMapper;
import weboffice.sheet.SheetService;
import weboffice.sheet.SheetSummaryDto;
import weboffice.support.DbTestSupport;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 문서를 이름으로도 내용으로도 찾는다. 주소도 이름도 잊은 문서를 다시 찾는 길이다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WorkbookSearchTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private WorkbookService workbookService;

    @Autowired
    private WorkbookMapper workbookMapper;

    @Autowired
    private MemberMapper memberMapper;

    @Autowired
    private SheetMapper sheetMapper;

    @Autowired
    private SheetService sheetService;

    @Autowired
    private DocParagraphMapper paragraphMapper;

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
                new DirectoryUser("me", "me-id", "나", "사원", "D1", "전산팀"),
                new DirectoryUser("other", "other-id", "홍길동", "과장", "D2", "영업팀")));
    }

    @Test
    void 이름으로_찾는다() throws Exception {
        시트를_만든다("올해 예산안", List.of());
        시트를_만든다("회의록", List.of());

        mockMvc.perform(get("/api/workbooks/search").param("q", "예산"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mine.length()").value(1))
                .andExpect(jsonPath("$.mine[0].workbookName").value("올해 예산안"))
                .andExpect(jsonPath("$.mine[0].hit").value("NAME"));
    }

    @Test
    void 시트_내용으로_찾는다() throws Exception {
        시트를_만든다("회의록", List.of(cell(0, 0, "전산팀 예산 협의")));

        mockMvc.perform(get("/api/workbooks/search").param("q", "전산팀"))
                .andExpect(jsonPath("$.mine.length()").value(1))
                .andExpect(jsonPath("$.mine[0].workbookName").value("회의록"))
                .andExpect(jsonPath("$.mine[0].hit").value("CELL"))
                .andExpect(jsonPath("$.mine[0].snippet").value(
                        org.hamcrest.Matchers.containsString("전산팀")));
    }

    @Test
    void 글_문단으로_찾는다() throws Exception {
        Long seq = 글을_만든다("보고서", "지난 분기 매출은 크게 늘었다");

        mockMvc.perform(get("/api/workbooks/search").param("q", "매출"))
                .andExpect(jsonPath("$.mine.length()").value(1))
                .andExpect(jsonPath("$.mine[0].workbookSeq").value(seq))
                .andExpect(jsonPath("$.mine[0].hit").value("PARA"));
    }

    @Test
    void 대소문자를_가리지_않는다() throws Exception {
        시트를_만든다("Budget Plan", List.of());

        mockMvc.perform(get("/api/workbooks/search").param("q", "budget"))
                .andExpect(jsonPath("$.mine.length()").value(1));
    }

    @Test
    void 남의_문서는_찾지_못한다() throws Exception {
        남의_시트를_만든다("남의 예산안", "other");

        mockMvc.perform(get("/api/workbooks/search").param("q", "예산"))
                .andExpect(jsonPath("$.mine.length()").value(0))
                .andExpect(jsonPath("$.shared.length()").value(0));
    }

    @Test
    void 공유받은_문서는_찾는다() throws Exception {
        Long shared = 남의_시트를_만든다("남의 예산안", "other");
        memberMapper.insertMember(shared, "me", "EDITOR", "other");

        mockMvc.perform(get("/api/workbooks/search").param("q", "예산"))
                .andExpect(jsonPath("$.shared.length()").value(1))
                .andExpect(jsonPath("$.shared[0].workbookSeq").value(shared));
    }

    @Test
    void 찾는_말이_비면_아무것도_주지_않는다() throws Exception {
        시트를_만든다("올해 예산안", List.of());

        mockMvc.perform(get("/api/workbooks/search").param("q", "  "))
                .andExpect(jsonPath("$.mine.length()").value(0));
    }

    @Test
    void 사번은_결과에_나오지_않는다() throws Exception {
        Long shared = 남의_시트를_만든다("남의 예산안", "other");
        memberMapper.insertMember(shared, "me", "EDITOR", "other");

        String body = mockMvc.perform(get("/api/workbooks/search").param("q", "예산"))
                .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(body).doesNotContain("\"other\"");
    }

    private Long 시트를_만든다(String name, List<CellDto> cells) {
        AuditUser user = AuditUser.of("me");
        Long workbookSeq = workbookService.createWorkbook(name, user);
        SheetSummaryDto sheet = new SheetSummaryDto(null, workbookSeq, "Sheet1", 0, 0, 0);
        sheetMapper.insertSheet(sheet, "me");
        if (!cells.isEmpty()) {
            sheetService.saveDelta(sheet.sheetSeq(), cells, List.of(), user);
        }
        return workbookSeq;
    }

    private Long 글을_만든다(String name, String text) {
        AuditUser user = AuditUser.of("me");
        Long workbookSeq = workbookService.createWorkbook(name, user);
        paragraphMapper.insertParagraphs(workbookSeq,
                List.of(new DocParagraphDto(0, text, null, null)), user.userKey());
        return workbookSeq;
    }

    private Long 남의_시트를_만든다(String name, String ownerUser) {
        WorkbookDto workbook = new WorkbookDto(null, name, ownerUser, null, null);
        workbookMapper.insertWorkbook(workbook, ownerUser);
        return workbook.workbookSeq();
    }

    private CellDto cell(int rowNo, int columnNo, String text) {
        return new CellDto(rowNo, columnNo, CellDto.ValueType.STRING, text, null, null, null, null, null);
    }

    private void loggedInAs(String userKey) {
        when(authenticator.authenticate(any(), any()))
                .thenReturn(new LoginUser(userKey + "-id", userKey, userKey, "사원", "D1", "전산팀", "ko"));
    }
}
