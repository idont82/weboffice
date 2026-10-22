package weboffice.sheet;

import java.util.List;

import weboffice.excel.CellDto;

/**
 * PATCH /api/sheets/{sheetSeq}/cells 요청 본문.
 *
 * <p>둘 중 하나만 보내는 경우가 흔하다. 빠진 쪽을 null 로 두면 서비스가 매번
 * null 검사를 해야 하므로 여기서 빈 목록으로 맞춘다.
 */
public record CellDeltaRequest(List<CellDto> changed, List<CellDto> deleted) {

    public CellDeltaRequest {
        changed = changed == null ? List.of() : changed;
        deleted = deleted == null ? List.of() : deleted;
    }
}
