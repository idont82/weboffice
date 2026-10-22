package weboffice.slide;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFGroupShape;
import org.apache.poi.xslf.usermodel.XSLFNotes;
import org.apache.poi.xslf.usermodel.XSLFPictureShape;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTable;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.springframework.stereotype.Service;

/**
 * pptx 읽기.
 *
 * <p>보여 주기만 한다. 슬라이드를 고쳐 쓰는 화면은 없다 - 쓸 만한 오픈소스 편집기가
 * Apache/MIT 계열에 없고, Univer 슬라이드는 만드는 중이라 표도 발표 모드도 없다
 * (2026-08-13 조사).
 *
 * <p>애니메이션과 화면 전환은 읽지 않는다. POI 에 그것을 다루는 갈래가 아예 없다.
 * SmartArt 도 속을 못 본다 - 그림 틀로만 나온다.
 */
@Service
public class PptxIoService {

    /** 파워포인트가 쪽 번호 자리에 적어 두는 기호. */
    private static final String SLIDE_NUMBER = "‹#›";

    /** POI 는 길이를 포인트로 준다. 화면은 픽셀(96dpi)이다. */
    private static final double PIXELS_PER_POINT = 96.0 / 72.0;

    public SlideDeck readPptx(InputStream in) throws IOException {
        try (XMLSlideShow show = new XMLSlideShow(in)) {
            var size = show.getPageSize();
            List<SlideDeck.Slide> slides = new ArrayList<>();
            int slideNo = 0;
            int firstSlideNo = firstSlideNoOf(show);
            for (XSLFSlide slide : show.getSlides()) {
                slides.add(read(slide, slideNo++, firstSlideNo));
            }
            return new SlideDeck(px(size.getWidth()), px(size.getHeight()), slides,
                    firstSlideNoOf(show));
        }
    }

    /**
     * 묶음(그룹) 안 좌표를 슬라이드 좌표로 옮기는 자.
     *
     * <p>묶음 안 도형은 묶음만의 좌표를 쓴다 - 묶음을 줄이거나 늘려도 속은 그대로
     * 두려는 것이다. 그대로 읽으면 엉뚱한 자리에 찍힌다(2026-08-21 지적).
     */
    private record Frame(double offsetX, double offsetY, double scaleX, double scaleY) {

        /** 묶음 밖. 슬라이드 좌표 그대로다. */
        static final Frame WHOLE = new Frame(0, 0, 1, 1);

        double x(double value) {
            return offsetX + value * scaleX;
        }

        double y(double value) {
            return offsetY + value * scaleY;
        }

        double wide(double value) {
            return value * scaleX;
        }

        double high(double value) {
            return value * scaleY;
        }

        /** 이 자리 안에 다시 묶음이 있을 때. 바깥 것을 안쪽 것에 먹인다. */
        Frame inside(Frame inner) {
            return new Frame(x(inner.offsetX), y(inner.offsetY),
                    scaleX * inner.scaleX, scaleY * inner.scaleY);
        }
    }

    /** 묶음 하나가 만드는 좌표 옮김. 속 좌표(chOff·chExt)를 겉 자리로 편다. */
    private Frame frameOf(XSLFGroupShape group) {
        var outer = group.getAnchor();
        var inner = group.getInteriorAnchor();
        if (inner == null || inner.getWidth() == 0 || inner.getHeight() == 0) {
            return Frame.WHOLE;
        }
        double scaleX = outer.getWidth() / inner.getWidth();
        double scaleY = outer.getHeight() / inner.getHeight();
        return new Frame(outer.getX() - inner.getX() * scaleX,
                outer.getY() - inner.getY() * scaleY, scaleX, scaleY);
    }

    /**
     * 첫 장에 매길 쪽 번호.
     *
     * <p>표지를 0쪽으로 두는 자료가 흔하다. 자료마다 다르니 적힌 값을 따르고,
     * 안 적혀 있으면 1로 본다(2026-08-21 지적).
     */
    private int firstSlideNoOf(XMLSlideShow show) {
        try {
            var written = show.getCTPresentation();
            return written != null && written.isSetFirstSlideNum()
                    ? written.getFirstSlideNum() : SlideDeck.FIRST_SLIDE_NO;
        } catch (RuntimeException cannotRead) {
            return SlideDeck.FIRST_SLIDE_NO;
        }
    }

