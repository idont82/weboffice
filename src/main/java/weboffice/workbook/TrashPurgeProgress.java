package weboffice.workbook;

/**
 * 비우기가 어디까지 갔는가.
 *
 * <p>휴지통 뒤에는 셀이 수백만 줄 딸려 있다(2026-09-04 실측: 473문서에 셀 283만,
 * 이력 130만). 다 지울 때까지 붙잡고 있으면 화면은 멈춘 것으로 보인다 - 그래서
 * 시작만 알리고 진행은 이것으로 물어본다.
 *
 * @param running 아직 지우는 중인가
 * @param done    지운 문서 수
 * @param total   지우기로 한 문서 수
 * @param message 끝났거나 막혔을 때 할 말. 진행 중이면 null
 */
public record TrashPurgeProgress(
        boolean running,
        int done,
        int total,
        String message
) {

    public static TrashPurgeProgress idle() {
        return new TrashPurgeProgress(false, 0, 0, null);
    }
}
