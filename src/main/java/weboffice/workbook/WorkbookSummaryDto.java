package weboffice.workbook;

/**
 * 목록 화면에 나가는 문서 한 줄. <b>사번도 공개 토큰도 없다.</b>
 *
 * <p>토큰을 담지 않는 것이 중요하다. 목록은 여럿에게 보이는 화면이라 실리면
 * 공개 링크가 그대로 새어 나간다. 공개 여부만 참·거짓으로 알린다.
 *
 * @param ownerName   소유자 성명. 인명부에서 채운다. 못 찾으면 null
 * @param role        내가 이 문서에 대해 갖는 권한
 * @param memberCount 공유 인원. 내 문서에서만 의미가 있다
 * @param shared      공개 링크가 걸려 있는지
 * @param modifiedAt  마지막으로 손댄 때. 목록이 최신순이라 이것이 정렬 기준이다
 * @param preview     카드에 보일 첫 시트 앞부분. 줄마다 칸 수가 같다
 * @param docType     시트냐 글이냐. 화면이 목록 아이콘과 열 화면을 이것으로 가른다
 * @param starred     내가 즐겨찾기로 달아 두었는지. 남의 별표는 오지 않는다
 * @param color       내가 칠한 색. 안 칠했으면 null
 */
public record WorkbookSummaryDto(
        Long workbookSeq,
        String workbookName,
        String ownerName,
        String role,
        int memberCount,
        boolean shared,
        java.time.LocalDateTime modifiedAt,
        java.util.List<java.util.List<String>> preview,
        String docType,
        boolean starred,
        String color
) {
}