    private SlideDeck.Slide read(XSLFSlide slide, int slideNo, int firstSlideNo) {
        List<SlideDeck.SlideText> texts = new ArrayList<>();
        List<SlideDeck.SlideTable> tables = new ArrayList<>();
        List<SlideDeck.SlidePicture> pictures = new ArrayList<>();
        List<SlideDeck.SlideShape> shapes = new ArrayList<>();
        // 밑그림이 먼저다. 마스터 → 레이아웃 → 슬라이드 차례로 얹는다. 꼬릿글·쪽
        // 번호·회사 로고는 마스터에, 배너와 띠는 레이아웃에 있어서 슬라이드만 보면
        // 그것들이 모든 장에서 통째로 빠진다(2026-08-21 지적).
        collectBase(slide.getSlideMaster(), firstSlideNo + slideNo, texts, pictures, shapes);
        collectBase(slide.getSlideLayout(), firstSlideNo + slideNo, texts, pictures, shapes);
        for (XSLFShape shape : slide.getShapes()) {
            collect(shape, texts, tables, pictures, shapes, Frame.WHOLE);
        }
        return new SlideDeck.Slide(slideNo, texts, tables, pictures, notesOf(slide),
                shapes, backgroundOf(slide), backgroundImageOf(slide));
    }

    /**
     * 밑그림(마스터·레이아웃)의 꾸밈을 담는다.
     *
     * <p>제목·본문 자리표시자는 건너뛴다 - 그 자리의 글은 슬라이드가 채운다.
     * 꼬릿글·쪽 번호처럼 밑그림이 직접 적어 둔 글은 담는다.
     */
    private void collectBase(org.apache.poi.xslf.usermodel.XSLFSheet sheet, int pageNo,
                             List<SlideDeck.SlideText> texts,
                             List<SlideDeck.SlidePicture> pictures,
                             List<SlideDeck.SlideShape> shapes) {
        if (sheet == null) {
            return;
        }
        for (XSLFShape shape : sheet.getShapes()) {
            if (shape instanceof XSLFTextShape written && fillsFromSlide(written)) {
                continue;
            }
            List<SlideDeck.SlideText> mine = new ArrayList<>();
            collect(shape, mine, new ArrayList<>(), pictures, shapes, Frame.WHOLE);
            for (SlideDeck.SlideText one : mine) {
                // 쪽 번호 자리에는 특별한 기호가 적혀 있다. 그대로 두면 모든 장에
                // 그 기호가 뜬다.
                texts.add(one.text().contains(SLIDE_NUMBER)
                        ? new SlideDeck.SlideText(
                                one.text().replace(SLIDE_NUMBER, String.valueOf(pageNo)),
                                one.left(), one.top(), one.width(), one.height(),
                                one.fontSize(), one.bold(), one.color())
                        : one);
            }
        }
    }

    /**
     * 슬라이드가 채우는 자리인가. 밑그림에서는 건너뛴다.
     *
     * <p>제목·본문뿐 아니라 날짜·머리글·꼬릿글·쪽 번호도 여기 든다. 파워포인트는
     * [삽입 &gt; 머리글/바닥글] 로 켠 장에만 그 글을 슬라이드 안에 적어 넣고 그린다.
     * 밑그림에 남은 빈 자리를 담으면 만든 날짜가 모든 장에 뜬다(2026-08-21 지적).
     */
    private boolean fillsFromSlide(XSLFTextShape shape) {
        var type = shape.getTextType();
        return type == org.apache.poi.sl.usermodel.Placeholder.TITLE
                || type == org.apache.poi.sl.usermodel.Placeholder.CENTERED_TITLE
                || type == org.apache.poi.sl.usermodel.Placeholder.SUBTITLE
                || type == org.apache.poi.sl.usermodel.Placeholder.BODY
                || type == org.apache.poi.sl.usermodel.Placeholder.CONTENT
                || type == org.apache.poi.sl.usermodel.Placeholder.DATETIME
                || type == org.apache.poi.sl.usermodel.Placeholder.HEADER
                || type == org.apache.poi.sl.usermodel.Placeholder.FOOTER
                || type == org.apache.poi.sl.usermodel.Placeholder.SLIDE_NUMBER;
    }

    /**
     * 슬라이드 바탕색.
     *
     * <p>슬라이드에 없으면 그 장이 쓰는 밑그림(레이아웃), 그것도 없으면 마스터를
     * 본다. 파워포인트가 칠하는 차례와 같다.
     */
    private String backgroundOf(XSLFSlide slide) {
        String own = fillOf(slide);
        if (own != null) {
            return own;
        }
        String layout = fillOf(slide.getSlideLayout());
        return layout != null ? layout : fillOf(slide.getSlideMaster());
    }

    /**
     * 바탕에 깐 그림.
     *
     * <p>표지와 머리 띠를 통째로 그림 한 장으로 깐 발표 자료가 흔하다. 색만 보면
     * 흰 종이에 글씨만 남는다(2026-08-21 지적). 슬라이드 → 레이아웃 → 마스터
     * 차례로 찾는다. 파워포인트가 칠하는 차례와 같다.
     */
    private byte[] backgroundImageOf(XSLFSlide slide) {
        byte[] own = pictureFillOf(slide);
        if (own != null) {
            return own;
        }
        byte[] layout = pictureFillOf(slide.getSlideLayout());
        return layout != null ? layout : pictureFillOf(slide.getSlideMaster());
    }

