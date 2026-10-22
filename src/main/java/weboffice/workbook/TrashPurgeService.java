package weboffice.workbook;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import jakarta.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import weboffice.common.AuditUser;
import weboffice.common.Chunks;

/**
 * 휴지통 비우기.
 *
 * <p>되돌릴 수 없다. 지우기(휴지통에 넣기)와 다른 일이라 서비스를 따로 둔다 -
 * 한 클래스 안에 "표시만 하는 지우기" 와 "정말 지우기" 가 섞이면 어느 쪽을 부르는지
 * 호출부에서 흐려진다.
 *
 * <p>내 문서만 지운다. 공유받은 문서는 애초에 내 휴지통에 없다.
 *
 * <p><b>뒤에서 지운다.</b> 휴지통 뒤에는 셀이 수백만 줄 딸려 있어(2026-09-04 실측:
 * 473문서에 셀 283만 줄, 이력 130만 줄) 다 지울 때까지 붙잡으면 화면이 멈춘 것으로
 * 보인다. 시작만 알리고 진행은 {@link #progressOf} 로 물어보게 한다.
 */
@Service
public class TrashPurgeService {

    private static final Logger log = LoggerFactory.getLogger(TrashPurgeService.class);

    /**
     * 한 번에 지울 문서 수.
     *
     * <p>사이사이 다른 저장이 끼어들 수 있게, 그리고 진행 표시가 자주 움직이게
     * 잘게 나눈다 - 스무 개씩 지웠더니 서른 초 동안 눈금이 한 번도 안 움직였다
     * (2026-09-04 실측).
     */
    private static final int DOCS_AT_ONCE = 5;

    private final WorkbookPurgeMapper purgeMapper;

    /**
     * 사람마다 지금 하는 비우기.
     *
     * <p>서버가 여럿이면 자기 서버 것만 보인다. 진행 표시가 잠깐 안 보일 뿐 지우는
     * 일 자체는 끝까지 간다 - 이것 때문에 표를 하나 더 두지는 않는다.
     */
    private final Map<String, TrashPurgeProgress> progress = new ConcurrentHashMap<>();

