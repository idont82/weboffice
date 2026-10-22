package weboffice.sheet;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 편집 기록을 "버전" 으로 묶는 규칙.
 *
 * <p>구글도 저장 시점을 따로 두지 않는다. 잇달아 고친 것을 한 덩어리로 묶어 하나의
 * 버전으로 보인다. 우리는 10분 넘게 쉬었거나 사람이 바뀌면 새 버전으로 본다.
 */
class VersionGroupingTest {

    private static LogHeadRow log(long logSeq, String at, String emp) {
        return new LogHeadRow(logSeq, LocalDateTime.parse(at), emp);
    }

    @Test
    void 잇달아_고친_것은_한_버전이다() {
        List<SheetVersion> versions = VersionGrouping.versionsOf(List.of(
                log(3, "2026-08-20T10:05:00", "me"),
                log(2, "2026-08-20T10:02:00", "me"),
                log(1, "2026-08-20T10:00:00", "me")));

        assertThat(versions).hasSize(1);
        assertThat(versions.get(0).versionId())
                .isEqualTo(VersionGrouping.versionIdOf(LocalDateTime.parse("2026-08-20T10:05:00")));
        assertThat(versions.get(0).changeCount()).isEqualTo(3);
        assertThat(versions.get(0).fromAt()).isEqualTo(LocalDateTime.parse("2026-08-20T10:00:00"));
        assertThat(versions.get(0).toAt()).isEqualTo(LocalDateTime.parse("2026-08-20T10:05:00"));
    }

    @Test
    void 열_분_넘게_쉬면_다른_버전이다() {
        List<SheetVersion> versions = VersionGrouping.versionsOf(List.of(
                log(2, "2026-08-20T11:00:00", "me"),
                log(1, "2026-08-20T10:00:00", "me")));

        assertThat(versions).hasSize(2);
        assertThat(versions.get(0).versionId())
                .isEqualTo(VersionGrouping.versionIdOf(LocalDateTime.parse("2026-08-20T11:00:00")));
        assertThat(versions.get(1).versionId())
                .isEqualTo(VersionGrouping.versionIdOf(LocalDateTime.parse("2026-08-20T10:00:00")));
    }

    @Test
    void 사람이_바뀌면_다른_버전이다() {
        // 같은 시각에 고쳐도 누가 고친 것인지가 버전을 가르는 값이다.
        List<SheetVersion> versions = VersionGrouping.versionsOf(List.of(
                log(2, "2026-08-20T10:01:00", "other"),
                log(1, "2026-08-20T10:00:00", "me")));

        assertThat(versions).hasSize(2);
        assertThat(versions.get(0).editors()).containsExactly("other");
        assertThat(versions.get(1).editors()).containsExactly("me");
    }

    @Test
    void 최근_버전이_앞이다() {
        List<SheetVersion> versions = VersionGrouping.versionsOf(List.of(
                log(3, "2026-08-20T12:00:00", "me"),
                log(2, "2026-08-20T11:00:00", "me"),
                log(1, "2026-08-20T10:00:00", "me")));

        assertThat(versions).extracting(SheetVersion::versionId).containsExactly(
                VersionGrouping.versionIdOf(LocalDateTime.parse("2026-08-20T12:00:00")),
                VersionGrouping.versionIdOf(LocalDateTime.parse("2026-08-20T11:00:00")),
                VersionGrouping.versionIdOf(LocalDateTime.parse("2026-08-20T10:00:00")));
    }

    @Test
    void 기록이_없으면_버전도_없다() {
        assertThat(VersionGrouping.versionsOf(List.of())).isEmpty();
    }
}
