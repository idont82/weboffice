package weboffice.sheet;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 편집 기록을 버전으로 묶는다.
 *
 * <p>구글도 저장 시점을 따로 두지 않는다 - 잇달아 고친 것을 한 덩어리로 묶어 버전
 * 하나로 보인다. 우리도 같은 규칙을 쓴다: <b>10분 넘게 쉬었거나 사람이 바뀌면</b>
 * 새 버전이다.
 *
 * <p>순수 함수다. DB 도 시계도 보지 않는다 - 규칙만 여기 있다.
 */
public final class VersionGrouping {

    /** 이만큼 쉬면 다른 판으로 본다. */
    public static final Duration SESSION_GAP = Duration.ofMinutes(10);

    private VersionGrouping() {
    }

    /**
     * @param heads 최근 것이 앞인 로그 목록
     * @return 최근 버전이 앞인 목록
     */
    public static List<SheetVersion> versionsOf(List<LogHeadRow> heads) {
        List<SheetVersion> versions = new ArrayList<>();
        LogHeadRow newest = null;
        LogHeadRow oldest = null;
        int count = 0;

        for (LogHeadRow head : heads) {
            if (newest == null) {
                newest = head;
                oldest = head;
                count = 1;
                continue;
            }
            if (sameSession(oldest, head)) {
                oldest = head;
                count++;
                continue;
            }
            versions.add(versionOf(newest, oldest, count));
            newest = head;
            oldest = head;
            count = 1;
        }
        if (newest != null) {
            versions.add(versionOf(newest, oldest, count));
        }
        return versions;
    }

    /** 목록은 최근 것이 앞이라 {@code older} 가 뒤에 온다. */
    private static boolean sameSession(LogHeadRow newer, LogHeadRow older) {
        if (!java.util.Objects.equals(newer.changedUser(), older.changedUser())) {
            return false;
        }
        if (newer.changedAt() == null || older.changedAt() == null) {
            return false;
        }
        return Duration.between(older.changedAt(), newer.changedAt())
                .compareTo(SESSION_GAP) <= 0;
    }

    private static SheetVersion versionOf(LogHeadRow newest, LogHeadRow oldest, int count) {
        List<String> editors = oldest.changedUser() == null
                ? List.of() : List.of(oldest.changedUser());
        return new SheetVersion(versionIdOf(newest.changedAt()), oldest.changedAt(),
                newest.changedAt(), count, editors);
    }

    /**
     * 버전 번호는 <b>시각</b>이다(1970년부터의 밀리초).
     *
     * <p>로그 번호를 쓰지 않는 까닭: 시트 이력과 글 문단 이력이 서로 다른 표에서
     * 각자 번호를 매긴다. 두 줄기를 한 목록으로 묶으려면 공통 잣대가 필요하고,
     * 되돌리기도 "그 시각 뒤의 변경" 으로 말이 된다.
     */
    public static long versionIdOf(java.time.LocalDateTime at) {
        return at == null ? 0
                : at.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    /** 버전 번호를 다시 시각으로. 되돌리기가 쓴다. */
    public static java.time.LocalDateTime timeOf(long versionId) {
        return java.time.LocalDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(versionId), java.time.ZoneId.systemDefault());
    }
}
