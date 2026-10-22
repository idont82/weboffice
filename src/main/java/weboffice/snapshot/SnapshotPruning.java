package weboffice.snapshot;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 무엇을 솎을 것인가.
 *
 * <p>스냅샷 하나가 수백 KB 다. 안 솎으면 문서 하나가 한 해에 수십 MB 로 자란다.
 *
 * <p>규칙은 둘뿐이다.
 * <ul>
 *   <li>자동본은 최근 {@value #KEEP} 개만 남긴다.
 *   <li>{@value #THIN_AFTER_DAYS} 일이 지난 자동본은 하루에 하나(그날의 마지막)만 남긴다.
 * </ul>
 *
 * <p>사람이 만든 것과 위험한 일 직전 것은 세지도 지우지도 않는다. 일부러 남긴 것과
 * 사고 직전 모습이 가장 값진데, 그것이 자동본에 밀려 사라지면 이 기능의 뜻이 없다.
 */
public final class SnapshotPruning {

    /** 남길 자동본 수. */
    public static final int KEEP = 10;

    /** 이 날 수가 지나면 하루에 하나로 솎는다. */
    public static final int THIN_AFTER_DAYS = 30;

    /** 저절로 찍힌 것. 이것만 솎는다. */
    private static final Set<String> AUTOMATIC = Set.of("SESSION", "DAILY");

    private SnapshotPruning() {
    }

    /**
     * 지워도 되는 것.
     *
     * <p>최근 것이 앞에 오도록 스스로 줄을 세운다 - 매퍼가 어떤 차례로 주든 답이
     * 같아야 한다.
     */
    public static List<Long> stale(List<SnapshotHead> heads, LocalDateTime now) {
        List<SnapshotHead> automatic = heads.stream()
                .filter(one -> AUTOMATIC.contains(one.reason()))
                .sorted(Comparator.comparing(SnapshotHead::takenAt).reversed())
                .toList();

        Set<Long> stale = new LinkedHashSet<>();
        for (int at = KEEP; at < automatic.size(); at += 1) {
            stale.add(automatic.get(at).snapshotSeq());
        }

        // 오래된 것은 하루에 하나만 남긴다. 최근 것이 앞이므로 그날 처음 만난 것이
        // 그날의 마지막 모습이다.
        LocalDateTime thinBefore = now.minus(Duration.ofDays(THIN_AFTER_DAYS));
        Set<LocalDate> kept = new LinkedHashSet<>();
        for (SnapshotHead one : automatic) {
            if (one.takenAt().isAfter(thinBefore)) {
                continue;
            }
            if (!kept.add(one.takenAt().toLocalDate())) {
                stale.add(one.snapshotSeq());
            }
        }
        return new ArrayList<>(stale);
    }
}
