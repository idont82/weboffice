package weboffice.lock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 만료된 잠금 회수.
 *
 * <p>브라우저가 강제 종료되면 하트비트가 끊기고 잠금이 영원히 남는다.
 * 10초 주기로 훑어 EXPIRE_DATETIME 이 지난 잠금을 소프트삭제한다.
 * TTL 60초 + 주기 10초이므로 최대 70초 안에 회수된다.
 *
 * <p>회수 대상에서 DEL_DATETIME 이 이미 찍힌 행을 뺀다(Lock.xml). 사람이 해제한
 * 기록을 SYSTEM 으로 덮으면 누가 풀었는지 알 수 없게 된다.
 */
@Component
public class LockExpiryScheduler {

    private static final Logger log = LoggerFactory.getLogger(LockExpiryScheduler.class);

    private final LockMapper lockMapper;

    public LockExpiryScheduler(LockMapper lockMapper) {
        this.lockMapper = lockMapper;
    }

    /** @return 회수한 잠금 수. 테스트가 직접 호출해 결과를 볼 수 있게 public 이다. */
    @Scheduled(fixedDelayString = "${weboffice.lock.reclaim-interval-ms:10000}")
    @Transactional
    public int reclaimExpiredLocks() {
        int reclaimed = lockMapper.expireOverdueLocks();
        if (reclaimed > 0) {
            log.info("만료된 잠금 {}건을 회수했다", reclaimed);
        }
        return reclaimed;
    }
}
