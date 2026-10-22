package weboffice.sheet;

/**
 * 어느 시트를 몇 배로 보고 있었는가.
 *
 * <p>사번은 담지 않는다. 브라우저로 나가는 값이라 사번이 섞이면 안 된다(설계 3절).
 *
 * @param zoomRatio 1 이 100% 다.
 */
public record SheetViewDto(Long sheetSeq, Double zoomRatio) {
}
