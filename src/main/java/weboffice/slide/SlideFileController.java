package weboffice.slide;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import weboffice.drawing.ImageService;
import weboffice.drm.DrmCodec;
import weboffice.security.UserContext;
import weboffice.workbook.DocType;
import weboffice.workbook.WorkbookPermissionService;
import weboffice.workbook.WorkbookResourceMapper;
import weboffice.workbook.WorkbookService;

/**
 * pptx 올리기와 보여 주기.
 *
 * <p>고쳐 쓰기는 없다. 슬라이드에 무엇이 적혀 있는지 보여 주기만 한다(2026-08-13
 * 결정). 그래서 슬라이드는 문단·셀 표에 담지 않고 리소스 한 덩어리로 둔다 - 고칠
 * 일이 없으니 나눠 담을 까닭도 없다.
 */
@RestController
public class SlideFileController {

    /** 슬라이드 한 벌을 담는 이름표. */
    private static final String IMPORT_SLIDES = "WO_IMPORT_SLIDES";

    private static final tools.jackson.databind.json.JsonMapper JSON =
            tools.jackson.databind.json.JsonMapper.builder().build();

    private final PptxIoService pptxIoService;
    private final WorkbookService workbookService;
    private final WorkbookPermissionService permissionService;
    private final WorkbookResourceMapper resourceMapper;
    private final ImageService imageService;
    private final DrmCodec drmCodec;

    public SlideFileController(PptxIoService pptxIoService, WorkbookService workbookService,
                               WorkbookPermissionService permissionService,
                               WorkbookResourceMapper resourceMapper,
                               ImageService imageService, DrmCodec drmCodec) {
        this.pptxIoService = pptxIoService;
        this.workbookService = workbookService;
        this.permissionService = permissionService;
        this.resourceMapper = resourceMapper;
        this.imageService = imageService;
        this.drmCodec = drmCodec;
    }

    /** 화면이 받는 슬라이드 한 벌. 그림은 알맹이 대신 주소로 담는다. */
    public record SlideDeckView(double width, double height, List<SlideView> slides,
                                int firstSlideNo) {

        public SlideDeckView(double width, double height, List<SlideView> slides) {
            this(width, height, slides, SlideDeck.FIRST_SLIDE_NO);
        }
    }

    public record SlideView(int slideNo, List<SlideDeck.SlideText> texts,
                            List<SlideDeck.SlideTable> tables, List<PlacedPicture> pictures,
                            String notes, List<PlacedShape> shapes,
                            String background, String backgroundImage) {
    }

    /**
     * 도형 하나. 깔아 둔 그림은 알맹이 대신 주소로 담는다.
     *
     * <p>{@link SlideDeck.SlideShape} 와 같되 {@code image} 만 주소다 - 리소스는 열
     * 때마다 통째로 오가는데 그림 알맹이까지 실으면 그 덩어리가 몇 MB 가 된다.
     */
    public record PlacedShape(double left, double top, double width, double height,
                              String fill, String line, double lineWidth, String shape,
                              double rotation, List<SlideDeck.ColorStop> gradient,
                              double gradientAngle, String image, double corner,
                              List<SlideDeck.Point> outline) {
    }

    /** 그림 자리. 알맹이는 그림 저장소에 있다. */
    public record PlacedPicture(String url, double left, double top, double width,
                                double height) {
    }

    @PostMapping("/api/slides/import")
    @Transactional
    public Map<String, Object> importPptx(@RequestParam("file") MultipartFile file)
            throws IOException {
        String fileName = file.getOriginalFilename();
        if (!extensionOf(fileName).equals("pptx")) {
            throw new IllegalArgumentException("발표 자료로 읽을 수 있는 것은 pptx 입니다");
        }
        byte[] plain = drmCodec.decrypt(file.getBytes(), fileName);
        SlideDeck deck = pptxIoService.readPptx(new ByteArrayInputStream(plain));

        // 올린 사람이 소유자가 된다. 권한 검사가 없는 이유가 이것이다.
        var created = workbookService.createBlank(titleOf(fileName), DocType.SLIDE,
                UserContext.currentAuditUser());
        saveDeck(created.workbookSeq(), deck);

        return Map.of("workbookSeq", created.workbookSeq(),
                "workbookName", created.workbookName(),
                "docType", created.docType(),
                "slideCount", deck.slides().size());
    }

    @GetMapping("/api/slides/{workbookSeq}")
    public SlideDeckView readDeck(@PathVariable Long workbookSeq) {
        permissionService.requireReadWorkbook(workbookSeq);

        var saved = resourceMapper.selectResources(workbookSeq).stream()
                .filter(one -> IMPORT_SLIDES.equals(one.name()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("발표 자료가 없습니다"));
        return JSON.readValue(saved.data(), SlideDeckView.class);
    }

    /**
     * 슬라이드를 담아 둔다.
     *
     * <p>그림 알맹이는 그림 저장소로 보낸다. 리소스는 열 때마다 통째로 오가는데
     * 슬라이드 그림까지 실으면 그 덩어리가 몇 MB 가 된다.
     */
    private void saveDeck(Long workbookSeq, SlideDeck deck) {
        List<SlideView> slides = new ArrayList<>();
        for (SlideDeck.Slide slide : deck.slides()) {
            List<PlacedPicture> pictures = new ArrayList<>();
            for (SlideDeck.SlidePicture picture : slide.pictures()) {
                Long imageSeq = imageService.store(workbookSeq, picture.bytes(),
                        UserContext.currentAuditUser());
                pictures.add(new PlacedPicture("/api/images/" + imageSeq, picture.left(),
                        picture.top(), picture.width(), picture.height()));
            }
            List<PlacedShape> shapes = new ArrayList<>();
            for (SlideDeck.SlideShape shape : slide.shapes()) {
                String image = null;
                if (shape.image() != null && shape.image().length > 0) {
                    Long imageSeq = imageService.store(workbookSeq, shape.image(),
                            UserContext.currentAuditUser());
                    image = "/api/images/" + imageSeq;
                }
                shapes.add(new PlacedShape(shape.left(), shape.top(), shape.width(),
                        shape.height(), shape.fill(), shape.line(), shape.lineWidth(),
                        shape.shape(), shape.rotation(), shape.gradient(),
                        shape.gradientAngle(), image, shape.corner(), shape.outline()));
            }
            String paper = null;
            if (slide.backgroundImage() != null && slide.backgroundImage().length > 0) {
                Long imageSeq = imageService.store(workbookSeq, slide.backgroundImage(),
                        UserContext.currentAuditUser());
                paper = "/api/images/" + imageSeq;
            }
            slides.add(new SlideView(slide.slideNo(), slide.texts(), slide.tables(),
                    pictures, slide.notes(), shapes, slide.background(), paper));
        }

        resourceMapper.deleteResource(workbookSeq, IMPORT_SLIDES);
        resourceMapper.insertResource(workbookSeq, IMPORT_SLIDES,
                JSON.writeValueAsString(new SlideDeckView(deck.width(), deck.height(),
                        slides, deck.firstSlideNo())),
                UserContext.currentAuditUser().userKey());
    }

    private String extensionOf(String fileName) {
        if (fileName == null) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /** 파일 이름에서 확장자를 뗀 것이 문서 제목이다. */
    private String titleOf(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return null;
        }
        int dot = fileName.lastIndexOf('.');
        return dot <= 0 ? fileName : fileName.substring(0, dot);
    }
}
