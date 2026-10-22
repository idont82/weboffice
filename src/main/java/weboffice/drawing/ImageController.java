package weboffice.drawing;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import weboffice.security.UserContext;
import weboffice.workbook.WorkbookPermissionService;

@RestController
public class ImageController {

    private final ImageService imageService;
    private final WorkbookPermissionService permissionService;

    public ImageController(ImageService imageService,
                           WorkbookPermissionService permissionService) {
        this.imageService = imageService;
        this.permissionService = permissionService;
    }

    @PostMapping("/api/workbooks/{workbookSeq}/images")
    public Map<String, Object> upload(@PathVariable Long workbookSeq,
                                      @RequestParam("file") MultipartFile file)
            throws IOException {
        permissionService.requireWriteWorkbook(workbookSeq);

        // file.getContentType() 과 원본 파일명은 읽지 않는다. 둘 다 클라이언트가
        // 정하는 값이라 방어가 되지 않는다. ImageService 가 바이트로 판별한다.
        Long imageSeq = imageService.store(workbookSeq, file.getBytes(),
                UserContext.currentAuditUser());

        return Map.of("imageSeq", imageSeq,
                "url", "/api/images/" + imageSeq,
                "mimeType", imageService.load(imageSeq).mimeType());
    }

    /**
     * 사용자가 올린 바이트를 우리 오리진에서 내보낸다. 헤더 셋이 방어선이다.
     *
     * <p>Content-Type 은 저장할 때 바이트로 판별한 값이라 클라이언트가 정할 수 없다.
     * nosniff 로 브라우저의 추측도 막고, inline 으로 처리 방식을 고정한다.
     *
     * <p>캐시를 1년 immutable 로 걸어도 안전한 이유: IMAGE_SEQ 는 (문서, 해시)
     * 유니크 아래 있어 사실상 내용 주소다. 같은 번호가 다른 바이트를 가리키지 않는다.
     */
    @GetMapping("/api/images/{imageSeq}")
    public ResponseEntity<byte[]> download(@PathVariable Long imageSeq) {
        ImageRow image = imageService.load(imageSeq);
        permissionService.requireReadWorkbook(image.workbookSeq());

        return ResponseEntity.ok()
                // CHAR(64) 는 고정 길이다. 64자를 넣으므로 실제로는 공백이 붙지
                // 않지만 벤더에 따라 패딩이 달라질 수 있어 정리해 내보낸다.
                .eTag("\"" + image.imageHash().trim() + "\"")
                .cacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePrivate().immutable())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .contentType(MediaType.parseMediaType(image.mimeType()))
                .body(image.imageData());
    }
}
