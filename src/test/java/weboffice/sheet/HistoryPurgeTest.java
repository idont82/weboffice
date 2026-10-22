package weboffice.sheet;

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
import weboffice.support.DbTestSupport;
import weboffice.workbook.DocType;
import weboffice.workbook.WorkbookService;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 오래된 편집 이력 지우기.
 *
 * <p>이력은 늘기만 하고 줄지 않아 넉 주 만에 1GB 가 됐다(2026-09-04 실측).
 */
@SpringBootTest
@ActiveProfiles("test")
class HistoryPurgeTest {

    @Autowired
    private HistoryPurgeService purgeService;

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

    private Long sheetSeq;

    @BeforeEach
    void 문서를_하나_만든다() {
        DbTestSupport.deleteAll(jdbcTemplate);
        WorkbookService.CreatedWorkbook created =
                workbookService.createBlank("이력 시험", DocType.SHEET, ME);
        sheetSeq = created.sheets().get(0).sheetSeq();
    }

    @Test
    void 오래된_이력을_지운다() {
        고친다("가");
        이력을_옛날로_돌린다(40);

        purgeService.purgeOld();

        assertThat(셀_이력_수()).isZero();
    }

    @Test
    void 최근_이력은_남긴다() {
        // 이력은 "누가 언제 무엇을 고쳤나" 를 최근 것만 보여 주면 된다.
        고친다("가");

        purgeService.purgeOld();

        assertThat(셀_이력_수()).isEqualTo(1);
    }

    @Test
    void 보관_기간_안쪽은_남기고_바깥쪽만_지운다() {
        고친다("옛것");
        이력을_옛날로_돌린다(40);
        고친다("새것");

        purgeService.purgeOld();

        assertThat(셀_이력_수()).isEqualTo(1);
    }

    @Test
    void 셀은_안_건드린다() {
        // 이력을 지우는 것이지 값을 지우는 것이 아니다.
        고친다("가");
        이력을_옛날로_돌린다(40);

        purgeService.purgeOld();

        assertThat(sheetService.readCells(sheetSeq, new CellRange(0, 0, 5, 5))).hasSize(1);
    }

    @Test
    void 지울_것이_없으면_아무_일도_없다() {
        purgeService.purgeOld();

        assertThat(셀_이력_수()).isZero();
    }

    private void 고친다(String value) {
        sheetService.saveDelta(sheetSeq,
                List.of(new CellDto(0, 0, CellDto.ValueType.STRING, value,
                        (BigDecimal) null, null, null, null, null)), List.of(), ME);
    }

    /** 이력에 적힌 시각을 뒤로 물린다. 지우개가 보는 것이 그 시각이다. */
    private void 이력을_옛날로_돌린다(int days) {
        jdbcTemplate.update("UPDATE wo_cell_change_log"
                + " SET INPUT_DATETIME = DATEADD('DAY', ?, INPUT_DATETIME)", -days);
    }

    private Integer 셀_이력_수() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM wo_cell_change_log", Integer.class);
    }
}
