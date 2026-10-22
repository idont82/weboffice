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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 보던 시트와 배율.
 *
 * <p>엑셀처럼 문서를 다시 열면 보던 시트로, 그 시트를 보던 배율로 연다. 브라우저에
 * 두면 다른 PC 에서 기억이 없다 - 계정을 따라가야 한다(2026-08-13 지적).
 *
 * <p>개인 것이다. 남이 본 자리가 내 화면을 끌고 다니면 안 된다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SheetViewTest {

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
    private Long firstSheet;
    private Long secondSheet;

    @BeforeEach
    void 문서를_만들고_로그인한다() {
        DbTestSupport.deleteAll(jdbcTemplate);
        WorkbookDto workbook = new WorkbookDto(null, "문서", "me", null, null);
        workbookMapper.insertWorkbook(workbook, "me");
        workbookSeq = workbook.workbookSeq();
        memberMapper.insertMember(workbookSeq, "other", "EDITOR", "me");

        firstSheet = 시트를_만든다("시트1", 0);
        secondSheet = 시트를_만든다("시트2", 1);

        loggedInAs("me");
    }

    @Test
    void 본_적_없으면_아무것도_없다() throws Exception {
        mockMvc.perform(get("/api/workbooks/{seq}/view", workbookSeq))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastSheetSeq").doesNotExist())
                .andExpect(jsonPath("$.zooms.length()").value(0));
    }

    @Test
    void 마지막으로_본_시트를_돌려준다() throws Exception {
        본다(firstSheet, null);
        본다(secondSheet, null);

        mockMvc.perform(get("/api/workbooks/{seq}/view", workbookSeq))
                .andExpect(jsonPath("$.lastSheetSeq").value(secondSheet));
    }

    @Test
    void 시트마다_배율을_따로_담는다() throws Exception {
        본다(firstSheet, 1.5);
        본다(secondSheet, 0.8);

        mockMvc.perform(get("/api/workbooks/{seq}/view", workbookSeq))
                .andExpect(jsonPath("$.zooms." + firstSheet).value(1.5))
                .andExpect(jsonPath("$.zooms." + secondSheet).value(0.8));
    }

    @Test
    void 배율을_안_보내면_앞서_담은_배율을_지킨다() throws Exception {
        // 시트를 옮기는 것만으로 배율이 1 로 되돌아가면 안 된다.
        본다(firstSheet, 1.5);
        본다(firstSheet, null);

        mockMvc.perform(get("/api/workbooks/{seq}/view", workbookSeq))
                .andExpect(jsonPath("$.zooms." + firstSheet).value(1.5));
    }

    @Test
    void 남이_본_자리는_보이지_않는다() throws Exception {
        본다(firstSheet, 1.5);

        loggedInAs("other");

        mockMvc.perform(get("/api/workbooks/{seq}/view", workbookSeq))
                .andExpect(jsonPath("$.lastSheetSeq").doesNotExist())
                .andExpect(jsonPath("$.zooms.length()").value(0));
    }

    @Test
    void 읽을_수_없는_문서는_거절한다() throws Exception {
        loggedInAs("stranger");

        mockMvc.perform(get("/api/workbooks/{seq}/view", workbookSeq))
                .andExpect(status().isForbidden());
    }

    private void 본다(Long sheetSeq, Double zoom) throws Exception {
        String body = zoom == null
                ? "{\"sheetSeq\":%d}".formatted(sheetSeq)
                : "{\"sheetSeq\":%d,\"zoom\":%s}".formatted(sheetSeq, zoom);
        mockMvc.perform(put("/api/workbooks/{seq}/view", workbookSeq)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
    }

    private Long 시트를_만든다(String name, int sheetNo) {
        SheetSummaryDto sheet = new SheetSummaryDto(null, workbookSeq, name, sheetNo, 10, 5);
        sheetMapper.insertSheet(sheet, "me");
        return sheet.sheetSeq();
    }

    /** 인터셉터가 SSO 로 얻는 사용자를 갈아끼운다. SSO 서버가 필요 없다. */
    private void loggedInAs(String userKey) {
        when(authenticator.authenticate(any(), any()))
                .thenReturn(new LoginUser(userKey, userKey, userKey, "사원", "D100", "전산팀", "ko"));
    }
}
