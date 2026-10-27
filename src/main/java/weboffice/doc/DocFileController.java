package weboffice.doc;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import weboffice.drm.DrmCodec;
import weboffice.security.UserContext;
import weboffice.workbook.DocType;
import weboffice.workbook.WorkbookPermissionService;
import weboffice.workbook.WorkbookService;

/**
 * 글 문서 올리기·내려받기.
 *
 * <p>올리는 것도 내려받는 것도 docx·hwpx 다. 받는 쪽이 한글을 쓰면 docx 를 한글에서
 * 열어 다시 저장해야 하는데, 그 자리에서 표 테두리와 줄 간격이 한 번 더 어긋난다.
 * 옛 .hwp 로는 안 내보낸다 - hwpx 가 한글 2010 부터 열리는 공공 표준이다.
 *
 * <p>xlsx 와 같은 규칙이다. 사내 파일은 문서보안이 걸린 채로 올라오므로 풀어서
 * 읽고, 내려줄 때는 씌워서 준다. 실패하면 500 이다 - 평문으로 대신 주지 않는다.
 */
@RestController
public class DocFileController {

    /** 가져온 표 얼개를 담는 이름표. */
    private static final String IMPORT_TABLES = "WO_IMPORT_TABLES";

    /** 가져온 그림 자리를 담는 이름표. 알맹이는 그림 저장소에 있다. */
    private static final String IMPORT_IMAGES = "WO_IMPORT_IMAGES";

    /** 가져온 문서의 종이 크기와 여백을 담는 이름표. */
    private static final String IMPORT_PAGE = "WO_IMPORT_PAGE";

    /** 문서 기본 서식(글꼴·크기·간격·줄 격자). 내보낼 때 그대로 되돌린다. */
    private static final String IMPORT_DEFAULTS = "WO_IMPORT_DEFAULTS";

    /** 쪽마다 되풀이되는 글을 담는 이름표. 머릿글과 꼬릿글이다. */
    private static final String IMPORT_HEADER = "WO_IMPORT_HEADER";

    private static final String IMPORT_FOOTER = "WO_IMPORT_FOOTER";

    /** 첫 쪽에만 쓰는 머릿글·꼬릿글. 이 문서는 첫 쪽에 아무것도 안 찍는다. */
    private static final String IMPORT_FIRST_HEADER = "WO_IMPORT_FIRST_HEADER";

    private static final String IMPORT_FIRST_FOOTER = "WO_IMPORT_FIRST_FOOTER";

    /** 첫 쪽에 다른 머릿글을 쓰는가(titlePg). */
    private static final String IMPORT_TITLE_PAGE = "WO_IMPORT_TITLE_PAGE";

    private static final org.slf4j.Logger logger =
            org.slf4j.LoggerFactory.getLogger(DocFileController.class);

    private static final tools.jackson.databind.json.JsonMapper JSON =
            tools.jackson.databind.json.JsonMapper.builder().build();

    private static final String DOCX_MIME =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    /** 한글 문서(hwpx)의 종류. 한글이 제 파일에 적어 두는 것과 같다. */
    private static final String HWPX_MIME = "application/hwp+zip";

    private final DocxIoService docxIoService;
    private final HwpIoService hwpIoService;
    private final HwpxWriteService hwpxWriteService;
    private final DocParagraphMapper paragraphMapper;
    private final WorkbookService workbookService;
    private final WorkbookPermissionService permissionService;
    private final DrmCodec drmCodec;
    private final weboffice.workbook.WorkbookResourceMapper resourceMapper;
    private final weboffice.drawing.ImageService imageService;

    public DocFileController(DocxIoService docxIoService, HwpIoService hwpIoService,
                             HwpxWriteService hwpxWriteService,
                             DocParagraphMapper paragraphMapper,
                             WorkbookService workbookService,
                             WorkbookPermissionService permissionService, DrmCodec drmCodec,
                             weboffice.workbook.WorkbookResourceMapper resourceMapper,
                             weboffice.drawing.ImageService imageService) {
        this.docxIoService = docxIoService;
        this.hwpIoService = hwpIoService;
        this.hwpxWriteService = hwpxWriteService;
        this.paragraphMapper = paragraphMapper;
        this.workbookService = workbookService;
        this.permissionService = permissionService;
        this.drmCodec = drmCodec;
        this.resourceMapper = resourceMapper;
        this.imageService = imageService;
    }

