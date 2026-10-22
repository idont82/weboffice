package weboffice.snapshot;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import weboffice.common.AuditUser;
import weboffice.excel.CellDto;
import weboffice.security.UserDirectory;
import weboffice.security.Authenticator;
import weboffice.sheet.SheetService;
import weboffice.support.DbTestSupport;
import weboffice.workbook.DocType;
import weboffice.workbook.WorkbookService;

import static org.assertj.core.api.Assertions.assertThat;

/** 편집이 멎은 문서를 저절로 찍는가. */
@SpringBootTest
@ActiveProfiles("test")
class SnapshotSchedulerTest {

    @Autowired
    private SnapshotScheduler scheduler;

    @Autowired
    private SnapshotService snapshotService;

    @Autowired
    private WorkbookService workbookService;

    @Autowired
    private SheetService sheetService;

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
        WorkbookService.CreatedWorkbook created =
                workbookService.createBlank("일꾼 시험", DocType.SHEET, ME);
        workbookSeq = created.workbookSeq();
        sheetSeq = created.sheets().get(0).sheetSeq();
    }

    @Test
    void 한창_고치는_중에는_안_찍는다() {
        // 고치는 중에 찍으면 반쯤 만든 모습이 쌓인다.
        고친다("가");

        scheduler.takeAfterEditingStops();

        assertThat(snapshotService.list(workbookSeq)).isEmpty();
    }

    @Test
    void 편집이_멎으면_찍는다() {
        고친다("가");
        편집을_옛날로_돌린다(30);

        scheduler.takeAfterEditingStops();

        assertThat(snapshotService.list(workbookSeq))
                .extracting(SnapshotSummaryDto::reason).containsExactly("SESSION");
    }

    @Test
    void 이미_찍힌_판은_다시_안_찍는다() {
        // 5분마다 도는 일꾼이 같은 판을 계속 쌓으면 안 된다.
        고친다("가");
        편집을_옛날로_돌린다(30);
        scheduler.takeAfterEditingStops();

        scheduler.takeAfterEditingStops();

        assertThat(snapshotService.list(workbookSeq)).hasSize(1);
    }

    @Test
    void 찍은_뒤에_또_고치면_다시_찍는다() {
        고친다("가");
        편집을_옛날로_돌린다(60);
        scheduler.takeAfterEditingStops();
        찍은_때를_옛날로_돌린다(40);

        고친다("나");
        편집을_옛날로_돌린다(20);
        scheduler.takeAfterEditingStops();

        assertThat(snapshotService.list(workbookSeq)).hasSize(2);
    }

    @Test
    void 한_번도_안_고친_문서는_안_찍는다() {
        scheduler.takeAfterEditingStops();

        assertThat(snapshotService.list(workbookSeq)).isEmpty();
    }

    @Test
    void 지운_문서는_안_찍는다() {
        고친다("가");
        편집을_옛날로_돌린다(30);
        workbookService.delete(workbookSeq, ME);

        scheduler.takeAfterEditingStops();

        assertThat(snapshotService.list(workbookSeq)).isEmpty();
    }

    @Test
    void 하루_한_번은_고치는_중이어도_찍는다() {
        // 편집이 안 멎어 위 갈래에 안 걸린 문서를 여기서 줍는다.
        고친다("가");

        scheduler.takeDaily();

        assertThat(snapshotService.list(workbookSeq))
                .extracting(SnapshotSummaryDto::reason).containsExactly("DAILY");
    }

    private void 고친다(String value) {
        sheetService.saveDelta(sheetSeq,
                List.of(new CellDto(0, 0, CellDto.ValueType.STRING, value,
                        (BigDecimal) null, null, null, null, null)), List.of(), ME);
    }

    /** 이미 찍은 것의 시각을 뒤로 물린다. 그 뒤에 고친 것이 있는지를 일꾼이 본다. */
    private void 찍은_때를_옛날로_돌린다(int minutes) {
        jdbcTemplate.update("UPDATE wo_workbook_snapshot"
                + " SET TAKEN_AT = DATEADD('MINUTE', ?, TAKEN_AT)", -minutes);
    }

    /** 이력에 적힌 시각을 뒤로 물린다. 일꾼이 보는 것이 그 시각이다. */
    private void 편집을_옛날로_돌린다(int minutes) {
        jdbcTemplate.update("UPDATE wo_cell_change_log"
                + " SET INPUT_DATETIME = DATEADD('MINUTE', ?, INPUT_DATETIME)", -minutes);
    }
}
