package weboffice.drawing;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import weboffice.common.AfterCommit;
import weboffice.common.AuditUser;
import weboffice.realtime.RealtimeHub;
import weboffice.security.Actor;
import weboffice.security.UserContext;

/**
 * 그림 배치 저장·조회.
 *
 * <p>그림에는 잠금을 걸지 않는다(설계 4절). 그림은 임의의 셀을 가로질러 떠 있어
 * 범위 잠금 모델에 맞지 않고, 억지로 끼우면 그림이 덮은 영역의 셀 편집이 통째로
 * 막힌다. drawingId 단위 last-write-wins 로 간다.
 */
@Service
public class DrawingService {

    private final DrawingMapper drawingMapper;
    private final RealtimeHub realtimeHub;

    public DrawingService(DrawingMapper drawingMapper, RealtimeHub realtimeHub) {
        this.drawingMapper = drawingMapper;
        this.realtimeHub = realtimeHub;
    }

    public List<DrawingDto> readDrawings(Long sheetSeq) {
        return drawingMapper.selectDrawings(sheetSeq);
    }

    /**
     * 바뀐 그림과 지운 식별자를 한 트랜잭션에서 처리한다.
     *
     * <p>{@code @Transactional} 이 public 진입점에 있어야 한다. 안쪽 메서드에만
     * 붙이고 같은 빈에서 부르면 프록시를 타지 않아 트랜잭션이 사라지는데,
     * 단위 테스트는 안쪽을 직접 불러 통과한다({@code LockService} 전례).
     *
     * <p>삭제를 업서트보다 먼저 한다. 같은 호출에 같은 drawingId 가 양쪽에 들어오면
     * "지우고 다시 넣는" 것이 의도다. 순서가 반대면 방금 넣은 것을 지운다.
     */
    @Transactional
    public int saveDelta(Long sheetSeq, List<DrawingDto> upserted,
                         List<String> deletedIds, AuditUser user) {
        if (upserted.isEmpty() && deletedIds.isEmpty()) {
            return 0;
        }
        if (!deletedIds.isEmpty()) {
            drawingMapper.softDeleteDrawings(sheetSeq, deletedIds, user.userKey());
        }
        if (!upserted.isEmpty()) {
            drawingMapper.upsertDrawings(sheetSeq, upserted, user.userKey());
        }

        // 행위자는 USER_ID 다. 사번은 브라우저로 내보내지 않는다(설계 3절).
        Actor actor = UserContext.currentActor();

        // 커밋 전에 쏘면 알림을 받은 클라이언트가 곧바로 조회했을 때 아직 반영되지
        // 않은 상태를 본다. 롤백된 트랜잭션의 알림이 나가는 것도 막는다.
        AfterCommit.run(() ->
                realtimeHub.drawingsChanged(sheetSeq, actor, upserted, deletedIds));
        return upserted.size() + deletedIds.size();
    }
}
