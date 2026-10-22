package weboffice.sheet;

import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 오래된 편집 이력 지우기.
 *
 * <p>이력은 늘기만 하고 줄지 않았다. 넉 주 쓴 개발 DB 에서 셀 이력이 200만 줄,
 * 1GB 였다 - 원본 셀(366만 줄, 489MB)보다 두 배 무겁다. 고치기 전·후 값을 JSON
 * 통째로 담기 때문이다(2026-09-04 실측). 사람이 늘면 그대로 불어난다.
 *
 * <p>지워도 되는 까닭은 <b>복구 지점</b>이 있어서다. 이력이 없어도 그때 문서
 * 모습은 되살릴 수 있다 - 이력은 "누가 언제 무엇을 고쳤나" 를 최근 것만 보여 주면
 * 된다.
 *
 * <p>기본 14일이다. 복구 지점이 그때 문서 모습을 들고 있으므로 이력은 "요즘 누가
 * 무엇을 고쳤나" 만 보여 주면 된다 - 2주면 그 물음에 넉넉하다(2026-09-04 결정).
 * {@code weboffice.history.keep-days} 로 늘리거나 줄인다.
 */
@Service
public class HistoryPurgeService {

    private static final Logger log = LoggerFactory.getLogger(HistoryPurgeService.class);

    /**
     * 한 번에 지울 줄 수.
     *
     * <p>백만 줄을 한 문장으로 지우면 그동안 표가 잠기고 WAL 이 부푼다. 나눠 지우면
     * 사이사이 다른 일이 끼어들 수 있다.
     */
    private static final int CHUNK = 20_000;

    /** 한 밤에 도는 횟수 상한. 남은 것은 다음 밤에 이어 지운다. */
    private static final int MOST_ROUNDS = 200;

    private final HistoryPurgeMapper purgeMapper;
    private final int keepDays;

    public HistoryPurgeService(HistoryPurgeMapper purgeMapper,
                               @Value("${weboffice.history.keep-days:14}") int keepDays) {
        this.purgeMapper = purgeMapper;
        this.keepDays = keepDays;
    }

    /** 새벽 네 시. 복구 지점 찍기(세 시)가 끝난 뒤다. */
    @Scheduled(cron = "${weboffice.history.purge-cron:0 0 4 * * *}")
    public void purgeOld() {
        if (keepDays <= 0) {
            // 0 이면 끄는 것으로 읽는다. 실수로 0 이 들어와 이력이 통째로 사라지면 안 된다.
            return;
        }
        LocalDateTime before = LocalDateTime.now().minusDays(keepDays);
        long startedAt = System.currentTimeMillis();

        int cells = purgeAll(true, before);
        int docs = purgeAll(false, before);

        if (cells + docs > 0) {
            log.info("{}일 지난 편집 이력을 지웠습니다(셀 {}줄, 문단 {}줄, {}ms)",
                    keepDays, cells, docs, System.currentTimeMillis() - startedAt);
        }
    }

    /**
     * 다 지울 때까지 나눠 돈다.
     *
     * @param cellSide 셀 이력이면 참, 문단 이력이면 거짓
     */
    private int purgeAll(boolean cellSide, LocalDateTime before) {
        int total = 0;
        for (int round = 0; round < MOST_ROUNDS; round += 1) {
            int removed = purgeChunk(cellSide, before);
            total += removed;
            if (removed < CHUNK) {
                break;
            }
        }
        return total;
    }

    /**
     * 한 묶음.
     *
     * <p>{@code @Transactional} 을 안 붙인다. 문장 하나가 그대로 한 트랜잭션이고,
     * 붙여도 같은 빈 안에서 부르면 프록시를 안 타 아무 일도 안 한다 - 묶음마다
     * 끊기는 것이 여기서는 오히려 맞다.
     */
    private int purgeChunk(boolean cellSide, LocalDateTime before) {
        return cellSide
                ? purgeMapper.deleteOldCellLogs(before, CHUNK)
                : purgeMapper.deleteOldDocLogs(before, CHUNK);
    }
}
