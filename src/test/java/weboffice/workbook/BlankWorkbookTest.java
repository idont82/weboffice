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
import weboffice.doc.DocParagraphService;
import weboffice.excel.CellDto;
import weboffice.security.LoginUser;
import weboffice.security.Authenticator;
import weboffice.sheet.SheetService;
import weboffice.support.DbTestSupport;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * 손대지 않은 빈 문서는 파일함에 두지 않는다.
 *
 * <p>「새 시트」를 눌러 보기만 해도 파일함에 한 줄이 생겨 쌓였다(2026-08-20 지적).
 * 만드는 것은 그대로 두고 - 편집기는 문서 번호가 있어야 저장한다 - 목록에서만
 * 감춘다. 글자 한 자, 제목 한 번, 공유 한 번이면 곧바로 나온다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BlankWorkbookTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private WorkbookService workbookService;

    @Autowired
    private WorkbookMapper workbookMapper;

    @Autowired
    private MemberMapper memberMapper;

    @Autowired
    private SheetService sheetService;

    @Autowired
    private DocParagraphService docParagraphService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private Authenticator authenticator;

    @BeforeEach
    void 비우고_로그인한다() {
        DbTestSupport.deleteAll(jdbcTemplate);
        when(authenticator.authenticate(any(), any()))
                .thenReturn(new LoginUser("me", "me", "나", "사원", "D100", "전산팀", "ko"));
    }

    @Test
    void 아무것도_쓰지_않은_새_시트는_목록에_없다() throws Exception {
        빈_시트를_만든다();

        mockMvc.perform(get("/api/workbooks"))
                .andExpect(jsonPath("$.mine.length()").value(0));
    }

    @Test
    void 셀에_한_자라도_쓰면_목록에_나온다() throws Exception {
        WorkbookService.CreatedWorkbook created = 빈_시트를_만든다();

        sheetService.saveDelta(created.sheets().get(0).sheetSeq(),
                List.of(new CellDto(0, 0, CellDto.ValueType.STRING, "가", null, null, null, null, null)),
                List.of(), AuditUser.of("me"));

        mockMvc.perform(get("/api/workbooks"))
                .andExpect(jsonPath("$.mine.length()").value(1));
    }

    @Test
    void 제목을_바꾸면_목록에_나온다() throws Exception {
        WorkbookService.CreatedWorkbook created = 빈_시트를_만든다();

        workbookService.rename(created.workbookSeq(), "3분기 실적", AuditUser.of("me"));

        mockMvc.perform(get("/api/workbooks"))
                .andExpect(jsonPath("$.mine[0].workbookName").value("3분기 실적"));
    }

    @Test
    void 글_문서는_문단에_글이_있어야_나온다() throws Exception {
        WorkbookService.CreatedWorkbook created =
                workbookService.createBlank(null, DocType.DOC, AuditUser.of("me"));

        mockMvc.perform(get("/api/workbooks"))
                .andExpect(jsonPath("$.mine.length()").value(0));

        docParagraphService.save(created.workbookSeq(),
                List.of(new DocParagraphDto(0, "첫 줄", null, null)), List.of(),
                AuditUser.of("me"));

        mockMvc.perform(get("/api/workbooks"))
                .andExpect(jsonPath("$.mine.length()").value(1));
    }

    @Test
    void 빈_줄만_있는_글_문서는_그대로_감춘다() throws Exception {
        // 편집기가 처음 열릴 때 빈 문단 하나를 저장한다. 그것은 쓴 것이 아니다.
        WorkbookService.CreatedWorkbook created =
                workbookService.createBlank(null, DocType.DOC, AuditUser.of("me"));

        docParagraphService.save(created.workbookSeq(),
                List.of(new DocParagraphDto(0, "", null, null)), List.of(),
                AuditUser.of("me"));

        mockMvc.perform(get("/api/workbooks"))
                .andExpect(jsonPath("$.mine.length()").value(0));
    }

    @Test
    void 공유한_빈_문서는_보인다() throws Exception {
        // 남에게 넘겼으면 빈 것이라도 내 파일함에 있어야 거둬들일 수 있다.
        WorkbookService.CreatedWorkbook created = 빈_시트를_만든다();
        memberMapper.insertMember(created.workbookSeq(), "other", "EDITOR", "me");

        mockMvc.perform(get("/api/workbooks"))
                .andExpect(jsonPath("$.mine.length()").value(1));
    }

    @Test
    void 공개_링크를_낸_빈_문서도_보인다() throws Exception {
        WorkbookService.CreatedWorkbook created = 빈_시트를_만든다();
        workbookMapper.updateShareToken(created.workbookSeq(), "t".repeat(32), "me");

        mockMvc.perform(get("/api/workbooks"))
                .andExpect(jsonPath("$.mine.length()").value(1));
    }

    private WorkbookService.CreatedWorkbook 빈_시트를_만든다() {
        return workbookService.createBlank(null, DocType.SHEET, AuditUser.of("me"));
    }
}
