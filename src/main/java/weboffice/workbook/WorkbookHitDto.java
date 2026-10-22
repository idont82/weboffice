package weboffice.workbook;

/**
 * 찾기 결과 한 줄. 목록 한 줄에 <b>어디서 맞았는지</b>를 더한 것이다.
 *
 * <p>사번도 공개 토큰도 없다 - 목록과 같은 이유다({@link WorkbookSummaryDto}).
 *
 * @param hit     NAME(이름) · CELL(시트 칸) · PARA(글 문단)
 * @param snippet 맞은 자리 앞뒤 토막. 어느 문서인지 이름만으로 모를 때 고르는 실마리다
 */
public record WorkbookHitDto(
        Long workbookSeq,
        String workbookName,
        String ownerName,
        String role,
        int memberCount,
        boolean shared,
        java.time.LocalDateTime modifiedAt,
        String docType,
        String hit,
        String snippet,
        boolean starred,
        String color
) {
}
