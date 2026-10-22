package weboffice.lock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.config.ScheduledTaskHolder;
import org.springframework.test.context.ActiveProfiles;

import weboffice.sheet.CellRange;
import weboffice.sheet.SheetMapper;
import weboffice.sheet.SheetSummaryDto;
import weboffice.support.DbTestSupport;
import weboffice.workbook.WorkbookDto;
import weboffice.workbook.WorkbookMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@SpringBootTest
@ActiveProfiles("test")
class LockExpirySchedulerTest {

    @Autowired
    private LockExpiryScheduler scheduler;

    @Autowired
    private LockService lockService;

    @Autowired
    private LockMapper lockMapper;

    @Autowired
    private SheetMapper sheetMapper;

    @Autowired
    private WorkbookMapper workbookMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ScheduledTaskHolder scheduledTaskHolder;

    private Long sheetSeq;

    @BeforeEach
    void 문서와_시트를_만든다() {
        DbTestSupport.deleteAll(jdbcTemplate);
        WorkbookDto workbook = new WorkbookDto(null, "만료 문서", "owner1", null, null);
        workbookMapper.insertWorkbook(workbook, "owner1");
        SheetSummaryDto sheet =
                new SheetSummaryDto(null, workbook.workbookSeq(), "시트1", 0, 0, 0);
        sheetMapper.insertSheet(sheet, "owner1");
        sheetSeq = sheet.sheetSeq();
    }

    @Test
    void 만료된_잠금을_회수한다() {
        PresenceDto lock = lockService.acquire(sheetSeq, new CellRange(0, 0, 9, 9), "owner1");
        expire(lock.lockSeq());

        assertThat(scheduler.reclaimExpiredLocks()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT DEL_USER FROM wo_sheet_lock_line WHERE LOCK_SEQ = ?
                """, String.class, lock.lockSeq()))
                .as("사람이 아니라 시스템이 회수한 것을 감사 기록에 남긴다")
                .isEqualTo("SYSTEM");
    }

    @Test
    void 살아_있는_잠금은_건드리지_않는다() {
        lockService.acquire(sheetSeq, new CellRange(0, 0, 9, 9), "owner1");

        assertThat(scheduler.reclaimExpiredLocks()).isZero();
        assertThat(lockMapper.selectActiveLocks(sheetSeq)).hasSize(1);
    }

    @Test
    void 이미_해제된_잠금을_두_번_회수하지_않는다() {
        PresenceDto lock = lockService.acquire(sheetSeq, new CellRange(0, 0, 9, 9), "owner1");
        lockService.release(lock.lockSeq(), "owner1");
        expire(lock.lockSeq());

        assertThat(scheduler.reclaimExpiredLocks())
                .as("DEL_DATETIME 이 이미 찍힌 행을 다시 건드리면 해제자 기록이 SYSTEM 으로 덮인다")
                .isZero();
        assertThat(jdbcTemplate.queryForObject("""
                SELECT DEL_USER FROM wo_sheet_lock_line WHERE LOCK_SEQ = ?
                """, String.class, lock.lockSeq())).isEqualTo("owner1");
    }

    @Test
    void 회수된_범위는_다시_잠글_수_있다() {
        PresenceDto lock = lockService.acquire(sheetSeq, new CellRange(0, 0, 9, 9), "owner1");
        expire(lock.lockSeq());
        scheduler.reclaimExpiredLocks();

        assertThatCode(() -> lockService.acquire(sheetSeq, new CellRange(0, 0, 9, 9), "owner1"))
                .doesNotThrowAnyException();
    }

    @Test
    void 회수_작업이_실제로_등록돼_있다() {
        // 나머지 테스트는 스케줄러를 직접 호출한다. @EnableScheduling 이 빠지거나
        // @Scheduled 가 지워져도 전부 통과한다. 운영에서만 잠금이 영영 안 풀린다.
        assertThat(scheduledTaskHolder.getScheduledTasks())
                .as("@EnableScheduling 과 @Scheduled 중 하나라도 빠지면 등록되지 않는다")
                .anySatisfy(task -> assertThat(task.getTask().toString())
                        .contains("reclaimExpiredLocks"));
    }

    private void expire(Long lockSeq) {
        jdbcTemplate.update("""
                UPDATE wo_sheet_lock_line
                   SET EXPIRE_DATETIME = TIMESTAMP '2000-01-01 00:00:00'
                 WHERE LOCK_SEQ = ?
                """, lockSeq);
    }
}