    /**
     * 손으로 그린 도형(custGeom)의 꼭짓점.
     *
     * <p>머리 띠를 사다리꼴로 깎아 쓴 발표 자료가 많다. 네모로 그리면 비스듬한
     * 모서리가 사라진다(2026-08-21 지적). 곧은 선만 읽는다 - 곡선이 섞이면
     * 어설프게 흉내 내느니 네모로 둔다.
     */
    private List<SlideDeck.Point> outlineOf(
            org.apache.poi.xslf.usermodel.XSLFAutoShape shape) {
        try {
            var properties = shape.getXmlObject() instanceof org.openxmlformats.schemas
                    .presentationml.x2006.main.CTShape drawn ? drawn.getSpPr() : null;
            if (properties == null || !properties.isSetCustGeom()) {
                return List.of();
            }
            var paths = properties.getCustGeom().getPathLst();
            if (paths == null || paths.sizeOfPathArray() != 1) {
                return List.of();
            }
            var path = paths.getPathArray(0);
            // 곡선이 섞였거나 획이 둘 이상이면 순서를 우리가 다시 세울 수 없다.
            if (path.sizeOfCubicBezToArray() > 0 || path.sizeOfArcToArray() > 0
                    || path.sizeOfQuadBezToArray() > 0
                    || path.sizeOfMoveToArray() != 1) {
                return List.of();
            }
            double wide = path.getW();
            double high = path.getH();
            if (wide <= 0 || high <= 0) {
                return List.of();
            }
            List<SlideDeck.Point> corners = new ArrayList<>();
            for (var move : path.getMoveToList()) {
                corners.add(pointOf(move.getPt(), wide, high));
            }
            for (var straight : path.getLnToList()) {
                corners.add(pointOf(straight.getPt(), wide, high));
            }
            return corners.size() < 3 ? List.of() : List.copyOf(corners);
        } catch (RuntimeException cannotRead) {
            return List.of();
        }
    }

    /** 꼭짓점 하나를 도형 크기에 대한 비율로 바꾼다. */
    private SlideDeck.Point pointOf(
            org.openxmlformats.schemas.drawingml.x2006.main.CTAdjPoint2D corner,
            double wide, double high) {
        // 0 은 아예 안 적기도 한다.
        double x = corner.getX() == null ? 0
                : Double.parseDouble(String.valueOf(corner.getX()));
        double y = corner.getY() == null ? 0
                : Double.parseDouble(String.valueOf(corner.getY()));
        return new SlideDeck.Point(Math.round(x / wide * 1000) / 1000.0,
                Math.round(y / high * 1000) / 1000.0);
    }

    /** 그 장의 바탕에 깔린 그림 알맹이. 없으면 null 이다. */
    private byte[] pictureFillOf(org.apache.poi.xslf.usermodel.XSLFSheet sheet) {
        try {
            var properties = backgroundPropertiesOf(sheet);
            if (properties == null || !properties.isSetBlipFill()) {
                return null;
            }
            String id = properties.getBlipFill().getBlip().getEmbed();
            var part = sheet.getRelationById(id);
            return part instanceof org.apache.poi.xslf.usermodel.XSLFPictureData picture
                    ? picture.getData() : null;
        } catch (RuntimeException cannotRead) {
            return null;
        }
    }

    /** 그 장의 바탕 꾸밈. 슬라이드·레이아웃·마스터가 저마다 다른 자리에 적는다. */
    private org.openxmlformats.schemas.presentationml.x2006.main.CTBackgroundProperties
            backgroundPropertiesOf(org.apache.poi.xslf.usermodel.XSLFSheet sheet) {
        if (sheet == null) {
            return null;
        }
        var common = switch (sheet.getXmlObject()) {
            case org.openxmlformats.schemas.presentationml.x2006.main.CTSlide one ->
                    one.getCSld();
            case org.openxmlformats.schemas.presentationml.x2006.main.CTSlideLayout one ->
                    one.getCSld();
            case org.openxmlformats.schemas.presentationml.x2006.main.CTSlideMaster one ->
                    one.getCSld();
            default -> null;
        };
        return common == null || common.getBg() == null ? null : common.getBg().getBgPr();
    }

    private String fillOf(org.apache.poi.xslf.usermodel.XSLFSheet sheet) {
        try {
            var background = sheet == null ? null : sheet.getBackground();
            return background == null ? null : rgb(background.getFillColor());
        } catch (RuntimeException cannotRead) {
            return null;
        }
    }

