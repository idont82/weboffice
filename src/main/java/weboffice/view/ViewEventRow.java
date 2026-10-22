package weboffice.view;

/** 이력 한 줄. 요청·열람·오류·관리자 접근이 같은 모양을 쓴다. */
public record ViewEventRow(
        String eventType,
        Long sessionSeq,
        String callerSystem,
        String fileName,
        Long byteSize,
        String encryptedYn,
        String viewerName,
        Integer tookMs,
        String resultCode,
        String message,
        String clientIp,
        String userAgent
) {
}
