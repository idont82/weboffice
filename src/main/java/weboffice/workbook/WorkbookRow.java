package weboffice.workbook;

/**
 * 목록 한 줄의 원본. <b>사번을 담으므로 응답으로 나가지 않는다</b> -
 * {@code WorkbookSummaryDto} 로 옮긴 뒤에 나간다.
 *
 * @param sharedPublicly 공개 링크가 걸려 있는지. 토큰 값 자체는 담지 않는다
 * @param modifiedAt     마지막으로 손댄 때. 한 번도 안 고쳤으면 만든 때다
 */
record WorkbookRow(Long workbookSeq, String workbookName, String ownerUser,
                   String docType,
                   String roleCode, int memberCount, boolean sharedPublicly,
                   java.time.LocalDateTime modifiedAt) {
}
