package weboffice.doc;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import weboffice.security.UserContext;
import weboffice.workbook.WorkbookPermissionService;

/**
 * 글 문서 본문 읽기·쓰기.
 *
 * <p>셀과 같은 delta 방식이다. 바뀐 문단과 지운 문단만 온다 - 통째로 보내면 글이
 * 길어질수록 저장이 무거워지고, 둘이 동시에 고칠 때 나중 것이 앞선 것을 덮는다.
 */
@RestController
@RequestMapping("/api/docs/{workbookSeq}/paragraphs")
public class DocParagraphController {

    public record SaveRequest(List<DocParagraphDto> changed, List<Integer> deleted) {
    }

    private final DocParagraphMapper paragraphMapper;
    private final DocParagraphService paragraphService;
    private final WorkbookPermissionService permissionService;

    public DocParagraphController(DocParagraphMapper paragraphMapper,
                                  DocParagraphService paragraphService,
                                  WorkbookPermissionService permissionService) {
        this.paragraphMapper = paragraphMapper;
        this.paragraphService = paragraphService;
        this.permissionService = permissionService;
    }

    @GetMapping
    public List<DocParagraphDto> read(@PathVariable Long workbookSeq) {
        permissionService.requireReadWorkbook(workbookSeq);
        return paragraphMapper.selectParagraphs(workbookSeq);
    }

    @PatchMapping
    public Map<String, Integer> save(@PathVariable Long workbookSeq,
                                     @RequestBody SaveRequest request) {
        permissionService.requireWriteWorkbook(workbookSeq);

        List<DocParagraphDto> changed = request.changed() == null ? List.of() : request.changed();
        List<Integer> deleted = request.deleted() == null ? List.of() : request.deleted();

        paragraphService.save(workbookSeq, changed, deleted, UserContext.currentAuditUser());
        return Map.of("savedCount", changed.size(), "deletedCount", deleted.size());
    }
}
