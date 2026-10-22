package weboffice.workbook;

/**
 * 공유 대화상자에 나가는 구성원 한 명. <b>사번이 없다.</b>
 *
 * <p>인명부에서 못 찾은 사람은 이름 자리가 전부 null 이 된다. 줄 자체는 남겨야 한다 -
 * 사라지면 제거 버튼도 같이 사라져 뺄 수가 없다.
 */
public record WorkbookMemberDto(String userId, String displayName, String rank,
                                String deptName, String role) {
}
