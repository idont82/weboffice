package weboffice.sheet;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import weboffice.security.UserContext;
import weboffice.workbook.WorkbookPermissionService;

/**
 * 보던 시트와 배율.
 *
 * <p>엑셀처럼 문서를 다시 열면 보던 시트로, 그 시트를 보던 배율로 연다. 브라우저에
 * 두면 다른 PC 에서 기억이 없다 - 계정을 따라간다(2026-08-13 지적).
 *
 * <p>개인 것이다. 남이 본 자리는 보이지 않는다. 사번은 세션에서 꺼내 쓰고 응답에는
 * 담지 않는다(설계 3절).
 */
@RestController
public class SheetViewController {

    private final SheetViewMapper viewMapper;
    private final WorkbookPermissionService permissionService;

    public SheetViewController(SheetViewMapper viewMapper,
                               WorkbookPermissionService permissionService) {
        this.viewMapper = viewMapper;
        this.permissionService = permissionService;
    }

    /**
     * 이 문서에서 내가 보던 자리.
     *
     * @param lastSheetSeq 마지막으로 본 시트. 본 적이 없으면 null.
     * @param zooms 시트마다의 배율. 1 이 100% 다.
     */
    public record SheetView(Long lastSheetSeq, Map<Long, Double> zooms) {
    }

    /** 지금 보고 있는 자리. 배율을 안 보내면 앞서 담은 배율을 그대로 둔다. */
    public record SheetViewRequest(Long sheetSeq, Double zoom) {
    }

    @GetMapping("/api/workbooks/{workbookSeq}/view")
    public SheetView readView(@PathVariable Long workbookSeq) {
        permissionService.requireReadWorkbook(workbookSeq);

        List<SheetViewDto> rows = viewMapper.selectViews(userKey(), workbookSeq);
        Map<Long, Double> zooms = new LinkedHashMap<>();
        for (SheetViewDto row : rows) {
            zooms.put(row.sheetSeq(), row.zoomRatio());
        }
        // 조회가 최근 순이라 첫 줄이 마지막으로 본 시트다.
        Long last = rows.isEmpty() ? null : rows.get(0).sheetSeq();
        return new SheetView(last, zooms);
    }

    @PutMapping("/api/workbooks/{workbookSeq}/view")
    public SheetView saveView(@PathVariable Long workbookSeq,
                              @RequestBody SheetViewRequest request) {
        // 읽을 수 있으면 제 보기는 담을 수 있다. 문서를 고치는 것이 아니다.
        permissionService.requireReadWorkbook(workbookSeq);
        if (request.sheetSeq() == null) {
            throw new IllegalArgumentException("어느 시트인지 알려 주세요.");
        }
        viewMapper.upsertView(userKey(), workbookSeq, request.sheetSeq(), request.zoom());
        return readView(workbookSeq);
    }

    private String userKey() {
        return UserContext.current().userKey();
    }
}
