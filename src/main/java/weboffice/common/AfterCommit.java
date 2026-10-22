package weboffice.common;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 트랜잭션 커밋 후에 실행한다.
 *
 * <p>커밋 전에 브로드캐스트하면 알림을 받은 클라이언트가 곧바로 조회했을 때 아직
 * 반영되지 않은 상태를 볼 수 있다. 롤백된 트랜잭션의 알림이 나가는 것도 막는다.
 * 트랜잭션이 없으면 즉시 실행한다.
 */
public final class AfterCommit {

    private AfterCommit() {
    }

    public static void run(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        action.run();
                    }
                });
    }
}
