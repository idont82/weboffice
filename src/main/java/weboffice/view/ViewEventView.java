package weboffice.view;

import java.time.LocalDateTime;

/** 관리자 화면 한 줄. 사번은 담지 않는다. */
public record ViewEventView(
        String eventType,
        String callerSystem,
        String fileName,
        Long byteSize,
        String encryptedYn,
        String viewerName,
        Integer tookMs,
        String resultCode,
        String message,
        String clientIp,
        LocalDateTime changedAt
) {
}
