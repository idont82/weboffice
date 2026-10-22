package weboffice.sheet;

import java.time.LocalDateTime;

/** 칸 기록 조회가 읽는 한 행. 사번은 여기까지만 온다 - 화면으로는 안 나간다. */
public record CellHistoryLogRow(
        String changeType,
        String orgValue,
        String modValue,
        LocalDateTime changedAt,
        String changedUser
) {
}
