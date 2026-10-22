package weboffice.view;

/** 관리자 화면 위쪽 숫자 요약. */
public record ViewStats(
        int issued,
        int opened,
        int failed,
        int avgConvertMs,
        int cacheRows
) {
}
