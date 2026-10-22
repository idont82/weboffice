package weboffice.lock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import weboffice.sheet.CellRange;
import weboffice.sheet.SheetMapper;
import weboffice.sheet.SheetSummaryDto;
import weboffice.support.DbTestSupport;
import weboffice.workbook.ForbiddenException;
import weboffice.workbook.MemberMapper;
import weboffice.workbook.WorkbookDto;
import weboffice.workbook.WorkbookMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class LockServiceTest {

    @Autowired
    private LockService lockService;

    @Autowired
    private LockMapper lockMapper;

    @Autowired
    private SheetMapper sheetMapper;

    @Autowired
    private WorkbookMapper workbookMapper;

    @Autowired
    private MemberMapper memberMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long sheetSeq;

    @BeforeEach
    void 문서와_시트를_만든다() {
        DbTestSupport.deleteAll(jdbcTemplate);
        WorkbookDto workbook = new WorkbookDto(null, "잠금 문서", "owner1", null, null);
        workbookMapper.insertWorkbook(workbook, "owner1");
        // 겹침을 시험하려면 두 번째 사람이 편집 권한을 가져야 한다.
        memberMapper.insertMember(workbook.workbookSeq(), "other1", "EDITOR", "owner1");
        SheetSummaryDto sheet =
                new SheetSummaryDto(null, workbook.workbookSeq(), "시트1", 0, 0, 0);
        sheetMapper.insertSheet(sheet, "owner1");
        sheetSeq = sheet.sheetSeq();
    }

    @Test
    void 빈_시트에서는_자리를_잡는다() {
        PresenceDto lock = lockService.acquire(sheetSeq, new CellRange(1, 1, 49, 3), "owner1");

        assertThat(lock.lockSeq()).isNotNull();
        assertThat(lock.fromRowNo()).isEqualTo(1);
        assertThat(lock.toRowNo()).isEqualTo(49);
        // 사번을 담는 자리가 아예 없다. 있으면 언젠가 채워진다.
        assertThat(lock.toString()).doesNotContain("owner1");
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM wo_sheet_lock_line
                 WHERE LOCK_SEQ = ? AND EXPIRE_DATETIME > ACQUIRE_DATETIME
                """, Integer.class, lock.lockSeq()))
                .as("TTL 이 붙지 않으면 자리가 영원히 남는다")
                .isEqualTo(1);
    }

    @Test
    void 겹치는_범위도_막지_않는다() {
        // 잠금이 아니라 표시다. 남이 보고 있다고 내가 못 쓰면 그것은 협업이 아니다.
        lockService.acquire(sheetSeq, new CellRange(0, 0, 9, 9), "owner1");

        assertThatCode(() -> lockService.acquire(sheetSeq, new CellRange(5, 5, 14, 14), "other1"))
                .doesNotThrowAnyException();
        assertThat(lockMapper.selectActiveLocks(sheetSeq)).hasSize(2);
    }

    @Test
    void 앞의_자리는_클라이언트가_지운다() {
        // 서버가 사번으로 하나만 남기면 두 창을 띄운 사람의 자리가 서로 지워진다.
        // 옮길 때 앞 자리를 푸는 것은 자리를 잡은 쪽의 일이고, 실패해도 TTL 이 회수한다.
        PresenceDto first = lockService.acquire(sheetSeq, new CellRange(0, 0, 9, 9), "owner1");
        lockService.release(first.lockSeq(), "owner1");

        lockService.acquire(sheetSeq, new CellRange(20, 20, 29, 29), "owner1");

        assertThat(lockMapper.selectActiveLocks(sheetSeq)).hasSize(1);
        assertThat(lockMapper.selectActiveLocks(sheetSeq).get(0).fromRowNo()).isEqualTo(20);
    }

    @Test
    void 해제는_소프트삭제다() {
        PresenceDto lock = lockService.acquire(sheetSeq, new CellRange(0, 0, 9, 9), "owner1");

        lockService.release(lock.lockSeq(), "owner1");

        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM wo_sheet_lock_line WHERE LOCK_SEQ = ?
                """, Integer.class, lock.lockSeq())).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT DEL_USER FROM wo_sheet_lock_line WHERE LOCK_SEQ = ?
                """, String.class, lock.lockSeq())).isEqualTo("owner1");
    }

    @Test
    void 만료된_자리는_목록에서_빠진다() {
        // 브라우저를 그냥 닫으면 해제 요청이 오지 않는다. 그 자리가 영원히 남으면
        // 아무도 없는 문서가 늘 누군가 편집 중으로 보인다.
        PresenceDto lock = lockService.acquire(sheetSeq, new CellRange(0, 0, 9, 9), "owner1");
        jdbcTemplate.update("""
                UPDATE wo_sheet_lock_line
                   SET EXPIRE_DATETIME = TIMESTAMP '2000-01-01 00:00:00'
                 WHERE LOCK_SEQ = ?
                """, lock.lockSeq());

        assertThat(lockMapper.selectActiveLocks(sheetSeq)).isEmpty();
    }

    @Test
    void 하트비트는_만료를_뒤로_민다() {
        PresenceDto lock = lockService.acquire(sheetSeq, new CellRange(0, 0, 9, 9), "owner1");
        jdbcTemplate.update("""
                UPDATE wo_sheet_lock_line
                   SET EXPIRE_DATETIME = TIMESTAMP '2000-01-01 00:00:00'
                 WHERE LOCK_SEQ = ?
                """, lock.lockSeq());
        assertThat(lockMapper.selectActiveLocks(sheetSeq)).isEmpty();

        lockService.heartbeat(lock.lockSeq(), "owner1");

        assertThat(lockMapper.selectActiveLocks(sheetSeq))
                .as("하트비트 후에는 다시 살아 있는 잠금이어야 한다")
                .hasSize(1);
    }

    @Test
    void 남의_자리는_지우지_못한다() {
        PresenceDto lock = lockService.acquire(sheetSeq, new CellRange(0, 0, 9, 9), "owner1");

        assertThatThrownBy(() -> lockService.release(lock.lockSeq(), "stranger"))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void 남의_자리에_하트비트를_보내도_연장되지_않는다() {
        PresenceDto lock = lockService.acquire(sheetSeq, new CellRange(0, 0, 9, 9), "owner1");
        jdbcTemplate.update("""
                UPDATE wo_sheet_lock_line
                   SET EXPIRE_DATETIME = TIMESTAMP '2000-01-01 00:00:00'
                 WHERE LOCK_SEQ = ?
                """, lock.lockSeq());

        lockService.heartbeat(lock.lockSeq(), "stranger");

        assertThat(lockMapper.selectActiveLocks(sheetSeq))
                .as("LOCK_USER 조건이 없으면 남이 남의 잠금을 무한 연장할 수 있다")
                .isEmpty();
    }

    @Test
    void 쓰기_권한이_없으면_자리를_잡지_못한다() {
        assertThatThrownBy(() -> lockService.acquire(sheetSeq, new CellRange(0, 0, 9, 9), "stranger"))
                .as("표시는 편집을 전제한다. 읽기 전용 사용자가 편집 중으로 보이면 안 된다")
                .isInstanceOf(ForbiddenException.class);
    }
}
