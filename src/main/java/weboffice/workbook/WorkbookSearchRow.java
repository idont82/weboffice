package weboffice.workbook;

/**
 * 찾은 문서 한 줄의 원본. <b>사번을 담으므로 응답으로 나가지 않는다.</b>
 *
 * @param cellText 찾는 말이 든 시트 칸 글자. 못 찾았으면 null
 * @param paraText 찾는 말이 든 글 문단. 못 찾았으면 null
 */
record WorkbookSearchRow(Long workbookSeq, String workbookName, String ownerUser,
                         String docType,
                         String roleCode, int memberCount, boolean sharedPublicly,
                         java.time.LocalDateTime modifiedAt,
                         String cellText, String paraText) {
}
