package weboffice.doc;

import java.time.LocalDateTime;

/** 버전 펼쳐 보기가 읽는 문단 로그 한 줄. */
public record DocVersionChangeRow(
        int paragraphNo,
        String orgValue,
        String modValue,
        LocalDateTime changedAt,
        String changedUser
) {
}
