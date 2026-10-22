package weboffice.workbook;

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
import weboffice.security.LoginUser;
import weboffice.security.Authenticator;
import weboffice.excel.CellDto;
import weboffice.sheet.SheetService;
import weboffice.sheet.SheetMapper;
import weboffice.sheet.SheetSummaryDto;
import weboffice.support.DbTestSupport;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 목록의 미리보기.
 *
 * <p>카드에 제목 첫 글자만 있어 어느 것이 무엇인지 알 수 없었다. 구글 드라이브처럼
 * 내용이 조금 보여야 고른다.
 *
 * <p>문서마다 셀을 따로 조회하면 목록 한 번에 조회가 문서 수만큼 붙는다. 한 번에
 * 모아 온다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WorkbookPreviewTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private WorkbookService workbookService;

    @Autowired
    private SheetMapper sheetMapper;

    @Autowired
    private SheetService sheetService;

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
    void 첫_시트의_앞부분을_함께_내려준다() throws Exception {
        문서를_만든다("실적", List.of(
                cell(0, 0, "지점"), cell(0, 1, "매출"),
                cell(1, 0, "서울"), cell(1, 1, "100")));

        mockMvc.perform(get("/api/workbooks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mine[0].preview[0][0]").value("지점"))
                .andExpect(jsonPath("$.mine[0].preview[0][1]").value("매출"))
                .andExpect(jsonPath("$.mine[0].preview[1][0]").value("서울"));
    }

    @Test
    void 빈_문서는_빈_미리보기다() throws Exception {
        문서를_만든다("빈 문서", List.of());

        mockMvc.perform(get("/api/workbooks"))
                .andExpect(jsonPath("$.mine[0].preview").isArray())
                .andExpect(jsonPath("$.mine[0].preview.length()").value(0));
    }

    @Test
    void 멀리_있는_셀은_담지_않는다() throws Exception {
        // 카드에 들어갈 만큼만 본다. 다 담으면 목록 응답이 문서 하나만큼 커진다.
        문서를_만든다("먼 셀", List.of(cell(0, 0, "가까이"), cell(50, 50, "멀리")));

        mockMvc.perform(get("/api/workbooks"))
                .andExpect(jsonPath("$.mine[0].preview[0][0]").value("가까이"))
                .andExpect(jsonPath("$.mine[0].preview.length()").value(1));
    }

    @Test
    void 빈_칸은_빈_문자열로_채운다() throws Exception {
        // 줄마다 칸 수가 다르면 화면이 들쭉날쭉해진다.
        문서를_만든다("구멍", List.of(cell(0, 2, "셋째 칸")));

        mockMvc.perform(get("/api/workbooks"))
                .andExpect(jsonPath("$.mine[0].preview[0][0]").value(""))
                .andExpect(jsonPath("$.mine[0].preview[0][2]").value("셋째 칸"));
    }

    private void 문서를_만든다(String name, List<CellDto> cells) {
        AuditUser user = AuditUser.of("me");
        Long workbookSeq = workbookService.createWorkbook(name, user);
        SheetSummaryDto sheet = new SheetSummaryDto(null, workbookSeq, "Sheet1", 0, 0, 0);
        sheetMapper.insertSheet(sheet, "me");
        if (!cells.isEmpty()) {
            sheetService.saveDelta(sheet.sheetSeq(), cells, List.of(), user);
        }
    }

    private CellDto cell(int rowNo, int columnNo, String text) {
        return new CellDto(rowNo, columnNo, CellDto.ValueType.STRING, text, null, null, null, null, null);
    }
}
