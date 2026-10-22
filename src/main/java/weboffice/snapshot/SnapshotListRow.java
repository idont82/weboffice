package weboffice.snapshot;

import java.time.LocalDateTime;

/**
 * 표에서 읽은 목록 한 줄. 사번이 들어 있어 <b>밖으로 안 나간다</b> -
 * 서비스가 이름으로 바꿔 {@link SnapshotSummaryDto} 로 옮겨 담는다.
 */
public record SnapshotListRow(
        Long snapshotSeq,
        LocalDateTime takenAt,
        String reason,
        String label,
        String takenUser,
        long bodySize
) {
}
