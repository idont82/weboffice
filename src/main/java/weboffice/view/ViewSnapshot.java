package weboffice.view;

/**
 * 뷰어가 한 번에 받는 덩어리.
 *
 * <p>{@code json} 은 화면이 그대로 쓰는 모양이다. 서버는 안을 해석하지 않는다 -
 * 셀 서식과 같은 판단이다.
 */
public record ViewSnapshot(String docType, String fileName, boolean encrypted, String json) {
}
