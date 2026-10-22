package weboffice.workbook;

import java.util.List;
import java.util.Map;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import weboffice.security.UserContext;

/**
 * 문서 리소스 읽기·쓰기.
 *
 * <p>Univer 의 기능 가운데 셀 값이 아닌 것 - 필터·조건부 서식·데이터 확인·메모 -
 * 은 모두 리소스라는 JSON 덩어리로 오간다. 기능마다 표를 새로 파지 않고 이름표를
 * 붙여 한 곳에 담는다.
 *
 * <p>안을 들여다보지 않는다. 모양을 우리가 정하지 않았으므로 해석하려 들면 Univer
 * 판올림마다 깨진다. 우리가 지키는 것은 "누가 무엇을 저장할 수 있는가" 뿐이다.
 */
@RestController
@RequestMapping("/api/workbooks/{workbookSeq}/resources")
public class WorkbookResourceController {

    public record SaveRequest(String data) {
    }

    private final WorkbookResourceMapper resourceMapper;
    private final WorkbookPermissionService permissionService;

    public WorkbookResourceController(WorkbookResourceMapper resourceMapper,
                                      WorkbookPermissionService permissionService) {
        this.resourceMapper = resourceMapper;
        this.permissionService = permissionService;
    }

    @GetMapping
    public List<WorkbookResourceDto> read(@PathVariable Long workbookSeq) {
        permissionService.requireReadWorkbook(workbookSeq);
        return resourceMapper.selectResources(workbookSeq);
    }

    /**
     * 하나를 통째로 갈아 끼운다.
     *
     * <p>빈 값이면 지운다. 필터를 끄면 Univer 가 빈 것을 넘기는데, 그것을 담아 두면
     * 다음에 열 때 빈 필터가 되살아난다.
     */
    @PutMapping("/{name}")
    @Transactional
    public Map<String, String> save(@PathVariable Long workbookSeq, @PathVariable String name,
                                    @RequestBody SaveRequest request) {
        permissionService.requireWriteWorkbook(workbookSeq);

        resourceMapper.deleteResource(workbookSeq, name);
        String data = request.data();
        if (data != null && !data.isBlank()) {
            resourceMapper.insertResource(workbookSeq, name, data,
                    UserContext.currentAuditUser().userKey());
        }
        return Map.of("name", name);
    }
}
