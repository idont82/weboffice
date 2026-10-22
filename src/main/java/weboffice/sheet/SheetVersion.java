package weboffice.sheet;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 문서의 한 버전.
 *
 * <p>{@code versionId} 는 그 덩어리의 마지막 로그 번호다. "이 버전으로 되돌리기" 는
 * 번호가 그보다 큰 변경을 모두 물리는 일이다.
 *
 * <p>{@code editors} 는 이름이다. 사번은 화면으로 나가지 않는다.
 */
public record SheetVersion(
        long versionId,
        LocalDateTime fromAt,
        LocalDateTime toAt,
        int changeCount,
        List<String> editors
) {
}
