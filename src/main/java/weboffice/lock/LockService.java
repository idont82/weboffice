package weboffice.lock;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import weboffice.common.AfterCommit;
import weboffice.security.DirectoryUser;
import weboffice.security.UserDirectory;
import weboffice.realtime.RealtimeHub;
import weboffice.security.LoginUser;
import weboffice.security.UserContext;
import weboffice.sheet.CellRange;
import weboffice.workbook.ForbiddenException;
import weboffice.workbook.WorkbookPermissionService;

/**
 * 편집 위치 공유(presence).
 *
 * <p>2026-08-06 에 잠금에서 표시로 바뀌었다. 겹쳐도 막지 않는다. 구글 시트가
 * 그렇듯, 남이 보고 있다고 내가 못 쓰면 그것은 협업이 아니다. 대신 상대가 어디에
 * 있는지 화면에 그린다. 같은 셀을 둘이 고치면 마지막 저장이 이긴다.
 *
 * <p>막지 않기로 한 이상 겹침 검사도, 그것을 위한 시트 행 직렬화도 필요 없다.
 *
 * <p>자리는 <b>사람이 아니라 세션 단위</b>다. 한 사람이 두 창을 띄우면 자리도 둘이다 -
 * 실제로 두 곳을 보고 있으니 그것이 사실이다. 사번으로 하나만 남기면 같은 계정으로
 * 시험할 때 서로의 자리를 지워 아무것도 안 보인다(2026-08-06 실측).
 *
 * <p>옮길 때 앞 자리를 지우는 것은 클라이언트가 한다({@code releaseCurrentLock}).
 * 그것이 실패해도 TTL 이 회수한다.
 *
 * <p>{@code wo_sheet_lock_line} 이라는 표 이름은 그대로 둔다. 이름을 바꾸려면
 * 세 벤더 스키마를 함께 옮겨야 하는데, 얻는 것이 이름뿐이다.
 */
@Service
public class LockService {

    private final LockMapper lockMapper;
    private final WorkbookPermissionService permissionService;
    private final RealtimeHub realtimeHub;
    private final UserDirectory userDirectory;
    private final int ttlSeconds;

    public LockService(LockMapper lockMapper, WorkbookPermissionService permissionService,
                       RealtimeHub realtimeHub,
                       UserDirectory userDirectory,
                       @Value("${weboffice.lock.ttl-seconds:60}") int ttlSeconds) {
        this.lockMapper = lockMapper;
        this.permissionService = permissionService;
        this.realtimeHub = realtimeHub;
        this.userDirectory = userDirectory;
        this.ttlSeconds = ttlSeconds;
    }

    /**
     * 지금 사용자로 자리를 잡는다.
     *
     * <p>사번을 받는 것과 이름을 나눠 둔다. 둘 다 String 을 세 개째 인자로 받아
     * 이름이 같으면 어느 쪽이 불릴지 알 수 없다.
     */
    @Transactional
    public PresenceDto acquireForCurrentUser(Long sheetSeq, CellRange range, String sessionKey) {
        return acquire(sheetSeq, range, UserContext.current().userKey(), sessionKey);
    }

    @Transactional
    public PresenceDto acquire(Long sheetSeq, CellRange range, String userKey) {
        return acquire(sheetSeq, range, userKey, null);
    }

    @Transactional
    public PresenceDto acquire(Long sheetSeq, CellRange range, String userKey, String sessionKey) {
        permissionService.requireWrite(sheetSeq, userKey);

        // 같은 탭이 남긴 앞 자리를 지운다. 사번으로 지우면 두 창을 띄운 사람이
        // 자기 다른 창의 자리를 지우고, 아무것도 안 지우면 새로고침한 탭의 자리가
        // TTL 동안 유령으로 남는다(2026-08-06 지적).
        releaseSameSession(sheetSeq, userKey, sessionKey);

        LockRow lock = new LockRow(null, sheetSeq, range.fromRowNo(), range.fromColumnNo(),
                range.toRowNo(), range.toColumnNo(), userKey, null, null);
        lock.setSessionKey(sessionKey);
        lockMapper.insertLock(lock, userKey, ttlSeconds);

        PresenceDto presence = toPresence(lockMapper.selectLock(lock.lockSeq()));
        AfterCommit.run(() -> realtimeHub.presenceSet(presence));
        return presence;
    }