    @PostMapping("/api/docs/import")
    @Transactional
    public Map<String, Object> importDoc(@RequestParam("file") MultipartFile file)
            throws IOException {
        String fileName = file.getOriginalFilename();
        byte[] plain = drmCodec.decrypt(file.getBytes(), fileName);
        DocContent content = read(fileName, plain);
        List<DocParagraphDto> rows = content.paragraphs();

        // 올린 사람이 소유자가 된다. 권한 검사가 없는 이유가 이것이다.
        var created = workbookService.createBlank(titleOf(fileName), DocType.DOC,
                UserContext.currentAuditUser());
        if (!rows.isEmpty()) {
            paragraphMapper.insertParagraphs(created.workbookSeq(), rows,
                    UserContext.currentAuditUser().userKey());
        }
        saveTables(created.workbookSeq(), content.tables());
        savePictures(created.workbookSeq(), content.pictures());
        savePage(created.workbookSeq(), content.page());
        saveDefaults(created.workbookSeq(), content.defaults());
        saveEdge(created.workbookSeq(), IMPORT_HEADER, content.header());
        saveEdge(created.workbookSeq(), IMPORT_FOOTER, content.footer());
        saveEdge(created.workbookSeq(), IMPORT_FIRST_HEADER, content.firstHeader());
        saveEdge(created.workbookSeq(), IMPORT_FIRST_FOOTER, content.firstFooter());
        if (content.titlePage()) {
            resourceMapper.deleteResource(created.workbookSeq(), IMPORT_TITLE_PAGE);
            resourceMapper.insertResource(created.workbookSeq(), IMPORT_TITLE_PAGE, "true",
                    UserContext.currentAuditUser().userKey());
        }
        return Map.of("workbookSeq", created.workbookSeq(),
                "workbookName", created.workbookName(),
                "docType", created.docType(),
                "paragraphCount", rows.size());
    }

    @GetMapping(value = "/api/docs/{workbookSeq}/export", produces = DOCX_MIME)
    public ResponseEntity<byte[]> exportDocx(@PathVariable Long workbookSeq) throws IOException {
        permissionService.requireReadWorkbook(workbookSeq);

        DocContent content = savedContent(workbookSeq);
        byte[] docx = docxIoService.toDocx(content.paragraphs(),
                content.tables(), drawings(content.pictures()),
                content.page(), content.header(), content.footer(),
                content.defaults(), content.titlePage(),
                content.firstHeader(), content.firstFooter());

        return sealed(workbookSeq, docx, "docx", DOCX_MIME);
    }

    /**
     * 한글 문서(hwpx)로 내려받는다.
     *
     * <p>길을 따로 내는 까닭: 내주는 종류가 다르고 파일 이름 확장자가 다르다.
     * 물음표 뒤 형식으로 가르면 두 갈래가 한 함수 안에서 엉킨다.
     */
    @GetMapping(value = "/api/docs/{workbookSeq}/export/hwpx", produces = HWPX_MIME)
    public ResponseEntity<byte[]> exportHwpx(@PathVariable Long workbookSeq) {
        permissionService.requireReadWorkbook(workbookSeq);

        byte[] hwpx = hwpxWriteService.toHwpx(savedContent(workbookSeq));

        return sealed(workbookSeq, hwpx, "hwpx", HWPX_MIME);
    }

    /**
     * 담아 둔 조각을 글 한 벌로 모은다.
     *
     * <p>내보내는 길이 둘이 되면서 열 자리를 두 군데서 모으게 됐다. 한 군데서만
     * 모은다 - 한쪽에만 자리를 더하면 다른 쪽 파일에서 그것이 조용히 빠진다.
     *
     * <p>그림 알맹이는 저장소에서 한 번만 꺼낸다 - 내보내는 길이 둘이라고 두 번
     * 읽으면 큰 문서에서 그만큼 느려진다.
     */
    private DocContent savedContent(Long workbookSeq) {
        return new DocContent(paragraphMapper.selectParagraphs(workbookSeq),
                savedTables(workbookSeq), savedPictures(workbookSeq),
                savedOne(workbookSeq, IMPORT_PAGE, DocContent.DocPage.class),
                savedOne(workbookSeq, IMPORT_HEADER, DocContent.DocEdge.class),
                savedOne(workbookSeq, IMPORT_FOOTER, DocContent.DocEdge.class),
                savedResource(workbookSeq, IMPORT_TITLE_PAGE) != null,
                savedOne(workbookSeq, IMPORT_FIRST_HEADER, DocContent.DocEdge.class),
                savedOne(workbookSeq, IMPORT_FIRST_FOOTER, DocContent.DocEdge.class),
                savedOne(workbookSeq, IMPORT_DEFAULTS, DocContent.DocDefaults.class));
    }