    /** 자바 색을 여섯 자리 색값으로. 없으면 null 이다. */
    private String rgb(java.awt.Color color) {
        return color == null ? null
                : String.format("#%02x%02x%02x", color.getRed(), color.getGreen(),
                        color.getBlue());
    }

    /**
     * 도형 하나를 갈래에 나눠 담는다.
     *
     * <p>묶음 도형은 속을 다시 훑는다. 묶었다고 글이 사라지면 보여 줄 것이 없다.
     */
    private void collect(XSLFShape shape, List<SlideDeck.SlideText> texts,
                         List<SlideDeck.SlideTable> tables,
                         List<SlideDeck.SlidePicture> pictures,
                         List<SlideDeck.SlideShape> shapes, Frame frame) {
        if (shape instanceof XSLFGroupShape group) {
            Frame inside = frame.inside(frameOf(group));
            for (XSLFShape inner : group.getShapes()) {
                collect(inner, texts, tables, pictures, shapes, inside);
            }
            return;
        }
        if (shape instanceof XSLFTable table) {
            tables.add(tableOf(table, frame));
            return;
        }
        if (shape instanceof XSLFPictureShape picture) {
            var box = picture.getAnchor();
            pictures.add(new SlideDeck.SlidePicture(picture.getPictureData().getData(),
                    px(frame.x(box.getX())), px(frame.y(box.getY())),
                    px(frame.wide(box.getWidth())), px(frame.high(box.getHeight()))));
            return;
        }
        if (shape instanceof org.apache.poi.xslf.usermodel.XSLFAutoShape drawn) {
            // 색부터 담고 글은 그 위에 얹는다 - 한 도형이 바탕이면서 글상자인
            // 경우가 발표 자료에는 흔하다.
            var painted = shapeOf(drawn, frame);
            if (painted != null) {
                shapes.add(painted);
            }
        }
        if (shape instanceof XSLFTextShape text) {
            var written = textOf(text, frame);
            if (written != null) {
                texts.add(written);
            }
        }
    }

    /** 색을 칠했거나 선을 그은 도형. 아무것도 없으면 null 이다(그릴 것이 없다). */
    private SlideDeck.SlideShape shapeOf(org.apache.poi.xslf.usermodel.XSLFAutoShape shape,
                                         Frame frame) {
        String fill;
        String line = null;
        double lineWidth = 0;
        List<SlideDeck.ColorStop> gradient = List.of();
        double gradientAngle = 0;
        byte[] image = null;
        try {
            fill = rgb(shape.getFillColor());
            line = rgb(shape.getLineColor());
            lineWidth = shape.getLineWidth();
            var properties = shape.getXmlObject() instanceof org.openxmlformats.schemas
                    .presentationml.x2006.main.CTShape drawn ? drawn.getSpPr() : null;
            if (properties != null && properties.isSetGradFill()) {
                gradient = stopsOf(properties.getGradFill(), shape.getSheet());
                gradientAngle = angleOf(properties.getGradFill());
            } else if (properties != null && properties.isSetBlipFill()) {
                image = imageOf(shape, properties.getBlipFill());
            } else if (fill == null && properties != null && properties.isSetPattFill()) {
                // 무늬는 못 그린다. 앞색만 단색으로 받아 온다 - 비워 두는 것보다 낫다.
                var pattern = properties.getPattFill();
                fill = pattern.isSetFgClr() && pattern.getFgClr().isSetSrgbClr()
                        ? hex(pattern.getFgClr().getSrgbClr().getVal()) : null;
            }
        } catch (RuntimeException cannotRead) {
            // 우리가 못 읽는 채우기다. 색 없이 둔다.
            fill = null;
        }
        if (fill == null && line == null && gradient.isEmpty() && image == null) {
            return null;
        }
        var box = shape.getAnchor();
        return new SlideDeck.SlideShape(px(frame.x(box.getX())), px(frame.y(box.getY())),
                px(frame.wide(box.getWidth())), px(frame.high(box.getHeight())), fill, line,
                px(Math.max(0, lineWidth)), nameOf(shape.getShapeType()),
                Math.round(shape.getRotation() * 10) / 10.0, gradient, gradientAngle,
                image, cornerOf(shape), outlineOf(shape));
    }

