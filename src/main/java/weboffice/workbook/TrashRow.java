package weboffice.workbook;

import java.time.LocalDateTime;

/**
 * 휴지통에 있는 문서 한 줄.
 *
 * <p>사번은 담지 않는다. 휴지통은 제 것만 보이므로 누가 지웠는지도 나 자신이다.
 */
public record TrashRow(
        Long workbookSeq,
        String workbookName,
        String docType,
        LocalDateTime deletedAt
) {
}
