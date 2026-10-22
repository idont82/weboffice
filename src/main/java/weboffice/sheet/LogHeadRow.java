package weboffice.sheet;

import java.time.LocalDateTime;

/** 버전 목록을 묶는 데 필요한 만큼만 읽은 로그 한 줄. 값은 안 읽는다 - 목록에 안 쓴다. */
public record LogHeadRow(
        long logSeq,
        LocalDateTime changedAt,
        String changedUser
) {
}