    /**
     * 둥근 네모의 모서리 비율.
     *
     * <p>파워포인트는 짧은 변의 십만분율로 적는다(16667 이 기본, 곧 16.7%다).
     * 안 적혀 있으면 그 기본값을 쓴다.
     */
    private double cornerOf(org.apache.poi.xslf.usermodel.XSLFAutoShape shape) {
        if (shape.getShapeType() != org.apache.poi.sl.usermodel.ShapeType.ROUND_RECT) {
            return 0;
        }
        try {
            var drawn = shape.getXmlObject() instanceof org.openxmlformats.schemas
                    .presentationml.x2006.main.CTShape one ? one : null;
            var geometry = drawn == null || drawn.getSpPr() == null
                    ? null : drawn.getSpPr().getPrstGeom();
            if (geometry != null && geometry.isSetAvLst()) {
                for (var guide : geometry.getAvLst().getGdList()) {
                    if ("adj".equals(guide.getName())) {
                        // 십만분율이다. 5000 이 5% 다.
                        return Math.round(Double.parseDouble(
                                guide.getFmla().replace("val ", "")) / 100) / 1000.0;
                    }
                }
            }
        } catch (RuntimeException cannotRead) {
            // 값이 수식이거나 우리가 못 읽는 모양이다. 기본값을 쓴다.
        }
        return DEFAULT_CORNER;
    }

    /** 파워포인트가 둥근 네모에 쓰는 기본 모서리 비율. */
    private static final double DEFAULT_CORNER = 0.167;

    /**
     * 도형에 깔아 둔 그림.
     *
     * <p>그림은 슬라이드가 아니라 딸림 자료로 붙어 있다. 관계 이름(r:embed)으로 찾는다.
     */
    private byte[] imageOf(org.apache.poi.xslf.usermodel.XSLFAutoShape shape,
                           org.openxmlformats.schemas.drawingml.x2006.main
                                   .CTBlipFillProperties fill) {
        try {
            if (fill.getBlip() == null || fill.getBlip().getEmbed() == null) {
                return null;
            }
            var part = shape.getSheet().getRelationById(fill.getBlip().getEmbed());
            return part instanceof org.apache.poi.xslf.usermodel.XSLFPictureData data
                    ? data.getData() : null;
        } catch (RuntimeException cannotRead) {
            return null;
        }
    }

    /**
     * 번지는 색의 자리들.
     *
     * <p>여섯 자리 색만 읽는다. 테마 색(schemeClr)은 밑그림·테마를 따라가야 하는데
     * 그 자리까지 파면 값이 어긋날 때 더 이상해진다 - 그런 도형은 색 없이 둔다.
     *
     * <p>파워포인트는 자리를 십만분율로 적는다(100000 이 끝이다).
     */
    private List<SlideDeck.ColorStop> stopsOf(
            org.openxmlformats.schemas.drawingml.x2006.main.CTGradientFillProperties spread,
            org.apache.poi.xslf.usermodel.XSLFSheet sheet) {
        if (spread == null || spread.getGsLst() == null) {
            return List.of();
        }
        List<SlideDeck.ColorStop> stops = new ArrayList<>();
        for (var stop : spread.getGsLst().getGsList()) {
            String color = stop.isSetSrgbClr() ? hex(stop.getSrgbClr().getVal())
                    : themeColorOf(stop, sheet);
            if (color == null) {
                continue;
            }
            double at = Double.parseDouble(String.valueOf(stop.getPos()));
            stops.add(new SlideDeck.ColorStop(color, Math.round(at / 1000.0) / 100.0));
        }
        return stops.size() < 2 ? List.of() : stops;
    }

    /**
     * 테마 이름으로 적은 색(accent2 처럼)을 실제 색으로 푼다.
     *
     * <p>파워포인트 기본 도형 스타일이 이렇게 적는다. 이름을 못 풀면 그 도형이
     * 통째로 사라진다(2026-08-21 지적). 밝기·채도 손질까지 POI 가 해 준다.
     */
    private String themeColorOf(org.apache.xmlbeans.XmlObject holder,
                                org.apache.poi.xslf.usermodel.XSLFSheet sheet) {
        try {
            if (sheet == null || sheet.getTheme() == null) {
                return null;
            }
            return rgb(new org.apache.poi.xslf.usermodel.XSLFColor(holder,
                    sheet.getTheme(), null, sheet).getColor());
        } catch (RuntimeException cannotRead) {
            return null;
        }
    }

    /** 색이 번지는 방향(도). 파워포인트는 6만분의 1도로 적는다. */
    private double angleOf(
            org.openxmlformats.schemas.drawingml.x2006.main.CTGradientFillProperties spread) {
        if (spread == null || spread.getLin() == null) {
            return 0;
        }
        double turns = Double.parseDouble(String.valueOf(spread.getLin().getAng()));
        return Math.round(turns / 60000.0 * 10) / 10.0;
    }

    /** 바이트 세 개를 여섯 자리 색값으로. */
    private String hex(Object value) {
        if (!(value instanceof byte[] bytes) || bytes.length < 3) {
            return null;
        }
        return String.format("#%02x%02x%02x", bytes[0] & 0xff, bytes[1] & 0xff,
                bytes[2] & 0xff);
    }

