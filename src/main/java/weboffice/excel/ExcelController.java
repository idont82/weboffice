package weboffice.excel;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Map;

import java.nio.charset.StandardCharsets;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import weboffice.drm.DrmCodec;
import weboffice.security.UserContext;
import weboffice.sheet.SheetMapper;
import weboffice.workbook.WorkbookPermissionService;
import weboffice.workbook.WorkbookService;

@RestController
@RequestMapping("/api/workbooks")
public class ExcelController {

    private static final String XLSX_MIME =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final ExcelIoService excelIoService;
    private final SheetMapper sheetMapper;
    private final WorkbookPermissionService permissionService;
    private final WorkbookService workbookService;
    private final DrmCodec drmCodec;

    public ExcelController(ExcelIoService excelIoService, SheetMapper sheetMapper,
                           WorkbookPermissionService permissionService,
                           WorkbookService workbookService, DrmCodec drmCodec) {
        this.excelIoService = excelIoService;
        this.sheetMapper = sheetMapper;
        this.permissionService = permissionService;
        this.workbookService = workbookService;
        this.drmCodec = drmCodec;
    }

    @PostMapping("/import")
    public Map<String, Object> importXlsx(@RequestParam("file") MultipartFile file)
            throws IOException {
        String fileName = file.getOriginalFilename();
        if (fileName != null && fileName.toLowerCase().endsWith(".xlsm")) {
            // 스펙 5절: 매크로 문서는 업로드 거부
            throw new IllegalArgumentException("매크로 문서(.xlsm)는 올릴 수 없습니다");
        }

        // 사내 파일은 DRM 이 걸린 채로 올라온다. 풀지 않으면 POI 가 형식을 못 알아본다.
        // 평문이면 그대로 지나간다 - 밖에서 받은 xlsx 도 열 수 있어야 한다.
        byte[] plain = drmCodec.decrypt(file.getBytes(), fileName);

        // 올린 사람이 소유자가 된다. 권한 검사가 없는 이유가 이것이다.
        Long workbookSeq = excelIoService.importToDatabase(
                new ByteArrayInputStream(plain), fileName, UserContext.currentAuditUser());

        return Map.of("workbookSeq", workbookSeq,
                "sheets", sheetMapper.selectSheetsByWorkbook(workbookSeq));
    }

    @GetMapping(value = "/{workbookSeq}/export", produces = XLSX_MIME)
    public ResponseEntity<byte[]> exportXlsx(@PathVariable Long workbookSeq) throws IOException {
        permissionService.requireReadWorkbook(workbookSeq);
        byte[] bytes = excelIoService.exportFromDatabase(workbookSeq);
        String fileName = fileNameOf(workbookSeq);

        // 잠가서 내보낸다. 실패하면 DrmException 이 올라가 500 이 된다 - 평문으로
        // 대신 주지 않는다. 보호 없는 파일이 나가는 것이 기능이 멈추는 것보다 나쁘다.
        byte[] sealed = drmCodec.encrypt(bytes, fileName, UserContext.currentAuditUser().userKey());

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(fileName, StandardCharsets.UTF_8)
                                .build().toString())
                .contentType(MediaType.parseMediaType(XLSX_MIME))
                .body(sealed);
    }

    /**
     * 내려받을 파일 이름. 문서 제목에 {@code .xlsx} 를 붙인다.
     *
     * <p>{@code export.xlsx} 로 고정돼 있어 여러 문서를 받으면 브라우저가
     * {@code export (1).xlsx} 처럼 쌓았다 - 어느 파일이 어느 문서인지 알 수 없다
     * (2026-08-07 지적).
     *
     * <p>{@link ContentDisposition} 이 한글을 RFC 5987 로 싣고 ASCII 대체 이름도
     * 함께 넣는다. 헤더에 한글을 그대로 쓰면 글자가 깨진다.
     */
    private String fileNameOf(Long workbookSeq) {
        String name = workbookService.getWorkbook(workbookSeq).workbookName();
        if (name == null) {
            name = "";
        }
        // 윈도우가 파일명에 못 쓰는 글자다. 그대로 두면 저장이 실패한다.
        name = name.replaceAll("[\\/:*?\"<>|]", "_");
        // 끝의 점과 공백을 뗀다. "테스트문서 입니다." 를 그대로 쓰면 점이 둘이 되고,
        // 윈도우는 이름 끝의 점과 공백을 허용하지 않기도 한다.
        name = name.replaceAll("^[\s.]+|[\s.]+$", "");
        return (name.isEmpty() ? "문서" : name) + ".xlsx";
    }
}
