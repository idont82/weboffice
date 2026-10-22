package weboffice.drawing;

import java.util.List;

/**
 * PUT /api/sheets/{sheetSeq}/drawings 요청 본문.
 *
 * <p>{@code CellDeltaRequest} 와 같은 모양이다. 그림도 이동·크기 변경이 연속으로
 * 일어나므로 클라이언트가 디바운스한 뒤 한 번에 보낸다.
 *
 * <p>둘 중 하나만 보내는 경우가 흔하다. 빠진 쪽을 null 로 두면 매퍼까지 내려가
 * NPE 가 되므로 여기서 빈 목록으로 맞춘다.
 */
public record DrawingDeltaRequest(List<DrawingDto> upserted, List<String> deletedIds) {

    public DrawingDeltaRequest {
        upserted = upserted == null ? List.of() : upserted;
        deletedIds = deletedIds == null ? List.of() : deletedIds;
    }
}
