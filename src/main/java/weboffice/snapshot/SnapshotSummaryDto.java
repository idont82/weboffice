package weboffice.snapshot;

import java.time.LocalDateTime;

/**
 * 화면에 나가는 복구 지점 한 줄. <b>사번도 본문도 없다.</b>
 *
 * @param reason   MANUAL(사람) · SESSION(편집이 끝나서) · DAILY(하루 한 번) ·
 *                 BEFORE_RESTORE · BEFORE_AI · BEFORE_IMPORT(위험한 일 직전)
 * @param takenBy  찍은 사람 이름. 인명부에서 채운다. 못 찾으면 null
 * @param bodySize 푼 크기(바이트). 얼마나 큰 판인지 가늠하게 한다
 */
public record SnapshotSummaryDto(
        Long snapshotSeq,
        LocalDateTime takenAt,
        String reason,
        String label,
        String takenBy,
        long bodySize
) {
}
