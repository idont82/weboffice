package weboffice.view;

import java.time.LocalDateTime;

/** 변환 결과 캐시 한 줄. 원본 바이트는 담지 않는다. */
public record ViewCacheRow(
        String fileHash,
        String docType,
        byte[] snapshotGzip,
        Long byteSize,
        Integer madeMs,
        String encryptedYn,
        LocalDateTime inputDatetime
) {
}