    /**
     * 도형 모양 이름.
     *
     * <p>화면이 그릴 줄 아는 것만 이름으로 준다. 나머지는 네모다 - 파워포인트에는
     * 수백 가지 모양이 있고, 어설프게 흉내 낸 모양은 네모보다 더 어긋나 보인다.
     */
    private String nameOf(org.apache.poi.sl.usermodel.ShapeType type) {
        if (type == null) {
            return "rect";
        }
        return switch (type) {
            case ELLIPSE -> "ellipse";
            case ROUND_RECT -> "roundRect";
            case TRIANGLE -> "triangle";
            case RT_TRIANGLE -> "rtTriangle";
            case DIAMOND -> "diamond";
            case PENTAGON -> "pentagon";
            case HEXAGON -> "hexagon";
            case STAR_5 -> "star5";
            case RIGHT_ARROW -> "rightArrow";
            case LEFT_ARROW -> "leftArrow";
            case UP_ARROW -> "upArrow";
            case DOWN_ARROW -> "downArrow";
            case CHEVRON -> "chevron";
            case PARALLELOGRAM -> "parallelogram";
            case TRAPEZOID -> "trapezoid";
            case PLUS -> "plus";
            case LINE, STRAIGHT_CONNECTOR_1 -> "line";
            default -> "rect";
        };
    }

    /** 글상자 하나. 글이 없는 도형은 보여 줄 것이 없어 버린다. */
    private SlideDeck.SlideText textOf(XSLFTextShape shape, Frame frame) {
        String written = shape.getText();
        if (written == null || written.isBlank()) {
            return null;
        }
        // 줄마다 크기가 갈리는 상자가 흔하다(제목 밑에 작은 설명). 첫 줄 크기만
        // 상자 전체에 씌우면 원본과 크기가 어긋난다(2026-08-21 지적).
        var lines = new ArrayList<SlideDeck.SlideLine>();
        for (var paragraph : shape.getTextParagraphs()) {
            var run = firstWritten(paragraph);
            if (run == null) {
                continue;
            }
            lines.add(new SlideDeck.SlideLine(paragraph.getText(),
                    run.getFontSize() == null ? 0 : run.getFontSize(),
                    run.isBold(), colorOf(run), fontOf(run), bulletOf(paragraph),
                    px(paragraph.getLeftMargin()), -px(paragraph.getIndent()),
                    alignOf(paragraph)));
        }
        // 상자 전체의 모양은 첫 줄 것으로 본다. 줄을 하나하나 그리지 못하는 데서도
        // 지금까지처럼 보이게 하려는 것이다.
        var head = lines.isEmpty() ? null : lines.get(0);
        var box = shape.getAnchor();
        return new SlideDeck.SlideText(written, px(frame.x(box.getX())),
                px(frame.y(box.getY())), px(frame.wide(box.getWidth())),
                px(frame.high(box.getHeight())),
                head == null ? 0 : head.fontSize(), head != null && head.bold(),
                head == null ? null : head.color(), List.copyOf(lines), shrinks(shape),
                head == null ? null : head.font(), anchorOf(shape),
                Math.round(shape.getRotation() * 10) / 10.0,
                head == null ? null : head.align(), paddingOf(shape));
    }

    /**
     * 상자 안쪽 여백.
     *
     * <p>파워포인트 글상자는 기본으로도 안쪽에 여백을 둔다. 흘리면 글이 테두리에
     * 붙어 원본보다 왼쪽·위로 밀린다(2026-08-21 지적).
     */
    private SlideDeck.Padding paddingOf(XSLFTextShape shape) {
        try {
            return new SlideDeck.Padding(px(shape.getLeftInset()), px(shape.getTopInset()),
                    px(shape.getRightInset()), px(shape.getBottomInset()));
        } catch (RuntimeException cannotRead) {
            return SlideDeck.Padding.NONE;
        }
    }

    /**
     * 상자 안 세로 맞춤.
     *
     * <p>가운데 맞춤인 상자는 글이 상자보다 크면 위아래로 고루 넘친다. 위 맞춤으로
     * 그리면 아래로만 흘러 밑에 있는 것을 덮는다(2026-08-21 지적).
     */
    private String anchorOf(XSLFTextShape shape) {
        try {
            return switch (shape.getVerticalAlignment()) {
                case MIDDLE -> "center";
                case BOTTOM -> "bottom";
                default -> null;
            };
        } catch (RuntimeException cannotRead) {
            return null;
        }
    }

