package weboffice.sheet;

import java.time.LocalDateTime;

/**
 * 칸 하나가 언제 어떻게 바뀌었는가.
 *
 * <p>사번은 담지 않는다. 이름과 직급만 내려보낸다 - 화면에 사번이 뜨면 어깨너머로
 * 읽히고 스크린샷에 남는다(설계 규칙).
 */
public record CellHistoryDto(
        /** INSERT · UPDATE · DELETE */
        String changeType,
        /** 고치기 전에 보이던 글. 새로 넣은 칸이면 null. */
        String before,
        /** 고친 뒤에 보이는 글. 지운 칸이면 null. */
        String after,
        LocalDateTime changedAt,
        /** "홍길동 과장". 이름을 못 찾으면 null. */
        String changedBy
) {
}
