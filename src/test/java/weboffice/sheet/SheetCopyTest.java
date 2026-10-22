package weboffice.sheet;

import java.math.BigDecimal;
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

import weboffice.common.AuditUser;
import weboffice.drawing.DrawingDto;
import weboffice.drawing.DrawingService;
import weboffice.excel.CellDto;
import weboffice.excel.MergeRangeDto;
import weboffice.security.UserDirectory;
import weboffice.security.LoginUser;
import weboffice.security.Authenticator;
import weboffice.support.DbTestSupport;
import weboffice.workbook.DocType;
import weboffice.workbook.MemberMapper;
import weboffice.workbook.WorkbookService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 시트 복사.
 *
 * <p>값만 옮기면 복사가 아니다 - 표를 그대로 두고 숫자만 바꿔 보려고 복사한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SheetCopyTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SheetCopyService copyService;

    @Autowired
    private WorkbookService workbookService;

    @Autowired
    private SheetMapper sheetMapper;

    @Autowired
    private SheetService sheetService;

    @Autowired
    private SheetLifecycleService lifecycleService;

    @Autowired
    private MergeService mergeService;

    @Autowired
    private SheetSizeService sheetSizeService;

    @Autowired
    private DrawingService drawingService;

    @Autowired
    private MemberMapper memberMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private Authenticator authenticator;

    @MockitoBean
    private UserDirectory userDirectory;

    private static final AuditUser ME = AuditUser.of("me");

    private Long workbookSeq;
    private Long sheetSeq;

    @BeforeEach
    void 문서를_하나_만든다() {
        DbTestSupport.deleteAll(jdbcTemplate);
        loggedInAs("me");
        WorkbookService.CreatedWorkbook created =
                workbookService.createBlank("복사 시험", DocType.SHEET, ME);
        workbookSeq = created.workbookSeq();
        sheetSeq = created.sheets().get(0).sheetSeq();
    }

    @Test
    void 값을_그대로_옮긴다() {
        sheetService.saveDelta(sheetSeq,
                List.of(text(0, 0, "가"), text(2, 3, "나")), List.of(), ME);

        SheetSummaryDto made = copyService.copy(sheetSeq, null, ME);

        assertThat(valuesOf(made.sheetSeq())).containsExactly("가", "나");
    }

    @Test
    void 서식도_함께_간다() {
        // 표를 그대로 두고 숫자만 바꾸려고 복사한다. 서식이 빠지면 복사가 아니다.
        sheetService.saveDelta(sheetSeq, List.of(styled(0, 0, "제목", "{\"bl\":1}")),
                List.of(), ME);

        SheetSummaryDto made = copyService.copy(sheetSeq, null, ME);

        assertThat(cellsOf(made.sheetSeq()).get(0).styleValue()).isEqualTo("{\"bl\":1}");
    }

    @Test
    void 병합과_크기와_그림도_간다() {
        mergeService.replaceAll(sheetSeq, List.of(new MergeRangeDto(0, 0, 1, 1)), ME);
        sheetSizeService.saveDelta(sheetSeq, List.of(new SheetSizeDto("ROW", 2, 40)),
                List.of(), ME);
        drawingService.saveDelta(sheetSeq, List.of(drawing("d1", 이미지를_넣는다())), List.of(), ME);

        SheetSummaryDto made = copyService.copy(sheetSeq, null, ME);

        assertThat(mergeService.read(made.sheetSeq()))
                .containsExactly(new MergeRangeDto(0, 0, 1, 1));
        assertThat(sheetSizeService.read(made.sheetSeq()))
                .containsExactly(new SheetSizeDto("ROW", 2, 40));
        assertThat(drawingService.readDrawings(made.sheetSeq()))
                .extracting(DrawingDto::drawingId).containsExactly("d1");
    }

    @Test
    void 틀고정과_눈금선과_탭색과_필터도_간다() {
        lifecycleService.setFreeze(sheetSeq, 1, 2, ME);
        lifecycleService.setGridlines(sheetSeq, false, ME);
        lifecycleService.setTabColor(sheetSeq, "#ff0000", ME);
        lifecycleService.setFilterRange(sheetSeq, "A1:C9", ME);

        SheetSummaryDto made = copyService.copy(sheetSeq, null, ME);

        assertThat(made.freezeRowNo()).isEqualTo(1);
        assertThat(made.freezeColumnNo()).isEqualTo(2);
        assertThat(made.showGridlines()).isFalse();
        assertThat(made.tabColor()).isEqualTo("#ff0000");
        assertThat(made.filterRange()).isEqualTo("A1:C9");
    }

    @Test
    void 이름을_안_주면_사본이라고_짓는다() {
        lifecycleService.rename(sheetSeq, "보고서", ME);

        assertThat(copyService.copy(sheetSeq, null, ME).sheetName()).isEqualTo("보고서 (사본)");
    }

    @Test
    void 사본이_이미_있으면_번호를_붙인다() {
        lifecycleService.rename(sheetSeq, "보고서", ME);
        copyService.copy(sheetSeq, null, ME);

        assertThat(copyService.copy(sheetSeq, null, ME).sheetName())
                .isEqualTo("보고서 (사본 2)");
    }

    @Test
    void 이름을_주면_그_이름으로_짓는다() {
        assertThat(copyService.copy(sheetSeq, "2027년 계획", ME).sheetName())
                .isEqualTo("2027년 계획");
    }

    @Test
    void 원본은_그대로_남는다() {
        sheetService.saveDelta(sheetSeq, List.of(text(0, 0, "가")), List.of(), ME);

        copyService.copy(sheetSeq, null, ME);

        assertThat(valuesOf(sheetSeq)).containsExactly("가");
        assertThat(sheetMapper.selectSheetsByWorkbook(workbookSeq)).hasSize(2);
    }

    @Test
    void 사본을_고쳐도_원본은_안_바뀐다() {
        // 같은 줄을 가리키기만 하면 복사가 아니라 별칭이 된다.
        sheetService.saveDelta(sheetSeq, List.of(text(0, 0, "원본")), List.of(), ME);
        SheetSummaryDto made = copyService.copy(sheetSeq, null, ME);

        sheetService.saveDelta(made.sheetSeq(), List.of(text(0, 0, "사본")), List.of(), ME);

        assertThat(valuesOf(sheetSeq)).containsExactly("원본");
        assertThat(valuesOf(made.sheetSeq())).containsExactly("사본");
    }

    @Test
    void API_로_복사한다() throws Exception {
        sheetService.saveDelta(sheetSeq, List.of(text(0, 0, "가")), List.of(), ME);

        mockMvc.perform(post("/api/sheets/{seq}/copy", sheetSeq)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sheetName").value("Sheet1 (사본)"));
    }

    @Test
    void 볼_수만_있는_사람은_복사하지_못한다() throws Exception {
        // 사본도 문서에 남는다. 읽기 권한으로 시트를 늘릴 수는 없다.
        memberMapper.insertMember(workbookSeq, "guest", "VIEWER", "me");
        loggedInAs("guest");

        mockMvc.perform(post("/api/sheets/{seq}/copy", sheetSeq)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    private static CellDto text(int rowNo, int columnNo, String value) {
        return new CellDto(rowNo, columnNo, CellDto.ValueType.STRING, value,
                (BigDecimal) null, null, null, null, null);
    }

    private static CellDto styled(int rowNo, int columnNo, String value, String style) {
        return new CellDto(rowNo, columnNo, CellDto.ValueType.STRING, value,
                (BigDecimal) null, null, null, null, style);
    }

    private static DrawingDto drawing(String drawingId, Long imageSeq) {
        return new DrawingDto(drawingId, imageSeq, 0, 0, 0, 0, 3, 0, 3, 0, "TWO_CELL", 0, null);
    }

    private Long 이미지를_넣는다() {
        jdbcTemplate.update("INSERT INTO wo_sheet_image_master"
                + " (WORKBOOK_SEQ, IMAGE_HASH, MIME_TYPE, IMAGE_SIZE, IMAGE_DATA,"
                + " INPUT_DATETIME, INPUT_USER)"
                + " VALUES (?, ?, 'image/png', 3, ?, CURRENT_TIMESTAMP, 'me')",
                workbookSeq, "hash-copy", new byte[] { 1, 2, 3 });
        return jdbcTemplate.queryForObject(
                "SELECT IMAGE_SEQ FROM wo_sheet_image_master WHERE IMAGE_HASH = 'hash-copy'",
                Long.class);
    }

    private List<CellDto> cellsOf(Long sheetSeq) {
        return sheetService.readCells(sheetSeq, new CellRange(0, 0, 100, 100));
    }

    private List<String> valuesOf(Long sheetSeq) {
        return cellsOf(sheetSeq).stream().map(CellDto::textValue).toList();
    }

    private void loggedInAs(String userKey) {
        when(authenticator.authenticate(any(), any()))
                .thenReturn(new LoginUser(userKey + "-id", userKey, userKey, "사원", "D1", "전산팀", "ko"));
    }
}