    /**
     * 글머리 기호. 없으면 null 이다.
     *
     * <p>발표 자료 본문은 거의 이 기호가 붙은 목록이다. 흘리면 그냥 줄바꿈한
     * 글로 보인다(2026-08-21 지적).
     */
    private String bulletOf(org.apache.poi.xslf.usermodel.XSLFTextParagraph paragraph) {
        try {
            if (!paragraph.isBullet()) {
                return null;
            }
            String mark = paragraph.getBulletCharacter();
            if (mark == null || mark.isBlank()) {
                return "•";
            }
            return readable(mark, paragraph.getBulletFont());
        } catch (RuntimeException cannotRead) {
            return null;
        }
    }

    /** 그림 글꼴 글머리를 눈에 보이는 기호로 바꾼 표. 그 글꼴이 없는 PC 를 위한 것이다. */
    private static final java.util.Map<String, String> WINGDINGS = java.util.Map.of(
            "§", "▪",   // 작은 검은 네모
            "n", "■",         // 검은 네모
            "l", "●",         // 검은 동그라미
            "u", "❑",         // 그림자 네모
            "v", "❖",         // 마름모
            "Ø", "➔",   // 굵은 화살표
            "ü", "✔",   // 체크
            "û", "✘",   // 가위표
            "¨", "□");  // 빈 네모

    /** 그림 글꼴로 적은 글머리인가. 그 글꼴이 없으면 §·ü 같은 엉뚱한 글자로 보인다. */
    private static final java.util.Set<String> PICTURE_FONTS = java.util.Set.of(
            "wingdings", "wingdings 2", "wingdings 3", "webdings", "symbol",
            "monotype sorts", "zapfdingbats");

    /**
     * 눈에 보이는 글머리 기호.
     *
     * <p>Wingdings 같은 그림 글꼴은 글자 자리에 그림을 넣어 둔 것이라 그 글꼴이
     * 없는 PC 에서는 엉뚱한 글자가 뜬다(2026-08-21 지적). 아는 것은 같은 모양의
     * 표준 기호로, 모르는 것은 그냥 점으로 바꾼다.
     */
    private String readable(String mark, String font) {
        if (font == null || !PICTURE_FONTS.contains(font.toLowerCase(java.util.Locale.ROOT))) {
            return mark;
        }
        return WINGDINGS.getOrDefault(mark, "•");
    }

    /** 포인트를 픽셀로. 적혀 있지 않으면 0 이다. */
    private double px(Double points) {
        return points == null ? 0 : px(points.doubleValue());
    }

    /**
     * 글꼴 이름.
     *
     * <p>글꼴이 다르면 같은 크기라도 글 너비가 달라 줄이 엉뚱한 자리에서 접힌다
     * (2026-08-21 지적).
     */
    private String fontOf(org.apache.poi.xslf.usermodel.XSLFTextRun run) {
        try {
            String name = run.getFontFamily();
            return name == null || name.isBlank() ? null : name;
        } catch (RuntimeException cannotRead) {
            return null;
        }
    }

    /**
     * 넘치면 글자를 줄여 상자에 맞추는 상자인가({@code normAutofit}).
     *
     * <p>파워포인트는 줄일 배율을 파일에 안 적어 두는 일이 흔하다 - 열 때 스스로
     * 잰다. 그래서 배율이 아니라 "줄여야 하는가"만 넘기고 재는 일은 화면이
     * 한다(2026-08-21 지적).
     */
    private boolean shrinks(XSLFTextShape shape) {
        try {
            // POI 는 안 적혀 있어도 NORMAL 을 준다. 적혀 있는지를 직접 본다.
            if (shape.getXmlObject() instanceof org.openxmlformats.schemas
                    .presentationml.x2006.main.CTShape written) {
                var body = written.getTxBody();
                return body != null && body.getBodyPr() != null
                        && body.getBodyPr().isSetNormAutofit();
            }
            return false;
        } catch (RuntimeException cannotRead) {
            return false;
        }
    }

    /** 글이 든 첫 조각. 빈 조각을 보면 만든 이가 정하지도 않은 기본 크기가 나온다. */
    private org.apache.poi.xslf.usermodel.XSLFTextRun firstWritten(
            org.apache.poi.xslf.usermodel.XSLFTextParagraph paragraph) {
        for (var run : paragraph.getTextRuns()) {
            if (run.getRawText() != null && !run.getRawText().isBlank()) {
                return run;
            }
        }
        return null;
    }

    /**
     * 글자 색.
     *
     * <p>어두운 바탕에 흰 글씨가 흔하다. 색을 흘리면 검은 글씨가 되어 바탕에
     * 묻힌다(2026-08-21 지적).
     */
    private String colorOf(org.apache.poi.xslf.usermodel.XSLFTextRun run) {
        try {
            var paint = run.getFontColor();
            if (paint instanceof org.apache.poi.sl.usermodel.PaintStyle.SolidPaint solid) {
                return rgb(org.apache.poi.sl.draw.DrawPaint
                        .applyColorTransform(solid.getSolidColor()));
            }
            return null;
        } catch (RuntimeException cannotRead) {
            return null;
        }
    }