    @Transactional
    public void release(Long lockSeq) {
        release(lockSeq, UserContext.current().userKey());
    }

    @Transactional
    public void release(Long lockSeq, String userKey) {
        LockRow lock = lockMapper.selectLock(lockSeq);
        if (lock == null) {
            // 이미 풀렸거나 회수됐다. 해제 요청이 두 번 와도 오류가 아니다.
            return;
        }
        if (!lock.lockUser().equals(userKey)) {
            throw new ForbiddenException("다른 사용자의 자리는 지울 수 없습니다");
        }
        lockMapper.releaseLock(lockSeq, userKey);
        AfterCommit.run(() -> realtimeHub.presenceCleared(lock.sheetSeq(), lockSeq));
    }

    @Transactional
    public void heartbeat(Long lockSeq) {
        heartbeat(lockSeq, UserContext.current().userKey());
    }

    @Transactional
    public void heartbeat(Long lockSeq, String userKey) {
        lockMapper.heartbeat(lockSeq, userKey, ttlSeconds);
    }

    private void releaseSameSession(Long sheetSeq, String userKey, String sessionKey) {
        if (sessionKey == null) {
            return;
        }
        for (LockRow held : lockMapper.selectActiveLocks(sheetSeq)) {
            if (sessionKey.equals(held.sessionKey())) {
                lockMapper.releaseLock(held.lockSeq(), userKey);
                Long lockSeq = held.lockSeq();
                AfterCommit.run(() -> realtimeHub.presenceCleared(sheetSeq, lockSeq));
            }
        }
    }

    /**
     * 지금 이 시트에 있는 사람들. 문서를 여는 쪽이 첫 화면을 그릴 때 쓴다.
     *
     * <p>남의 이름은 세션에 없으니 인명부에서 한 번에 읽는다. 줄마다 조회하면
     * 사람 수만큼 인명부를 두드린다(구성원 목록과 같은 함정).
     */
    public List<PresenceDto> listFor(Long sheetSeq) {
        permissionService.requireRead(sheetSeq);
        List<LockRow> active = lockMapper.selectActiveLocks(sheetSeq);
        if (active.isEmpty()) {
            return List.of();
        }

        Map<String, DirectoryUser> byKey = userDirectory
                .findByKeys(active.stream().map(LockRow::lockUser).distinct().toList())
                .stream()
                .collect(Collectors.toMap(DirectoryUser::userKey, user -> user,
                        (first, second) -> first));

        return active.stream().map(lock -> {
            DirectoryUser user = byKey.get(lock.lockUser());
            return toPresence(lock, user == null ? null : user.userId(),
                    user == null ? null : user.displayName(),
                    user == null ? null : user.rank());
        }).toList();
    }

    /**
     * 자리 잡은 사람 자신의 요청이다. 아이디·이름은 세션에 있어 인명부를 안 봐도 된다.
     *
     * <p>세션이 없으면(단위 테스트가 사번을 직접 넘기는 경로) 이름 없이 나간다.
     * 알림 때문에 저장이 터지는 것보다 이름이 비는 편이 낫다.
     */
    private PresenceDto toPresence(LockRow lock) {
        LoginUser user = UserContext.findCurrent();
        boolean mine = user != null && lock.lockUser().equals(user.userKey());
        return toPresence(lock, mine ? user.userId() : null,
                mine ? user.displayName() : null, mine ? user.rank() : null);
    }

    /** 사번을 아이디·이름으로 바꿔 담는다. 나가는 자료에 사번이 실리는 자리는 여기 하나뿐이다. */
    private PresenceDto toPresence(LockRow lock, String userId, String displayName, String rank) {
        return new PresenceDto(lock.lockSeq(), lock.sheetSeq(), userId, displayName, rank,
                lock.sessionKey(),
                lock.fromRowNo(), lock.fromColumnNo(), lock.toRowNo(), lock.toColumnNo());
    }
}