    /** 비우기를 도는 일꾼. 사람이 여럿이어도 한 줄로 세워 DB 를 덜 흔든다. */
    private final ExecutorService worker = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "trash-purge");
        thread.setDaemon(true);
        return thread;
    });

    public TrashPurgeService(WorkbookPurgeMapper purgeMapper) {
        this.purgeMapper = purgeMapper;
    }

    @PreDestroy
    void stop() {
        worker.shutdownNow();
    }

    /**
     * 비우기를 시작한다. 곧바로 돌아온다.
     *
     * @param keepDays 최근 이 날 수 안에 버린 것은 남긴다. 0 이면 전부 지운다
     * @return 지우기로 한 문서 수
     */
    public int start(AuditUser user, int keepDays) {
        if (keepDays < 0) {
            throw new IllegalArgumentException("남길 날 수는 0 이상이어야 합니다");
        }
        String userKey = user.userKey();
        if (progressOf(user).running()) {
            throw new IllegalStateException("이미 비우는 중입니다");
        }

        // 전부 비우기(0)는 버린 때를 아예 안 본다. 경계를 "지금" 으로 잡으면 방금
        // 버린 문서가 간발의 차로 빠진다 - 자바의 "지금" 과 DB 가 찍은 시각은
        // 눈금이 달라, 셋에 한 번꼴로 한 건이 남았다(2026-10-14 실측).
        LocalDateTime deletedBefore = keepDays == 0 ? null : LocalDateTime.now().minusDays(keepDays);
        List<Long> targets = purgeMapper.selectPurgeTargets(userKey, deletedBefore);
        if (targets.isEmpty()) {
            progress.put(userKey, new TrashPurgeProgress(false, 0, 0, "지울 것이 없습니다"));
            return 0;
        }

        progress.put(userKey, new TrashPurgeProgress(true, 0, targets.size(), null));
        worker.submit(() -> purgeInBackground(targets, user, keepDays));
        return targets.size();
    }

    /** 어디까지 갔는가. 시작한 적이 없으면 "하는 중 아님" 이다. */
    public TrashPurgeProgress progressOf(AuditUser user) {
        return progress.getOrDefault(user.userKey(), TrashPurgeProgress.idle());
    }

    private void purgeInBackground(List<Long> targets, AuditUser user, int keepDays) {
        String userKey = user.userKey();
        long startedAt = System.currentTimeMillis();
        int done = 0;
        try {
            for (List<Long> chunk : Chunks.by(targets, DOCS_AT_ONCE)) {
                purgeAll(chunk, user);
                done += chunk.size();
                progress.put(userKey, new TrashPurgeProgress(true, done, targets.size(), null));
            }
            progress.put(userKey, new TrashPurgeProgress(false, done, targets.size(),
                    done + "개를 지웠습니다"));
            log.info("휴지통에서 문서 {}개를 지웠습니다(남긴 날 수 {}, {}ms)",
                    done, keepDays, System.currentTimeMillis() - startedAt);
        } catch (RuntimeException failed) {
            // 지운 만큼은 이미 지워졌다. 남은 것은 다시 눌러 이어 지운다.
            progress.put(userKey, new TrashPurgeProgress(false, done, targets.size(),
                    "비우다가 막혔습니다. 다시 눌러 주세요"));
            log.warn("휴지통 비우기가 막혔습니다(지운 문서 {}개)", done, failed);
        }
    }

    /**
     * 문서 묶음을 뿌리째 지운다.
     *
     * <p>차례가 곧 참조의 역순이다. 하나라도 앞뒤가 바뀌면 참조 무결성 위반으로
     * 막히고, 그 실패는 무엇이 문제인지 알아보기 어렵다.
     *
     * <p>문서마다 도는 대신 표마다 한 번씩 돈다 - 사백 건이면 왕복이 팔천 번에서
     * 열일곱 번으로 준다(2026-09-03 지적: 비우기가 오래 걸린다).
     */
    @Transactional
    public void purgeAll(List<Long> workbookSeqs, AuditUser user) {
        step("개인 보기", () -> purgeMapper.deleteUserSheetViews(workbookSeqs));
        step("그림", () -> purgeMapper.deleteDrawings(workbookSeqs));
        step("이미지", () -> purgeMapper.deleteImages(workbookSeqs));
        step("셀 이력", () -> purgeMapper.deleteCellLogs(workbookSeqs));
        step("문단 이력", () -> purgeMapper.deleteDocLogs(workbookSeqs));
        step("잠금", () -> purgeMapper.deleteLocks(workbookSeqs));
        step("크기", () -> purgeMapper.deleteSizes(workbookSeqs));
        step("병합", () -> purgeMapper.deleteMerges(workbookSeqs));
        step("셀", () -> purgeMapper.deleteCells(workbookSeqs));
        step("시트", () -> purgeMapper.deleteSheets(workbookSeqs));
        // 서식 사전은 셀이 가리키므로 셀보다 늦게 지운다.
        step("서식", () -> purgeMapper.deleteStyles(workbookSeqs));
        step("문단", () -> purgeMapper.deleteParagraphs(workbookSeqs));
        step("자원", () -> purgeMapper.deleteResources(workbookSeqs));
        step("구성원", () -> purgeMapper.deleteMembers(workbookSeqs));
        step("복구 지점", () -> purgeMapper.deleteSnapshots(workbookSeqs));
        step("표시", () -> purgeMapper.deleteMarks(workbookSeqs));
        step("문서", () -> purgeMapper.deleteWorkbooks(workbookSeqs, user.userKey()));
    }

    /**
     * 다 지울 때까지 기다린다.
     *
     * <p>화면은 진행을 물어보며 기다리지만, 시험은 끝난 뒤를 봐야 한다.
     *
     * @return 지운 문서 수
     */
    public int purgeAndWait(AuditUser user, int keepDays) {
        int total = start(user, keepDays);
        while (progressOf(user).running()) {
            try {
                Thread.sleep(20);
            } catch (InterruptedException stopped) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return total;
    }

    /**
     * 한 단계와 그 시간.
     *
     * <p>느릴 때 어느 표가 오래 걸리는지 알아야 손을 댈 수 있다. 평소에는 안 찍고
     * 자세히 보기(DEBUG)를 켤 때만 찍는다.
     */
    private void step(String what, java.util.function.IntSupplier work) {
        if (!log.isDebugEnabled()) {
            work.getAsInt();
            return;
        }
        long startedAt = System.currentTimeMillis();
        int removed = work.getAsInt();
        log.debug("  비우기 · {} {}줄 {}ms", what, removed, System.currentTimeMillis() - startedAt);
    }
}