    /** 워드 내보내기가 쓰는 그림 모양으로 바꾼다. 담긴 값은 같다. */
    private List<DocxIoService.Drawing> drawings(List<DocContent.DocPicture> pictures) {
        return pictures.stream()
                .map(one -> new DocxIoService.Drawing(one.afterParagraphNo(), one.bytes(),
                        one.width(), one.height(), one.floating(), one.behind(),
                        one.left(), one.top()))
                .toList();
    }

    /** 문서보안을 씌워 내려준다. 실패하면 막는다 - 평문으로 대신 주지 않는다. */
    private ResponseEntity<byte[]> sealed(Long workbookSeq, byte[] made,
                                          String extension, String mime) {
        String fileName = fileNameOf(workbookSeq, extension);
        byte[] sealed = drmCodec.encrypt(made, fileName, UserContext.currentAuditUser().userKey());

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(fileName, StandardCharsets.UTF_8)
                                .build().toString())
                .contentType(MediaType.parseMediaType(mime))
                .body(sealed);
    }

    /**
     * 확장자를 보고 읽는 길을 고른다.
     *
     * <p>속을 보고 알아내지 않는 이유: hwpx 는 zip 이라 docx 와 머리가 같고, hwp 는
     * OLE 라 xls 와 머리가 같다. 확장자가 더 정확하다.
     */
    private DocContent read(String fileName, byte[] plain) throws IOException {
        String extension = extensionOf(fileName);
        return switch (extension) {
            case "docx" -> docxIoService.readDocx(new ByteArrayInputStream(plain));
            case "hwp" -> hwpIoService.readHwp(plain);
            case "hwpx" -> hwpIoService.readHwpx(plain);
            default -> throw new IllegalArgumentException(
                    "글 문서로 읽을 수 있는 것은 docx, hwp, hwpx 입니다");
        };
    }

    /**
     * 담아 둔 표. 없으면 빈 목록이다.
     *
     * <p>내보내기가 문단만 싣던 때에는 표가 통째로 사라졌다(2026-08-24 지적).
     */
    private List<DocTableDto> savedTables(Long workbookSeq) {
        String saved = savedResource(workbookSeq, IMPORT_TABLES);
        if (saved == null) {
            return List.of();
        }
        try {
            return List.of(JSON.readValue(saved, DocTableDto[].class));
        } catch (RuntimeException cannotRead) {
            logger.warn("담아 둔 표를 읽지 못했다: {}", cannotRead.getMessage());
            return List.of();
        }
    }

    /** 담아 둔 그림. 알맹이는 그림 저장소에서 꺼내 온다. */
    private List<DocContent.DocPicture> savedPictures(Long workbookSeq) {
        String saved = savedResource(workbookSeq, IMPORT_IMAGES);
        if (saved == null) {
            return List.of();
        }
        try {
            List<DocContent.DocPicture> drawings = new java.util.ArrayList<>();
            for (DocImageDto placed : JSON.readValue(saved, DocImageDto[].class)) {
                Long imageSeq = imageSeqOf(placed.url());
                var row = imageSeq == null ? null : imageService.load(imageSeq);
                if (row == null || row.imageData() == null) {
                    continue;
                }
                drawings.add(new DocContent.DocPicture(placed.afterParagraphNo(),
                        row.imageData(), placed.width(), placed.height(),
                        placed.floating(), placed.behind(), placed.left(), placed.top()));
            }
            return drawings;
        } catch (RuntimeException cannotRead) {
            logger.warn("담아 둔 그림을 읽지 못했다: {}", cannotRead.getMessage());
            return List.of();
        }
    }

    /** 문서 기본 서식을 담아 둔다. 없으면 내보낼 때 워드 기본이 되어 행간이 달라진다. */
    private void saveDefaults(Long workbookSeq, DocContent.DocDefaults defaults) {
        if (defaults == null) {
            return;
        }
        resourceMapper.deleteResource(workbookSeq, IMPORT_DEFAULTS);
        resourceMapper.insertResource(workbookSeq, IMPORT_DEFAULTS,
                JSON.writeValueAsString(defaults), UserContext.currentAuditUser().userKey());
    }

    /** 담아 둔 것 하나. 없거나 못 읽으면 null 이다. */
    private <T> T savedOne(Long workbookSeq, String name, Class<T> kind) {
        String saved = savedResource(workbookSeq, name);
        if (saved == null) {
            return null;
        }
        try {
            return JSON.readValue(saved, kind);
        } catch (RuntimeException cannotRead) {
            logger.warn("담아 둔 {} 을 읽지 못했다: {}", name, cannotRead.getMessage());
            return null;
        }
    }

    /** {@code /api/images/123} 에서 번호만. 못 읽으면 null 이다. */
    private Long imageSeqOf(String url) {
        if (url == null) {
            return null;
        }
        try {
            return Long.valueOf(url.substring(url.lastIndexOf('/') + 1));
        } catch (RuntimeException notANumber) {
            return null;
        }
    }

    private String savedResource(Long workbookSeq, String name) {
        return resourceMapper.selectResources(workbookSeq).stream()
                .filter(one -> name.equals(one.name()))
                .map(one -> one.data())
                .findFirst()
                .orElse(null);
    }

    /**
     * 표 얼개를 담아 둔다.
     *
     * <p>문단 표에는 담을 자리가 없다. 화면이 문서를 지을 때 이것을 보고 표를
     * 세운다 - 칸 글자를 문단으로 펼치면 세로로 길게 늘어서 원본과 너무 달라 보인다
     * (2026-08-12 지적).
     */
    private void saveTables(Long workbookSeq, List<DocTableDto> tables) {
        if (tables.isEmpty()) {
            return;
        }
        resourceMapper.deleteResource(workbookSeq, IMPORT_TABLES);
        resourceMapper.insertResource(workbookSeq, IMPORT_TABLES,
                JSON.writeValueAsString(tables), UserContext.currentAuditUser().userKey());
    }

    /**
     * 그림을 그림 저장소에 넣고 자리만 적어 둔다.
     *
     * <p>바이트를 문서 리소스에 담지 않는 이유: 같은 그림이 여러 번 나오면 그만큼
     * 다시 실리고, 리소스는 문서를 열 때마다 통째로 오간다. 저장소는 (문서, 해시)로
     * 같은 그림을 하나로 묶는다.
     */
    private void savePictures(Long workbookSeq, List<DocContent.DocPicture> pictures) {
        if (pictures.isEmpty()) {
            return;
        }
        List<DocImageDto> placed = new java.util.ArrayList<>();
        for (DocContent.DocPicture picture : pictures) {
            Long imageSeq = imageService.store(workbookSeq, picture.bytes(),
                    UserContext.currentAuditUser());
            placed.add(new DocImageDto(picture.afterParagraphNo(), "/api/images/" + imageSeq,
                    picture.width(), picture.height(), picture.floating(), picture.behind(),
                    picture.left(), picture.top(), picture.fromH(), picture.fromV()));
        }
        resourceMapper.deleteResource(workbookSeq, IMPORT_IMAGES);
        resourceMapper.insertResource(workbookSeq, IMPORT_IMAGES,
                JSON.writeValueAsString(placed), UserContext.currentAuditUser().userKey());
    }

    /**
     * 종이 크기와 여백을 담아 둔다.
     *
     * <p>여백이 다르면 본문 폭이 달라져 표와 그림이 원본과 다른 자리에 앉는다
     * (2026-08-13 지적: 표 크기가 다르다).
     */
    private void savePage(Long workbookSeq, DocContent.DocPage page) {
        if (page == null) {
            return;
        }
        resourceMapper.deleteResource(workbookSeq, IMPORT_PAGE);
        resourceMapper.insertResource(workbookSeq, IMPORT_PAGE,
                JSON.writeValueAsString(page), UserContext.currentAuditUser().userKey());
    }

    /** 쪽마다 되풀이되는 글을 담아 둔다. 없으면 아무것도 안 남긴다. */
    private void saveEdge(Long workbookSeq, String name, DocContent.DocEdge edge) {
        if (edge == null) {
            return;
        }
        resourceMapper.deleteResource(workbookSeq, name);
        resourceMapper.insertResource(workbookSeq, name,
                JSON.writeValueAsString(edge), UserContext.currentAuditUser().userKey());
    }

    private String extensionOf(String fileName) {
        if (fileName == null) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(java.util.Locale.ROOT);
    }

    /**
     * 내려받을 파일 이름. 문서 제목에 {@code .docx} 를 붙인다.
     *
     * <p>규칙은 xlsx 와 같다 - 윈도우가 못 쓰는 글자를 바꾸고 끝의 점과 공백을 뗀다.
     */
    private String fileNameOf(Long workbookSeq, String extension) {
        String name = workbookService.getWorkbook(workbookSeq).workbookName();
        if (name == null) {
            name = "";
        }
        name = name.replaceAll("[\\\\/:*?\"<>|]", "_");
        name = name.replaceAll("^[\\s.]+|[\\s.]+$", "");
        return (name.isEmpty() ? "문서" : name) + "." + extension;
    }

    /** 확장자는 제목에 넣지 않는다. 제목은 사람이 읽는 이름이다(시트에서 겪었다). */
    private String titleOf(String fileName) {
        if (fileName == null) {
            return null;
        }
        int dot = fileName.lastIndexOf('.');
        return dot <= 0 ? fileName : fileName.substring(0, dot);
    }
}
