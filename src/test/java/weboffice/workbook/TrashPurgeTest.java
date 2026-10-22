package weboffice.workbook;

import java.math.BigDecimal;
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
import weboffice.excel.CellDto;
import weboffice.security.UserDirectory;
import weboffice.security.LoginUser;
import weboffice.security.Authenticator;
import weboffice.sheet.SheetService;
import weboffice.snapshot.SnapshotService;
import weboffice.support.DbTestSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 휴지통 비우기.
 *
 * <p>되돌릴 수 없는 일이라 "무엇을 남기는가" 를 촘촘히 본다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TrashPurgeTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TrashPurgeService purgeService;

    @Autowired
    private WorkbookService workbookService;

    @Autowired
    private WorkbookMapper workbookMapper;

    @Autowired
    private SheetService sheetService;

    @Autowired
    private SnapshotService snapshotService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private Authenticator authenticator;

    @MockitoBean
    private UserDirectory userDirectory;

    private static final AuditUser ME = AuditUser.of("me");

    @BeforeEach
    void 데이터를_비우고_로그인한다() {
        DbTestSupport.deleteAll(jdbcTemplate);
        loggedInAs("me");
    }

    @Test
    void 전부_비우면_휴지통이_빈다() {
        버린_문서를_만든다("어제 버린 것", 1);
        버린_문서를_만든다("방금 버린 것", 0);

        assertThat(purgeService.purgeAndWait(ME, 0)).isEqualTo(2);
        assertThat(workbookService.listTrash("me")).isEmpty();
    }

    @Test
    void 전부_비우기는_버린_때를_아예_안_본다() {
        // 경계를 "지금" 으로 잡으면 방금 버린 문서가 간발의 차로 빠진다. 실제로
        // 전부_비우면_휴지통이_빈다 가 셋에 한 번꼴로 깨졌다(2026-10-14 실측).
        // 시계 눈금을 기다리는 대신, 버린 때가 앞서든 나중이든 다 지우는지 본다.
        Long 나중에_버린것 = 버린_문서를_만든다("시계가 앞선 것", 0);
        jdbcTemplate.update("UPDATE wo_workbook_master"
                + " SET DEL_DATETIME = DATEADD('MINUTE', 5, CURRENT_TIMESTAMP)"
                + " WHERE WORKBOOK_SEQ = ?", 나중에_버린것);

        assertThat(purgeService.purgeAndWait(ME, 0)).isEqualTo(1);
        assertThat(남았는가(나중에_버린것)).isFalse();
    }

    @Test
    void 오늘_버린_것은_남긴다() {
        Long 어제 = 버린_문서를_만든다("어제 버린 것", 1);
        Long 오늘 = 버린_문서를_만든다("오늘 버린 것", 0);

        assertThat(purgeService.purgeAndWait(ME, 1)).isEqualTo(1);

        assertThat(workbookService.listTrash("me")).extracting(TrashRow::workbookSeq)
                .containsExactly(오늘);
        assertThat(남았는가(어제)).isFalse();
    }

    @Test
    void 사흘_안에_버린_것은_남긴다() {
        Long 이틀전 = 버린_문서를_만든다("이틀 전", 2);
        Long 닷새전 = 버린_문서를_만든다("닷새 전", 5);

        assertThat(purgeService.purgeAndWait(ME, 3)).isEqualTo(1);

        assertThat(workbookService.listTrash("me")).extracting(TrashRow::workbookSeq)
                .containsExactly(이틀전);
        assertThat(남았는가(닷새전)).isFalse();
    }

    @Test
    void 이레_안에_버린_것은_남긴다() {
        Long 닷새전 = 버린_문서를_만든다("닷새 전", 5);
        버린_문서를_만든다("열흘 전", 10);

        assertThat(purgeService.purgeAndWait(ME, 7)).isEqualTo(1);

        assertThat(workbookService.listTrash("me")).extracting(TrashRow::workbookSeq)
                .containsExactly(닷새전);
    }

    @Test
    void 안_버린_문서는_건드리지_않는다() {
        // 비우기가 살아 있는 문서를 지우면 그것이 가장 큰 사고다.
        Long 살아있는것 = 문서를_만든다("살아 있는 것");
        버린_문서를_만든다("버린 것", 1);

        purgeService.purgeAndWait(ME, 0);

        assertThat(남았는가(살아있는것)).isTrue();
    }

    @Test
    void 남의_휴지통은_건드리지_않는다() {
        Long 남의것 = 문서를_만든다("남의 문서", "other");
        workbookService.delete(남의것, AuditUser.of("other"));

        assertThat(purgeService.purgeAndWait(ME, 0)).isZero();
        assertThat(남았는가(남의것)).isTrue();
    }

    @Test
    void 문서에_딸린_것도_함께_지운다() {
        // 셀만 남고 문서가 없으면 표만 부풀고 아무도 그것을 못 지운다.
        Long seq = 버린_문서를_만든다("딸린 것이 있는 문서", 1);

        purgeService.purgeAndWait(ME, 0);

        assertThat(세어본다("wo_workbook_sheet_line", seq)).isZero();
        assertThat(세어본다("wo_workbook_snapshot", seq)).isZero();
        assertThat(세어본다("wo_user_workbook_mark", seq)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM wo_sheet_cell_line", Integer.class)).isZero();
    }

    @Test
    void API_로_비운다() throws Exception {
        버린_문서를_만든다("버린 것", 1);

        // 시작만 알리고 곧바로 돌아온다. 다 지울 때까지 기다리면 화면이 멈춘다.
        mockMvc.perform(delete("/api/workbooks/trash/purge").param("keepDays", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.startedCount").value(1));

        기다린다();
        assertThat(workbookService.listTrash("me")).isEmpty();
    }

    @Test
    void 얼마나_갔는지_물어볼_수_있다() throws Exception {
        버린_문서를_만든다("버린 것", 1);
        purgeService.purgeAndWait(ME, 0);

        mockMvc.perform(get("/api/workbooks/trash/purge"))
                .andExpect(jsonPath("$.running").value(false))
                .andExpect(jsonPath("$.done").value(1))
                .andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void 시작한_적이_없으면_하는_중이_아니다() throws Exception {
        mockMvc.perform(get("/api/workbooks/trash/purge"))
                .andExpect(jsonPath("$.running").value(false))
                .andExpect(jsonPath("$.total").value(0));
    }

    /** 뒤에서 도는 비우기가 끝나기를 기다린다. */
    private void 기다린다() {
        while (purgeService.progressOf(ME).running()) {
            try {
                Thread.sleep(20);
            } catch (InterruptedException stopped) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    @Test
    void API_는_남길_날_수를_받는다() throws Exception {
        버린_문서를_만든다("오늘 버린 것", 0);

        mockMvc.perform(delete("/api/workbooks/trash/purge").param("keepDays", "1"))
                .andExpect(jsonPath("$.startedCount").value(0));
    }

    @Test
    void 남길_날_수가_음수면_거부한다() throws Exception {
        mockMvc.perform(delete("/api/workbooks/trash/purge").param("keepDays", "-1"))
                .andExpect(status().isBadRequest());
    }

    /**
     * 표에 행이 남아 있는가.
     *
     * <p>{@code selectWorkbook} 으로는 못 본다 - 그것은 지운 표시가 있는 문서를
     * 안 준다. 버린 문서가 "정말 지워졌는지" 를 가리려면 표를 직접 세야 한다.
     */
    private boolean 남았는가(Long workbookSeq) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM wo_workbook_master WHERE WORKBOOK_SEQ = ?",
                Integer.class, workbookSeq);
        return count != null && count > 0;
    }

    private Integer 세어본다(String table, Long workbookSeq) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE WORKBOOK_SEQ = ?",
                Integer.class, workbookSeq);
    }

    private Long 문서를_만든다(String name) {
        return 문서를_만든다(name, "me");
    }

    private Long 문서를_만든다(String name, String ownerUser) {
        WorkbookDto workbook = new WorkbookDto(null, name, ownerUser, null, null);
        workbookMapper.insertWorkbook(workbook, ownerUser);
        return workbook.workbookSeq();
    }

    /** 딸린 것(시트·셀·복구 지점·별표)까지 갖춘 문서를 만들어 휴지통에 넣는다. */
    private Long 버린_문서를_만든다(String name, int daysAgo) {
        WorkbookService.CreatedWorkbook created =
                workbookService.createBlank(name, DocType.SHEET, ME);
        Long seq = created.workbookSeq();
        sheetService.saveDelta(created.sheets().get(0).sheetSeq(),
                List.of(new CellDto(0, 0, CellDto.ValueType.STRING, "값",
                        (BigDecimal) null, null, null, null, null)), List.of(), ME);
        snapshotService.take(seq, "MANUAL", null, ME);
        jdbcTemplate.update("INSERT INTO wo_user_workbook_mark"
                + " (USER_KEY, WORKBOOK_SEQ, STARRED_YN, INPUT_DATETIME, INPUT_USER)"
                + " VALUES ('me', ?, 'Y', CURRENT_TIMESTAMP, 'me')", seq);

        workbookService.delete(seq, ME);
        if (daysAgo > 0) {
            jdbcTemplate.update("UPDATE wo_workbook_master"
                    + " SET DEL_DATETIME = DATEADD('DAY', ?, DEL_DATETIME)"
                    + " WHERE WORKBOOK_SEQ = ?", -daysAgo, seq);
        }
        return seq;
    }

    private void loggedInAs(String userKey) {
        when(authenticator.authenticate(any(), any()))
                .thenReturn(new LoginUser(userKey + "-id", userKey, userKey, "사원", "D1", "전산팀", "ko"));
    }
}
