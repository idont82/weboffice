package weboffice.sheet;

import java.time.LocalDateTime;

/** 버전 펼쳐 보기가 읽는 셀 로그 한 줄. 사번은 여기까지만 온다. */
public record VersionChangeRow(
        String sheetName,
        int rowNo,
        int columnNo,
        String orgValue,
        String modValue,
        LocalDateTime changedAt,
        String changedUser
) {
}
