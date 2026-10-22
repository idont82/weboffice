package weboffice.lock;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import weboffice.sheet.CellRange;
import weboffice.sheet.SheetMapper;
import weboffice.sheet.SheetSummaryDto;
import weboffice.support.DbTestSupport;
import weboffice.workbook.MemberMapper;
import weboffice.workbook.WorkbookDto;
import weboffice.workbook.WorkbookMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 스펙 6절의 판정 기준: 스레드 2개가 같은 범위를 요청하면 하나만 성공한다. 100회 반복.
 *
 * <p>이것은 H2 결과다. PostgreSQL 에서의 FOR UPDATE 직렬화는 따로 확인해야 한다 -
 * H2 통과가 다른 벤더 통과의 증거가 아니라는 규칙이 여기에도 적용된다.
 */
@SpringBootTest
@ActiveProfiles("test")
class LockConcurrencyTest {

    private static final int ROUNDS = 100;

    @Autowired
    private LockService lockService;

    @Autowired
    private SheetMapper sheetMapper;

    @Autowired
    private WorkbookMapper workbookMapper;

    @Autowired
    private MemberMapper memberMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void 같은_범위를_동시에_요청해도_둘_다_성공한다() throws Exception {
        DbTestSupport.deleteAll(jdbcTemplate);
        WorkbookDto workbook = new WorkbookDto(null, "동시성 문서", "owner1", null, null);
        workbookMapper.insertWorkbook(workbook, "owner1");
        memberMapper.insertMember(workbook.workbookSeq(), "owner2", "EDITOR", "owner1");

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < ROUNDS; round++) {
                // 회차마다 시트를 새로 만든다. 같은 시트를 재사용하면 앞 회차의
                // 잠금이 남아 두 번째 회차부터는 둘 다 실패해 통과해 버린다.
                SheetSummaryDto sheet = new SheetSummaryDto(
                        null, workbook.workbookSeq(), "시트" + round, round, 0, 0);
                sheetMapper.insertSheet(sheet, "owner1");
                Long sheetSeq = sheet.sheetSeq();

                CyclicBarrier gate = new CyclicBarrier(2);
                // 사번을 나눈다. 같은 사람이면 앞의 자리를 지우는 쪽이 걸려 결과가 흐려진다.
                java.util.concurrent.atomic.AtomicInteger seat =
                        new java.util.concurrent.atomic.AtomicInteger();
                Callable<Boolean> attempt = () -> {
                    String userKey = "owner" + (seat.getAndIncrement() % 2 + 1);
                    gate.await();
                    try {
                        lockService.acquire(sheetSeq, new CellRange(0, 0, 9, 9), userKey);
                        return true;
                    } catch (RuntimeException e) {
                        return false;
                    }
                };

                List<Future<Boolean>> results = pool.invokeAll(List.of(attempt, attempt));
                long succeeded = 0;
                for (Future<Boolean> result : results) {
                    if (result.get()) {
                        succeeded++;
                    }
                }

                assertThat(succeeded)
                        .as("%d 회차: 같은 범위라도 둘 다 자리를 잡아야 한다. 표시는 막지 않는다", round)
                        .isEqualTo(2);
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
