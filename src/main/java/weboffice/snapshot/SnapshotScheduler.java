package weboffice.snapshot;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import weboffice.common.AuditUser;

/**
 * 저절로 찍는 일꾼.
 *
 * <p>두 가지를 본다.
 * <ul>
 *   <li><b>편집이 끝난 판</b>: 마지막 손댄 지 {@value #IDLE_MINUTES} 분이 지났고 그
 *       뒤로 찍은 것이 없으면 한 장 남긴다. 버전 묶음의 끊김과 같은 분을 쓴다 -
 *       두 곳의 "한 판" 이 어긋나면 화면에서 짝이 안 맞는다.
 *   <li><b>하루 한 번</b>: 새벽에, 어제 손댄 문서를 남긴다. 편집이 안 멎어 위 갈래에
 *       안 걸린 문서를 여기서 줍는다.
 * </ul>
 *
 * <p>찍는 사람은 마지막에 고친 사람이 아니라 시스템이다. 사람 이름으로 남기면
 * 목록에서 "내가 언제 이걸 찍었지" 가 된다.
 */
@Component
public class SnapshotScheduler {

    private static final Logger log = LoggerFactory.getLogger(SnapshotScheduler.class);

    /** 편집이 멎었다고 보는 시간. VersionGrouping.SESSION_GAP 과 같다. */
    public static final int IDLE_MINUTES = 10;

    /** 이보다 오래 전에 멎은 문서는 안 본다. 매번 옛 문서를 다시 훑지 않는다. */
    private static final Duration LOOK_BACK = Duration.ofDays(2);

    /** 찍은 이를 시스템으로 적는다. */
    private static final AuditUser SYSTEM = AuditUser.of("SYSTEM");

    private final SnapshotMapper snapshotMapper;
    private final SnapshotService snapshotService;

    public SnapshotScheduler(SnapshotMapper snapshotMapper, SnapshotService snapshotService) {
        this.snapshotMapper = snapshotMapper;
        this.snapshotService = snapshotService;
    }

    /** 5분마다 본다. 편집이 멎고 5~15분 안에 한 장이 남는다. */
    @Scheduled(fixedDelayString = "${weboffice.snapshot.session-interval-ms:300000}")
    public void takeAfterEditingStops() {
        takeAll("SESSION", Duration.ofMinutes(IDLE_MINUTES), LOOK_BACK);
    }

    /**
     * 새벽 세 시. 어제 손댄 문서를 한 장 남긴다.
     *
     * <p>편집이 멎지 않아 위 갈래에 안 걸린 문서를 여기서 줍는다.
     */
    @Scheduled(cron = "${weboffice.snapshot.daily-cron:0 0 3 * * *}")
    public void takeDaily() {
        takeAll("DAILY", Duration.ZERO, Duration.ofDays(1));
    }

    private void takeAll(String reason, Duration idleFor, Duration lookBack) {
        LocalDateTime now = LocalDateTime.now();
        List<Long> workbooks = snapshotMapper.selectWorkbooksNeedingSnapshot(
                now.minus(idleFor), now.minus(lookBack));
        for (Long workbookSeq : workbooks) {
            try {
                snapshotService.take(workbookSeq, reason, null, SYSTEM);
            } catch (RuntimeException failed) {
                // 한 문서가 실패해도 나머지는 찍는다. 다음 차례에 다시 만난다.
                log.warn("복구 지점을 못 남겼습니다: 문서 {} ({})", workbookSeq, reason, failed);
            }
        }
        if (!workbooks.isEmpty()) {
            log.info("복구 지점 {}개를 남겼습니다({})", workbooks.size(), reason);
        }
    }
}
