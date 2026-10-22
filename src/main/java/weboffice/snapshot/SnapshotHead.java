package weboffice.snapshot;

import java.time.LocalDateTime;

/**
 * 스냅샷 한 개의 머리말. <b>본문은 없다.</b>
 *
 * <p>솎기와 목록은 본문을 안 읽는다. 함께 읽으면 목록 한 번에 수십 MB 를 끌어온다.
 */
public record SnapshotHead(
        Long snapshotSeq,
        String reason,
        LocalDateTime takenAt
) {
}
