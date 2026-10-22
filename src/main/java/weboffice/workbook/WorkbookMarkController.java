package weboffice.workbook;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import weboffice.security.UserContext;

/**
 * 별표와 색 딱지.
 *
 * <p>표시는 개인 것이라 권한은 "읽을 수 있는 문서인가" 만 본다. 남이 공유해 준
 * 문서에도 내 별표를 단다 - 그것이 목록에서 찾기 쉬워지는 길이다.
 */
@RestController
public class WorkbookMarkController {

    private final WorkbookMarkService markService;
    private final WorkbookPermissionService permissionService;

    public WorkbookMarkController(WorkbookMarkService markService,
                                  WorkbookPermissionService permissionService) {
        this.markService = markService;
        this.permissionService = permissionService;
    }

    /**
     * 표시를 바꾼다.
     *
     * @param starred 즐겨찾기 여부
     * @param color   색. 안 칠하면 null
     */
    public record MarkRequest(boolean starred, String color) {
    }

    @PutMapping("/api/workbooks/{workbookSeq}/mark")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void mark(@PathVariable Long workbookSeq, @RequestBody MarkRequest request) {
        permissionService.requireReadWorkbook(workbookSeq);
        markService.mark(UserContext.current().userKey(), workbookSeq,
                request.starred(), request.color());
    }

    /** 색 일곱 개와 그 이름. 이름을 안 지은 색은 기본 이름으로 온다. */
    @GetMapping("/api/workbook-colors")
    public List<ColorLabelRow> colors() {
        return markService.colorLabels(UserContext.current().userKey());
    }

    public record ColorNameRequest(String name) {
    }

    @PutMapping("/api/workbook-colors/{color}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void renameColor(@PathVariable String color, @RequestBody ColorNameRequest request) {
        markService.renameColor(UserContext.current().userKey(), color, request.name());
    }
}