    private SlideDeck.SlideTable tableOf(XSLFTable table, Frame frame) {
        List<List<SlideDeck.SlideCell>> cells = new ArrayList<>();
        for (var row : table.getRows()) {
            List<SlideDeck.SlideCell> line = new ArrayList<>();
            for (var cell : row.getCells()) {
                line.add(cellOf(cell));
            }
            cells.add(line);
        }
        var box = table.getAnchor();
        return new SlideDeck.SlideTable(cells, px(frame.x(box.getX())),
                px(frame.y(box.getY())), px(frame.wide(box.getWidth())),
                px(frame.high(box.getHeight())));
    }

    /** 칸 하나. 글과 그 칸의 꾸밈을 함께 담는다. */
    private SlideDeck.SlideCell cellOf(org.apache.poi.xslf.usermodel.XSLFTableCell cell) {
        var run = firstWritten(cell);
        return new SlideDeck.SlideCell(cell.getText() == null ? "" : cell.getText(),
                fillOf(cell), run == null ? null : colorOf(run), alignOf(cell),
                run == null || run.getFontSize() == null ? 0 : run.getFontSize(),
                run != null && run.isBold(), run == null ? null : fontOf(run));
    }

    /** 칸에서 글이 든 첫 조각. 칸 전체의 꾸밈을 이 조각에서 본다. */
    private org.apache.poi.xslf.usermodel.XSLFTextRun firstWritten(
            org.apache.poi.xslf.usermodel.XSLFTableCell cell) {
        for (var paragraph : cell.getTextParagraphs()) {
            var run = firstWritten(paragraph);
            if (run != null) {
                return run;
            }
        }
        return null;
    }

    /** 칸 바탕색. 안 칠했으면 null 이다. */
    private String fillOf(org.apache.poi.xslf.usermodel.XSLFTableCell cell) {
        try {
            return rgb(cell.getFillColor());
        } catch (RuntimeException cannotRead) {
            return null;
        }
    }

    /**
     * 문단 가로 맞춤. 왼쪽이면 null 이다 - 화면 기본이 왼쪽이라 적을 것이 없다.
     *
     * <p>띠 위 글은 거의 가운데 맞춤이다. 흘리면 글이 왼쪽으로 몰린다
     * (2026-08-21 지적).
     */
    private String alignOf(org.apache.poi.xslf.usermodel.XSLFTextParagraph paragraph) {
        try {
            var align = paragraph.getTextAlign();
            if (align == org.apache.poi.sl.usermodel.TextParagraph.TextAlign.CENTER) {
                return "center";
            }
            return align == org.apache.poi.sl.usermodel.TextParagraph.TextAlign.RIGHT
                    ? "right" : null;
        } catch (RuntimeException cannotRead) {
            return null;
        }
    }

    /** 칸 정렬. 왼쪽이면 null 이다 - 화면 기본이 왼쪽이라 적을 것이 없다. */
    private String alignOf(org.apache.poi.xslf.usermodel.XSLFTableCell cell) {
        for (var paragraph : cell.getTextParagraphs()) {
            var align = paragraph.getTextAlign();
            if (align == org.apache.poi.sl.usermodel.TextParagraph.TextAlign.CENTER) {
                return "center";
            }
            if (align == org.apache.poi.sl.usermodel.TextParagraph.TextAlign.RIGHT) {
                return "right";
            }
        }
        return null;
    }

    /** 발표자 노트. 없으면 null 이다. */
    private String notesOf(XSLFSlide slide) {
        XSLFNotes notes = slide.getNotes();
        if (notes == null) {
            return null;
        }
        StringBuilder written = new StringBuilder();
        for (var shape : notes.getShapes()) {
            // 본문 자리만 본다. 노트 장에는 머리글·날짜·쪽번호·슬라이드 그림 자리가
            // 함께 있어서 다 담으면 "‹#›" 같은 것이 섞인다(2026-08-13 실측).
            if (!(shape instanceof XSLFTextShape text)
                    || text.getTextType() != org.apache.poi.sl.usermodel.Placeholder.BODY
                    || text.getText().isBlank()) {
                continue;
            }
            if (!written.isEmpty()) {
                written.append('\n');
            }
            written.append(text.getText());
        }
        return written.isEmpty() ? null : written.toString();
    }

    private double px(double points) {
        return Math.round(points * PIXELS_PER_POINT * 100) / 100.0;
    }
}
