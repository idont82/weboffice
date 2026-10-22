package weboffice.snapshot;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 무엇을 솎을 것인가.
 *
 * <p>DB 없이 세는 부분만 따로 뗐다 - 규칙이 촘촘해서 표를 깔고 확인하면 무엇이
 * 규칙이고 무엇이 우연인지 흐려진다.
 */
class SnapshotPruningTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 31, 12, 0);

    private static SnapshotHead head(long seq, String reason, LocalDateTime takenAt) {
        return new SnapshotHead(seq, reason, takenAt);
    }

    @Test
    void 적으면_아무것도_안_지운다() {
        List<SnapshotHead> kept = List.of(
                head(1, "SESSION", NOW.minusHours(1)),
                head(2, "SESSION", NOW.minusHours(2)));

        assertThat(SnapshotPruning.stale(kept, NOW)).isEmpty();
    }

    @Test
    void 자동본은_최근_열_개만_남긴다() {
        List<SnapshotHead> many = new java.util.ArrayList<>();
        for (int at = 0; at < 13; at += 1) {
            many.add(head(at + 1, "SESSION", NOW.minusHours(at)));
        }

        // 11·12·13번째로 오래된 것이 밀려난다.
        assertThat(SnapshotPruning.stale(many, NOW)).containsExactly(11L, 12L, 13L);
    }

    @Test
    void 손으로_만든_것은_아무리_많아도_안_지운다() {
        // 사람이 일부러 남긴 것이 가장 값지다.
        List<SnapshotHead> many = new java.util.ArrayList<>();
        for (int at = 0; at < 20; at += 1) {
            many.add(head(at + 1, "MANUAL", NOW.minusHours(at)));
        }

        assertThat(SnapshotPruning.stale(many, NOW)).isEmpty();
    }

    @Test
    void 위험한_일_직전_것도_안_지운다() {
        // 사고 직전 모습이라 가장 자주 찾게 된다.
        List<SnapshotHead> many = new java.util.ArrayList<>();
        for (int at = 0; at < 20; at += 1) {
            many.add(head(at + 1, "BEFORE_AI", NOW.minusHours(at)));
        }

        assertThat(SnapshotPruning.stale(many, NOW)).isEmpty();
    }

    @Test
    void 손으로_만든_것은_열_개_셈에도_안_든다() {
        // 사람이 열 개를 만들어 두면 자동본이 한 개도 안 남는 일이 생긴다.
        List<SnapshotHead> mixed = new java.util.ArrayList<>();
        for (int at = 0; at < 10; at += 1) {
            mixed.add(head(at + 1, "MANUAL", NOW.minusMinutes(at)));
        }
        mixed.add(head(99, "SESSION", NOW.minusHours(3)));

        assertThat(SnapshotPruning.stale(mixed, NOW)).isEmpty();
    }

    @Test
    void 서른_날이_지난_자동본은_하루에_하나만_남긴다() {
        List<SnapshotHead> old = List.of(
                head(1, "SESSION", NOW.minusDays(40).withHour(9)),
                head(2, "SESSION", NOW.minusDays(40).withHour(14)),
                head(3, "SESSION", NOW.minusDays(40).withHour(18)),
                head(4, "SESSION", NOW.minusDays(41).withHour(9)));

        // 같은 날 것 중 가장 늦은 것만 남는다 - 그날의 마지막 모습이다.
        assertThat(SnapshotPruning.stale(old, NOW)).containsExactly(2L, 1L);
    }

    @Test
    void 서른_날이_안_지난_것은_같은_날이어도_다_남긴다() {
        List<SnapshotHead> today = List.of(
                head(1, "SESSION", NOW.minusHours(1)),
                head(2, "SESSION", NOW.minusHours(3)),
                head(3, "SESSION", NOW.minusHours(5)));

        assertThat(SnapshotPruning.stale(today, NOW)).isEmpty();
    }

    @Test
    void 오래된_것도_손으로_만든_것이면_남는다() {
        List<SnapshotHead> old = List.of(
                head(1, "MANUAL", NOW.minusDays(40).withHour(9)),
                head(2, "MANUAL", NOW.minusDays(40).withHour(14)));

        assertThat(SnapshotPruning.stale(old, NOW)).isEmpty();
    }

    @Test
    void 차례가_뒤섞여_들어와도_같은_답을_준다() {
        // 매퍼가 어떤 차례로 주든 규칙은 시각으로 정해진다.
        List<SnapshotHead> shuffled = new java.util.ArrayList<>();
        for (int at = 12; at >= 0; at -= 1) {
            shuffled.add(head(at + 1, "SESSION", NOW.minusHours(at)));
        }

        assertThat(SnapshotPruning.stale(shuffled, NOW)).containsExactly(11L, 12L, 13L);
    }
}
