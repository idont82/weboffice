package weboffice.sheet;

import java.time.LocalDateTime;

/**
 * 한 버전 안에서 바뀐 자리 하나.
 *
 * <p>{@code where} 는 사람이 읽는 자리다 - 시트 문서는 "시트1 · C2", 글 문서는
 * "문단 3". 화면은 이 글을 그대로 보인다.
 */
public record VersionChangeDto(
        String where,
        String before,
        String after,
        LocalDateTime changedAt,
        String changedBy
) {
}
