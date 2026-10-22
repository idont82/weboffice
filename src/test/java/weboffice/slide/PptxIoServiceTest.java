package weboffice.slide;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import org.apache.poi.sl.usermodel.PictureData;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFTextBox;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * pptx 읽기.
 *
 * <p>고쳐 쓰기는 하지 않는다. 슬라이드에 무엇이 적혀 있는지 보여 주기만 한다
 * (2026-08-13 결정: 뷰어·추출만).
 */
class PptxIoServiceTest {

    private final PptxIoService service = new PptxIoService();

    /** 첫 장에서 그 글을 담은 글상자. 밑그림 글이 섞여 있어 자리로 찾지 않는다. */
    private SlideDeck.SlideText 글을_찾는다(SlideDeck deck, String written) {
        return deck.slides().get(0).texts().stream()
                .filter(one -> one.text().contains(written))
                .findFirst()
                .orElseThrow(() -> new AssertionError("그런 글이 없다: " + written));
    }

    @Test
    void 슬라이드_수를_읽는다() throws Exception {
        SlideDeck deck = service.readPptx(new ByteArrayInputStream(세_장짜리()));

        assertThat(deck.slides()).hasSize(3);
        assertThat(deck.slides()).extracting(SlideDeck.Slide::slideNo)
                .containsExactly(0, 1, 2);
    }

    @Test
    void 글상자_글과_자리를_읽는다() throws Exception {
        SlideDeck deck = service.readPptx(new ByteArrayInputStream(글상자가_든_장()));

        // 밑그림(마스터)이 적어 둔 글이 앞에 온다. 우리가 쓴 글을 찾아 본다.
        var text = 글을_찾는다(deck, "첫 장 제목");
        // POI 는 자리를 포인트로 준다. 화면은 픽셀이다 - 96/72 를 곱한 값이다.
        assertThat(text.left()).isEqualTo(96);
        assertThat(text.top()).isEqualTo(48);
        assertThat(text.width()).isEqualTo(400);
    }

    @Test
    void 글자_크기와_굵기를_읽는다() throws Exception {
        SlideDeck deck = service.readPptx(new ByteArrayInputStream(글상자가_든_장()));

        var text = 글을_찾는다(deck, "첫 장 제목");
        assertThat(text.fontSize()).isEqualTo(28);
        assertThat(text.bold()).isTrue();
    }

    @Test
    void 도형의_자리와_채운_색을_읽는다() throws Exception {
        // 발표 자료는 색 띠와 사각형이 화면 대부분이다. 글자만 읽으면 흰 종이에
        // 검은 글씨만 남아 원본과 딴판이 된다(2026-08-21 지적).
        SlideDeck deck = service.readPptx(new ByteArrayInputStream(도형이_든_장()));

        var shape = deck.slides().get(0).shapes().get(0);
        assertThat(shape.fill()).isEqualTo("#07182d");
        assertThat(shape.left()).isEqualTo(96);
        assertThat(shape.top()).isEqualTo(48);
        assertThat(shape.width()).isEqualTo(400);
    }

    @Test
    void 도형_모양을_알려_준다() throws Exception {
        // 네모·동그라미 말고도 세모·둥근네모·화살표가 흔하다. 다 네모로 그리면
        // 원본과 딴판이다(2026-08-21 지적).
        var shapes = service.readPptx(new ByteArrayInputStream(도형이_든_장()))
                .slides().get(0).shapes();

        assertThat(shapes.get(0).shape()).isEqualTo("rect");
        assertThat(shapes.get(1).shape()).isEqualTo("ellipse");
        assertThat(shapes.get(2).shape()).isEqualTo("triangle");
        assertThat(shapes.get(3).shape()).isEqualTo("roundRect");
    }

    @Test
    void 돌려_놓은_도형은_각도를_알려_준다() throws Exception {
        var shapes = service.readPptx(new ByteArrayInputStream(도형이_든_장()))
                .slides().get(0).shapes();

        assertThat(shapes.get(2).rotation()).isEqualTo(45);
        assertThat(shapes.get(0).rotation()).isZero();
    }

    @Test
    void 글상자_글자_색을_읽는다() throws Exception {
        SlideDeck deck = service.readPptx(new ByteArrayInputStream(도형이_든_장()));

        assertThat(글을_찾는다(deck, "SECURITY").color()).isEqualTo("#ffffff");
    }

    /** 남색 사각형 하나, 시안 동그라미 하나, 흰 글씨 하나. */
    private byte[] 도형이_든_장() throws Exception {
        try (XMLSlideShow show = new XMLSlideShow();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            var slide = show.createSlide();

            var box = slide.createAutoShape();
            box.setShapeType(org.apache.poi.sl.usermodel.ShapeType.RECT);
            box.setAnchor(new java.awt.geom.Rectangle2D.Double(72, 36, 300, 100));
            box.setFillColor(new java.awt.Color(0x07, 0x18, 0x2D));

            var circle = slide.createAutoShape();
            circle.setShapeType(org.apache.poi.sl.usermodel.ShapeType.ELLIPSE);
            circle.setAnchor(new java.awt.geom.Rectangle2D.Double(200, 200, 80, 80));
            circle.setFillColor(new java.awt.Color(0x00, 0xE5, 0xFF));

            var triangle = slide.createAutoShape();
            triangle.setShapeType(org.apache.poi.sl.usermodel.ShapeType.TRIANGLE);
            triangle.setAnchor(new java.awt.geom.Rectangle2D.Double(300, 200, 60, 60));
            triangle.setFillColor(java.awt.Color.RED);
            triangle.setRotation(45);

            var round = slide.createAutoShape();
            round.setShapeType(org.apache.poi.sl.usermodel.ShapeType.ROUND_RECT);
            round.setAnchor(new java.awt.geom.Rectangle2D.Double(400, 200, 60, 60));
            round.setFillColor(java.awt.Color.GREEN);

            XSLFTextBox written = slide.createTextBox();
            written.setAnchor(new java.awt.geom.Rectangle2D.Double(72, 36, 300, 40));
            var run = written.addNewTextParagraph().addNewTextRun();
            run.setText("SECURITY");
            run.setFontColor(java.awt.Color.WHITE);

            show.write(out);
            return out.toByteArray();
        }
    }

    @Test
    void 바탕과_장식을_물려받는다() throws Exception {
        // 자리만 잡아 둔 장은 배경도 장식도 슬라이드가 아니라 그 장이 쓰는
        // 밑그림(레이아웃)에 있다. 슬라이드만 보면 빈 종이가 된다(2026-08-21 실측:
        // 마지막 「Thank You!」 장이 통째로 비었다).
        SlideDeck deck = service.readPptx(new ByteArrayInputStream(밑그림을_쓰는_장()));

        var slide = deck.slides().get(0);
        assertThat(slide.background()).isEqualTo("#07182d");
        assertThat(slide.shapes()).as("밑그림의 장식도 함께 그린다").isNotEmpty();
    }

    /** 밑그림에 어두운 바탕과 띠를 두고, 장에는 아무것도 두지 않는다. */
    private byte[] 밑그림을_쓰는_장() throws Exception {
        try (XMLSlideShow show = new XMLSlideShow();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            var layout = show.getSlideMasters().get(0).getSlideLayouts()[0];
            var background = layout.createAutoShape();
            background.setShapeType(org.apache.poi.sl.usermodel.ShapeType.RECT);
            background.setAnchor(new java.awt.geom.Rectangle2D.Double(0, 0, 720, 540));
            background.setFillColor(new java.awt.Color(0x07, 0x18, 0x2D));
            layout.getXmlObject().getCSld().addNewBg().addNewBgPr()
                    .addNewSolidFill().addNewSrgbClr().setVal(new byte[] { 7, 24, 45 });

            show.createSlide(layout);
            show.write(out);
            return out.toByteArray();
        }
    }

    @Test
    void 그라데이션을_읽는다() throws Exception {
        // 색이 번지는 도형이 흔하다. 못 읽는다고 비워 두면 그 자리가 뻥 뚫린다
        // (2026-08-21 지적).
        SlideDeck deck = service.readPptx(new ByteArrayInputStream(그라데이션_장()));

        var shape = deck.slides().get(0).shapes().get(0);
        assertThat(shape.gradient()).hasSize(2);
        assertThat(shape.gradient().get(0).color()).isEqualTo("#ff0000");
        assertThat(shape.gradient().get(1).color()).isEqualTo("#0000ff");
        assertThat(shape.gradient().get(1).at()).isEqualTo(1);
    }

    /** 빨강에서 파랑으로 번지는 사각형 하나. */
    private byte[] 그라데이션_장() throws Exception {
        try (XMLSlideShow show = new XMLSlideShow();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            var slide = show.createSlide();
            var box = slide.createAutoShape();
            box.setShapeType(org.apache.poi.sl.usermodel.ShapeType.RECT);
            box.setAnchor(new java.awt.geom.Rectangle2D.Double(0, 0, 300, 100));

            var drawn = (org.openxmlformats.schemas.presentationml.x2006.main.CTShape)
                    box.getXmlObject();
            var fill = drawn.getSpPr().addNewGradFill();
            var stops = fill.addNewGsLst();
            var first = stops.addNewGs();
            first.setPos(0);
            first.addNewSrgbClr().setVal(new byte[] { (byte) 0xFF, 0, 0 });
            var last = stops.addNewGs();
            last.setPos(100000);
            last.addNewSrgbClr().setVal(new byte[] { 0, 0, (byte) 0xFF });
            fill.addNewLin().setAng(5400000);

            show.write(out);
            return out.toByteArray();
        }
    }

    @Test
    void 그림으로_채운_도형은_그림을_담는다() throws Exception {
        // 사진을 깔아 둔 도형이 흔하다. 못 읽으면 그 자리가 빈다(2026-08-21 지적).
        SlideDeck deck = service.readPptx(new ByteArrayInputStream(그림으로_채운_장()));

        var shape = deck.slides().get(0).shapes().get(0);
        assertThat(shape.image()).isNotEmpty();
    }

    /** 도형 하나에 그림을 깔아 둔 장. */
    private byte[] 그림으로_채운_장() throws Exception {
        try (XMLSlideShow show = new XMLSlideShow();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            var slide = show.createSlide();
            var box = slide.createAutoShape();
            box.setShapeType(org.apache.poi.sl.usermodel.ShapeType.RECT);
            box.setAnchor(new java.awt.geom.Rectangle2D.Double(0, 0, 200, 100));

            // 그림을 장에 한 번 붙여 관계를 만든 뒤 그 이름을 도형 채우기에 쓴다.
            var picture = show.addPicture(작은_png(), PictureData.PictureType.PNG);
            var placed = slide.createPicture(picture);
            String relation = slide.getRelationId(placed.getPictureData());
            placed.setAnchor(new java.awt.geom.Rectangle2D.Double(500, 400, 10, 10));

            var drawn = (org.openxmlformats.schemas.presentationml.x2006.main.CTShape)
                    box.getXmlObject();
            var fill = drawn.getSpPr().addNewBlipFill();
            fill.addNewBlip().setEmbed(relation);
            fill.addNewStretch().addNewFillRect();

            show.write(out);
            return out.toByteArray();
        }
    }

    @Test
    void 밑그림의_글과_그림도_담는다() throws Exception {
        // 꼬릿글·쪽 번호·회사 로고는 슬라이드가 아니라 마스터에 있다. 안 담으면
        // 모든 장에서 그것들이 통째로 빠진다(2026-08-21 지적).
        SlideDeck deck = service.readPptx(new ByteArrayInputStream(마스터가_꾸민_장()));

        var slide = deck.slides().get(0);
        assertThat(slide.texts()).extracting(SlideDeck.SlideText::text)
                .contains("Copyright 2026");
        assertThat(slide.pictures()).as("로고도 담는다").isNotEmpty();
    }

    @Test
    void 쪽_번호_자리는_진짜_번호로_바꾼다() throws Exception {
        // 마스터에는 ‹#› 라고만 적혀 있다. 그대로 두면 모든 장에 그 기호가 뜬다.
        SlideDeck deck = service.readPptx(new ByteArrayInputStream(마스터가_꾸민_장()));

        assertThat(deck.slides().get(0).texts()).extracting(SlideDeck.SlideText::text)
                .contains("1 쪽");
    }

    @Test
    void 쪽_번호는_첫_장_번호부터_센다() throws Exception {
        // 표지를 0쪽으로 둔 자료는 둘째 장이 1쪽이다(2026-08-21 지적).
        var deck = service.readPptx(new ByteArrayInputStream(영부터_세며_쪽을_적은_장()));

        assertThat(deck.slides().get(0).texts()).extracting(SlideDeck.SlideText::text)
                .contains("0 쪽");
        assertThat(deck.slides().get(1).texts()).extracting(SlideDeck.SlideText::text)
                .contains("1 쪽");
    }

    /** 표지를 0쪽으로 두고 마스터에 쪽 번호를 적은 두 장. */
    private byte[] 영부터_세며_쪽을_적은_장() throws Exception {
        try (XMLSlideShow show = new XMLSlideShow();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            var numbered = show.getSlideMasters().get(0).createTextBox();
            numbered.setAnchor(new java.awt.geom.Rectangle2D.Double(600, 480, 60, 24));
            numbered.addNewTextParagraph().addNewTextRun().setText("‹#› 쪽");

            show.createSlide();
            show.createSlide();
            show.getCTPresentation().setFirstSlideNum(0);
            show.write(out);
            return out.toByteArray();
        }
    }

    @Test
    void 밑그림의_날짜_꼬릿글_자리는_담지_않는다() throws Exception {
        // 파워포인트는 [삽입 > 머리글/바닥글] 로 켠 장에만 그 글을 그린다. 밑그림에
        // 남아 있는 빈 자리표시자를 담으면 만든 날짜가 모든 장에 뜬다(2026-08-21 지적).
        SlideDeck deck = service.readPptx(new ByteArrayInputStream(민무늬_한_장()));

        assertThat(deck.slides().get(0).texts()).isEmpty();
    }

    /** 아무것도 안 적은 한 장. 밑그림 자리표시자만 남아 있다. */
    private byte[] 민무늬_한_장() throws Exception {
        try (XMLSlideShow show = new XMLSlideShow();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            show.createSlide();
            show.write(out);
            return out.toByteArray();
        }
    }

    /** 마스터에 꼬릿글·쪽 번호·로고를 둔 한 장. */
    private byte[] 마스터가_꾸민_장() throws Exception {
        try (XMLSlideShow show = new XMLSlideShow();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            var master = show.getSlideMasters().get(0);

            var footer = master.createTextBox();
            footer.setAnchor(new java.awt.geom.Rectangle2D.Double(36, 480, 300, 24));
            footer.addNewTextParagraph().addNewTextRun().setText("Copyright 2026");
            var numbered = master.createTextBox();
            numbered.setAnchor(new java.awt.geom.Rectangle2D.Double(600, 480, 60, 24));
            numbered.addNewTextParagraph().addNewTextRun().setText("‹#› 쪽");

            var picture = show.addPicture(작은_png(), PictureData.PictureType.PNG);
            master.createPicture(picture).setAnchor(
                    new java.awt.geom.Rectangle2D.Double(600, 480, 60, 30));

            show.createSlide();
            show.write(out);
            return out.toByteArray();
        }
    }

    @Test
    void 표_칸의_바탕색과_정렬을_읽는다() throws Exception {
        // 발표 자료 표는 머리 줄에 색을 깔고 값을 가운데 세운다. 그것을 흘리면
        // 흰 표에 왼쪽 정렬만 남아 원본과 딴판이다(2026-08-21 지적).
        SlideDeck deck = service.readPptx(new ByteArrayInputStream(꾸민_표가_든_장()));

        var table = deck.slides().get(0).tables().get(0);
        assertThat(table.cells().get(0).get(0).fill()).isEqualTo("#1b365d");
        assertThat(table.cells().get(0).get(0).color()).isEqualTo("#ffffff");
        assertThat(table.cells().get(0).get(1).align()).isEqualTo("center");
        assertThat(table.cells().get(1).get(0).align()).isNull();
    }

    @Test
    void 표_칸의_글자_크기와_굵기를_읽는다() throws Exception {
        // 머리 줄은 굵고 값은 작다. 흘리면 표 안 글이 모두 같은 크기로 나온다
        // (2026-08-21 지적).
        SlideDeck deck = service.readPptx(new ByteArrayInputStream(꾸민_표가_든_장()));

        var table = deck.slides().get(0).tables().get(0);
        assertThat(table.cells().get(0).get(0).fontSize()).isEqualTo(20);
        assertThat(table.cells().get(0).get(0).bold()).isTrue();
        assertThat(table.cells().get(0).get(0).font()).isEqualTo("Gulim");
        assertThat(table.cells().get(1).get(0).fontSize()).isEqualTo(11);
        assertThat(table.cells().get(1).get(0).bold()).isFalse();
    }

    /** 머리 줄에 남색 바탕과 흰 글씨, 가운데 정렬을 준 표. */
    private byte[] 꾸민_표가_든_장() throws Exception {
        try (XMLSlideShow show = new XMLSlideShow();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            var slide = show.createSlide();
            var table = slide.createTable(2, 2);
            table.setAnchor(new java.awt.geom.Rectangle2D.Double(20, 20, 400, 100));

            var head = table.getCell(0, 0);
            head.setText("구분");
            head.setFillColor(new java.awt.Color(0x1B, 0x36, 0x5D));
            var headRun = head.getTextParagraphs().get(0).getTextRuns().get(0);
            headRun.setFontColor(java.awt.Color.WHITE);
            headRun.setFontSize(20.0);
            headRun.setBold(true);
            headRun.setFontFamily("Gulim");

            var middle = table.getCell(0, 1);
            middle.setText("값");
            middle.getTextParagraphs().get(0)
                    .setTextAlign(org.apache.poi.sl.usermodel.TextParagraph.TextAlign.CENTER);

            var body = table.getCell(1, 0);
            body.setText("보안");
            body.getTextParagraphs().get(0).getTextRuns().get(0).setFontSize(11.0);
            table.getCell(1, 1).setText("접근 통제");

            show.write(out);
            return out.toByteArray();
        }
    }

    @Test
    void 둥근_네모의_모서리_값을_읽는다() throws Exception {
        // 파워포인트는 모서리를 짧은 변의 몇 %로 적는다. 우리가 15% 로 못 박으면
        // 원본과 곡률이 다르다(2026-08-21 지적).
        SlideDeck deck = service.readPptx(new ByteArrayInputStream(둥근_네모_장()));

        assertThat(deck.slides().get(0).shapes().get(0).corner()).isEqualTo(0.05);
    }

    /** 모서리를 5% 로 둥글린 네모 하나. */
    private byte[] 둥근_네모_장() throws Exception {
        try (XMLSlideShow show = new XMLSlideShow();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            var slide = show.createSlide();
            var box = slide.createAutoShape();
            box.setShapeType(org.apache.poi.sl.usermodel.ShapeType.ROUND_RECT);
            box.setAnchor(new java.awt.geom.Rectangle2D.Double(0, 0, 200, 100));
            box.setFillColor(java.awt.Color.GRAY);

            var drawn = (org.openxmlformats.schemas.presentationml.x2006.main.CTShape)
                    box.getXmlObject();
            var geometry = drawn.getSpPr().getPrstGeom();
            var guides = geometry.isSetAvLst() ? geometry.getAvLst() : geometry.addNewAvLst();
            var guide = guides.addNewGd();
            guide.setName("adj");
            guide.setFmla("val 5000");

            show.write(out);
            return out.toByteArray();
        }
    }

    @Test
    void 표를_읽는다() throws Exception {
        SlideDeck deck = service.readPptx(new ByteArrayInputStream(표가_든_장()));

        var table = deck.slides().get(0).tables().get(0);
        assertThat(table.cells().stream()
                .map(row -> row.stream().map(SlideDeck.SlideCell::text).toList()).toList())
                .containsExactly(
                        java.util.List.of("항목", "값"),
                        java.util.List.of("매출", "100"));
    }

    @Test
    void 그림을_읽는다() throws Exception {
        SlideDeck deck = service.readPptx(new ByteArrayInputStream(그림이_든_장()));

        var picture = deck.slides().get(0).pictures().get(0);
        assertThat(picture.bytes()).isNotEmpty();
        assertThat(picture.width()).isEqualTo(200);
    }

    @Test
    void 발표자_노트를_읽는다() throws Exception {
        SlideDeck deck = service.readPptx(new ByteArrayInputStream(노트가_든_장()));

        assertThat(deck.slides().get(0).notes()).isEqualTo("여기서 천천히");
    }

    @Test
    void 종이_크기를_읽는다() throws Exception {
        SlideDeck deck = service.readPptx(new ByteArrayInputStream(세_장짜리()));

        // 기본 슬라이드는 10 × 7.5 인치(720 × 540 pt)다.
        assertThat(deck.width()).isEqualTo(960);
        assertThat(deck.height()).isEqualTo(720);
    }

    private byte[] 세_장짜리() throws Exception {
        try (var show = new XMLSlideShow();
             var out = new ByteArrayOutputStream()) {
            for (int at = 0; at < 3; at++) {
                show.createSlide();
            }
            show.write(out);
            return out.toByteArray();
        }
    }

    private byte[] 글상자가_든_장() throws Exception {
        try (var show = new XMLSlideShow();
             var out = new ByteArrayOutputStream()) {
            XSLFTextBox box = show.createSlide().createTextBox();
            box.setAnchor(new java.awt.geom.Rectangle2D.Double(72, 36, 300, 60));
            var paragraph = box.addNewTextParagraph();
            var run = paragraph.addNewTextRun();
            run.setText("첫 장 제목");
            run.setFontSize(28.0);
            run.setBold(true);
            show.write(out);
            return out.toByteArray();
        }
    }

    private byte[] 표가_든_장() throws Exception {
        try (var show = new XMLSlideShow();
             var out = new ByteArrayOutputStream()) {
            var table = show.createSlide().createTable(2, 2);
            table.getCell(0, 0).setText("항목");
            table.getCell(0, 1).setText("값");
            table.getCell(1, 0).setText("매출");
            table.getCell(1, 1).setText("100");
            show.write(out);
            return out.toByteArray();
        }
    }

    private byte[] 그림이_든_장() throws Exception {
        try (var show = new XMLSlideShow();
             var out = new ByteArrayOutputStream()) {
            var data = show.addPicture(작은_png(), PictureData.PictureType.PNG);
            var picture = show.createSlide().createPicture(data);
            picture.setAnchor(new java.awt.geom.Rectangle2D.Double(0, 0, 150, 100));
            show.write(out);
            return out.toByteArray();
        }
    }

    private byte[] 노트가_든_장() throws Exception {
        try (var show = new XMLSlideShow();
             var out = new ByteArrayOutputStream()) {
            var slide = show.createSlide();
            var notes = show.getNotesSlide(slide);
            for (var shape : notes.getShapes()) {
                if (shape instanceof org.apache.poi.xslf.usermodel.XSLFTextShape text
                        && text.getTextType()
                        == org.apache.poi.sl.usermodel.Placeholder.BODY) {
                    text.setText("여기서 천천히");
                }
            }
            show.write(out);
            return out.toByteArray();
        }
    }

    /** 1픽셀 png. 그림 알맹이가 그대로 나오는지만 본다. */
    private byte[] 작은_png() {
        return java.util.Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");
    }

    @Test
    void 한_상자_안에서_줄마다_다른_크기를_읽는다() throws Exception {
        var deck = service.readPptx(new ByteArrayInputStream(줄마다_크기가_다른_장()));

        var text = 글을_찾는다(deck, "큰 줄");
        assertThat(text.lines()).hasSize(2);
        assertThat(text.lines().get(0).text()).isEqualTo("큰 줄");
        assertThat(text.lines().get(0).fontSize()).isEqualTo(32);
        assertThat(text.lines().get(1).text()).isEqualTo("작은 줄");
        assertThat(text.lines().get(1).fontSize()).isEqualTo(12);
    }

    private byte[] 줄마다_크기가_다른_장() throws Exception {
        try (var show = new XMLSlideShow();
             var out = new ByteArrayOutputStream()) {
            var box = show.createSlide().createTextBox();
            box.setAnchor(new java.awt.geom.Rectangle2D.Double(10, 10, 300, 100));
            var big = box.addNewTextParagraph().addNewTextRun();
            big.setText("큰 줄");
            big.setFontSize(32.0);
            var small = box.addNewTextParagraph().addNewTextRun();
            small.setText("작은 줄");
            small.setFontSize(12.0);
            show.write(out);
            return out.toByteArray();
        }
    }


    @Test
    void 상자에_맞춰_줄이는_글인지_읽는다() throws Exception {
        var deck = service.readPptx(new ByteArrayInputStream(줄여_맞추는_상자()));

        assertThat(글을_찾는다(deck, "넘치는 글").shrinkToFit()).isTrue();
        assertThat(글을_찾는다(deck, "그냥 글").shrinkToFit()).isFalse();
    }

    private byte[] 줄여_맞추는_상자() throws Exception {
        try (var show = new XMLSlideShow();
             var out = new ByteArrayOutputStream()) {
            var slide = show.createSlide();
            var shrinks = slide.createTextBox();
            shrinks.setAnchor(new java.awt.geom.Rectangle2D.Double(10, 10, 200, 40));
            shrinks.setTextAutofit(org.apache.poi.sl.usermodel.TextShape.TextAutofit.NORMAL);
            shrinks.addNewTextParagraph().addNewTextRun().setText("넘치는 글");

            var plain = slide.createTextBox();
            plain.setAnchor(new java.awt.geom.Rectangle2D.Double(10, 60, 200, 40));
            plain.addNewTextParagraph().addNewTextRun().setText("그냥 글");
            show.write(out);
            return out.toByteArray();
        }
    }


    @Test
    void 글꼴_이름을_읽는다() throws Exception {
        var deck = service.readPptx(new ByteArrayInputStream(글꼴을_정한_상자()));

        var text = 글을_찾는다(deck, "굴림으로 쓴 줄");
        assertThat(text.font()).isEqualTo("Gulim");
        assertThat(text.lines().get(0).font()).isEqualTo("Gulim");
    }

    private byte[] 글꼴을_정한_상자() throws Exception {
        try (var show = new XMLSlideShow();
             var out = new ByteArrayOutputStream()) {
            var box = show.createSlide().createTextBox();
            box.setAnchor(new java.awt.geom.Rectangle2D.Double(10, 10, 300, 40));
            var run = box.addNewTextParagraph().addNewTextRun();
            run.setText("굴림으로 쓴 줄");
            run.setFontFamily("Gulim");
            show.write(out);
            return out.toByteArray();
        }
    }


    @Test
    void 글의_세로_맞춤을_읽는다() throws Exception {
        var deck = service.readPptx(new ByteArrayInputStream(가운데_맞춘_상자()));

        assertThat(글을_찾는다(deck, "가운데 줄").anchor()).isEqualTo("center");
        assertThat(글을_찾는다(deck, "위 줄").anchor()).isNull();
    }

    private byte[] 가운데_맞춘_상자() throws Exception {
        try (var show = new XMLSlideShow();
             var out = new ByteArrayOutputStream()) {
            var slide = show.createSlide();
            var middle = slide.createTextBox();
            middle.setAnchor(new java.awt.geom.Rectangle2D.Double(10, 10, 200, 40));
            middle.setVerticalAlignment(org.apache.poi.sl.usermodel.VerticalAlignment.MIDDLE);
            middle.addNewTextParagraph().addNewTextRun().setText("가운데 줄");

            var top = slide.createTextBox();
            top.setAnchor(new java.awt.geom.Rectangle2D.Double(10, 60, 200, 40));
            top.addNewTextParagraph().addNewTextRun().setText("위 줄");
            show.write(out);
            return out.toByteArray();
        }
    }


    @Test
    void 글머리_기호와_들여쓰기를_읽는다() throws Exception {
        // 발표 자료 본문은 거의 글머리 기호 목록이다. 흘리면 기호가 통째로 빠져
        // 그냥 줄바꿈한 글로 보인다(2026-08-21 지적).
        var deck = service.readPptx(new ByteArrayInputStream(글머리가_든_상자()));

        var lines = 글을_찾는다(deck, "첫 항목").lines();
        assertThat(lines.get(0).bullet()).isEqualTo("•");
        // 30pt 를 픽셀로 바꾼 값이다(96/72).
        assertThat(lines.get(0).indent()).isEqualTo(40);
        assertThat(lines.get(0).hanging()).isEqualTo(40);
        assertThat(lines.get(1).bullet()).isNull();
    }

    private byte[] 글머리가_든_상자() throws Exception {
        try (var show = new XMLSlideShow();
             var out = new ByteArrayOutputStream()) {
            var box = show.createSlide().createTextBox();
            box.setAnchor(new java.awt.geom.Rectangle2D.Double(10, 10, 300, 100));
            var listed = box.addNewTextParagraph();
            listed.setBullet(true);
            listed.setBulletCharacter("•");
            listed.setLeftMargin(30.0);
            listed.setIndent(-30.0);
            listed.addNewTextRun().setText("첫 항목");

            var plain = box.addNewTextParagraph();
            plain.addNewTextRun().setText("그냥 줄");
            show.write(out);
            return out.toByteArray();
        }
    }


    @Test
    void 바탕에_깔린_그림을_읽는다() throws Exception {
        // 표지·머리 띠를 통째로 그림 한 장으로 깐 발표 자료가 흔하다. 안 읽으면
        // 흰 종이에 글씨만 남는다(2026-08-21 지적).
        var deck = service.readPptx(new ByteArrayInputStream(바탕_그림이_깔린_장()));

        assertThat(deck.slides().get(0).backgroundImage()).isNotEmpty();
    }

    private byte[] 바탕_그림이_깔린_장() throws Exception {
        try (var show = new XMLSlideShow();
             var out = new ByteArrayOutputStream()) {
            var slide = show.createSlide();
            var data = show.addPicture(작은_png(), PictureData.PictureType.PNG);
            String id = slide.addRelation(null,
                    org.apache.poi.xslf.usermodel.XSLFRelation.IMAGE_PNG, data)
                    .getRelationship().getId();

            var sheet = (org.openxmlformats.schemas.presentationml.x2006.main.CTSlide)
                    slide.getXmlObject();
            var fill = sheet.getCSld().addNewBg().addNewBgPr().addNewBlipFill();
            fill.addNewBlip().setEmbed(id);
            fill.addNewStretch().addNewFillRect();
            sheet.getCSld().getBg().getBgPr().addNewEffectLst();

            show.write(out);
            return out.toByteArray();
        }
    }


    @Test
    void 테마_색으로_적은_번지는_색도_읽는다() throws Exception {
        // 파워포인트 기본 도형 스타일은 색을 테마 이름(accent2)으로 적는다. 이름을
        // 못 풀면 도형이 통째로 사라진다(2026-08-21 지적).
        var deck = service.readPptx(new ByteArrayInputStream(테마_색_번짐_장()));

        var shape = deck.slides().get(0).shapes().get(0);
        assertThat(shape.gradient()).hasSize(2);
        assertThat(shape.gradient().get(0).color()).startsWith("#");
        assertThat(shape.gradient().get(1).color()).startsWith("#");
    }

    private byte[] 테마_색_번짐_장() throws Exception {
        try (var show = new XMLSlideShow();
             var out = new ByteArrayOutputStream()) {
            var box = show.createSlide().createAutoShape();
            box.setAnchor(new java.awt.geom.Rectangle2D.Double(10, 10, 200, 40));

            var properties = ((org.openxmlformats.schemas.presentationml.x2006.main.CTShape)
                    box.getXmlObject()).getSpPr();
            var spread = properties.addNewGradFill();
            var stops = spread.addNewGsLst();
            var first = stops.addNewGs();
            first.setPos(0);
            first.addNewSchemeClr().setVal(org.openxmlformats.schemas.drawingml.x2006.main
                    .STSchemeColorVal.ACCENT_1);
            var last = stops.addNewGs();
            last.setPos(100000);
            last.addNewSchemeClr().setVal(org.openxmlformats.schemas.drawingml.x2006.main
                    .STSchemeColorVal.ACCENT_2);
            spread.addNewLin().setAng(2700000);

            show.write(out);
            return out.toByteArray();
        }
    }


    @Test
    void 손으로_그린_도형의_꼭짓점을_읽는다() throws Exception {
        // 머리 띠를 사다리꼴로 깎아 쓴 발표 자료가 많다. 네모로 그리면 비스듬한
        // 모서리가 사라진다(2026-08-21 지적).
        var deck = service.readPptx(new ByteArrayInputStream(사다리꼴_장()));

        var shape = deck.slides().get(0).shapes().get(0);
        assertThat(shape.outline()).containsExactly(
                new SlideDeck.Point(0, 0), new SlideDeck.Point(0.8, 0),
                new SlideDeck.Point(1, 1), new SlideDeck.Point(0, 1));
    }

    private byte[] 사다리꼴_장() throws Exception {
        try (var show = new XMLSlideShow();
             var out = new ByteArrayOutputStream()) {
            var box = show.createSlide().createAutoShape();
            box.setAnchor(new java.awt.geom.Rectangle2D.Double(0, 0, 400, 80));

            var properties = ((org.openxmlformats.schemas.presentationml.x2006.main.CTShape)
                    box.getXmlObject()).getSpPr();
            properties.unsetPrstGeom();
            var drawn = properties.addNewCustGeom();
            drawn.addNewAvLst();
            drawn.addNewGdLst();
            drawn.addNewAhLst();
            drawn.addNewCxnLst();
            drawn.addNewRect();
            var path = drawn.addNewPathLst().addNewPath();
            path.setW(1000);
            path.setH(500);
            var start = path.addNewMoveTo().addNewPt();
            start.xsetX(뼘(0));
            start.xsetY(뼘(0));
            var first = path.addNewLnTo().addNewPt();
            first.xsetX(뼘(800));
            first.xsetY(뼘(0));
            var second = path.addNewLnTo().addNewPt();
            second.xsetX(뼘(1000));
            second.xsetY(뼘(500));
            var third = path.addNewLnTo().addNewPt();
            third.xsetX(뼘(0));
            third.xsetY(뼘(500));
            path.addNewClose();
            properties.addNewSolidFill().addNewSrgbClr().setVal(new byte[] {0x16, 0x41, (byte) 0x94});

            show.write(out);
            return out.toByteArray();
        }
    }

    /** 좌표 한 칸. POI 는 이 자리에 문자열 좌표를 받는다. */
    private org.openxmlformats.schemas.drawingml.x2006.main.STAdjCoordinate 뼘(long value) {
        var one = org.openxmlformats.schemas.drawingml.x2006.main.STAdjCoordinate.Factory
                .newInstance();
        one.setObjectValue(value);
        return one;
    }


    @Test
    void 돌려_놓은_글상자의_각도를_읽는다() throws Exception {
        // 비스듬한 띠에 얹은 글이 흔하다. 각도를 흘리면 글만 반듯이 남아 띠 밖으로
        // 삐져나온다(2026-08-21 지적).
        var deck = service.readPptx(new ByteArrayInputStream(돌려_놓은_글상자_장()));

        assertThat(글을_찾는다(deck, "비스듬한 글").rotation()).isEqualTo(4.5);
    }

    private byte[] 돌려_놓은_글상자_장() throws Exception {
        try (var show = new XMLSlideShow();
             var out = new ByteArrayOutputStream()) {
            var box = show.createSlide().createTextBox();
            box.setAnchor(new java.awt.geom.Rectangle2D.Double(10, 10, 300, 40));
            box.setRotation(4.5);
            box.addNewTextParagraph().addNewTextRun().setText("비스듬한 글");
            show.write(out);
            return out.toByteArray();
        }
    }


    @Test
    void 글의_가로_맞춤을_읽는다() throws Exception {
        // 띠 위 글은 거의 가운데 맞춤이다. 흘리면 글이 왼쪽으로 몰린다
        // (2026-08-21 지적).
        var deck = service.readPptx(new ByteArrayInputStream(가운데_맞춘_글_장()));

        var text = 글을_찾는다(deck, "가운데로");
        assertThat(text.align()).isEqualTo("center");
        assertThat(text.lines().get(0).align()).isEqualTo("center");
        assertThat(글을_찾는다(deck, "왼쪽으로").align()).isNull();
    }

    private byte[] 가운데_맞춘_글_장() throws Exception {
        try (var show = new XMLSlideShow();
             var out = new ByteArrayOutputStream()) {
            var slide = show.createSlide();
            var middle = slide.createTextBox();
            middle.setAnchor(new java.awt.geom.Rectangle2D.Double(10, 10, 300, 40));
            var paragraph = middle.addNewTextParagraph();
            paragraph.setTextAlign(org.apache.poi.sl.usermodel.TextParagraph.TextAlign.CENTER);
            paragraph.addNewTextRun().setText("가운데로");

            var left = slide.createTextBox();
            left.setAnchor(new java.awt.geom.Rectangle2D.Double(10, 60, 300, 40));
            left.addNewTextParagraph().addNewTextRun().setText("왼쪽으로");
            show.write(out);
            return out.toByteArray();
        }
    }


    @Test
    void 묶어_놓은_도형의_자리를_슬라이드_자리로_바꾼다() throws Exception {
        // 묶음(그룹) 안 도형은 묶음만의 좌표를 쓴다. 그대로 두면 엉뚱한 자리에
        // 찍힌다(2026-08-21 지적).
        var deck = service.readPptx(new ByteArrayInputStream(묶어_놓은_장()));

        var text = 글을_찾는다(deck, "묶음 안 글");
        // 묶음은 (100,50)에 200x100 인데 속 좌표는 0..400 x 0..200 이다. 곧 절반이다.
        // 속에서 (200,100)에 있으니 슬라이드에서는 (100+100, 50+50) 이다.
        assertThat(text.left()).isEqualTo(px(200));
        assertThat(text.top()).isEqualTo(px(100));
        assertThat(text.width()).isEqualTo(px(50));
    }

    /** 포인트를 픽셀로. 화면은 96dpi, 포인트는 72dpi 다. */
    private double px(double points) {
        return Math.round(points * 96 / 72 * 100) / 100.0;
    }

    private byte[] 묶어_놓은_장() throws Exception {
        try (var show = new XMLSlideShow();
             var out = new ByteArrayOutputStream()) {
            var group = show.createSlide().createGroup();
            group.setAnchor(new java.awt.geom.Rectangle2D.Double(100, 50, 200, 100));
            group.setInteriorAnchor(new java.awt.geom.Rectangle2D.Double(0, 0, 400, 200));

            var box = group.createTextBox();
            box.setAnchor(new java.awt.geom.Rectangle2D.Double(200, 100, 100, 40));
            box.addNewTextParagraph().addNewTextRun().setText("묶음 안 글");

            show.write(out);
            return out.toByteArray();
        }
    }


    @Test
    void 상자_안쪽_여백을_읽는다() throws Exception {
        // 파워포인트 글상자는 안쪽에 여백이 있다. 흘리면 글이 테두리에 붙는다
        // (2026-08-21 지적).
        var deck = service.readPptx(new ByteArrayInputStream(여백을_준_상자()));

        var text = 글을_찾는다(deck, "여백 있는 글");
        assertThat(text.padding().left()).isEqualTo(px(20));
        assertThat(text.padding().top()).isEqualTo(px(10));
        assertThat(text.padding().right()).isEqualTo(px(30));
        assertThat(text.padding().bottom()).isEqualTo(px(5));
    }

    private byte[] 여백을_준_상자() throws Exception {
        try (var show = new XMLSlideShow();
             var out = new ByteArrayOutputStream()) {
            var box = show.createSlide().createTextBox();
            box.setAnchor(new java.awt.geom.Rectangle2D.Double(10, 10, 300, 80));
            box.setLeftInset(20);
            box.setTopInset(10);
            box.setRightInset(30);
            box.setBottomInset(5);
            box.addNewTextParagraph().addNewTextRun().setText("여백 있는 글");
            show.write(out);
            return out.toByteArray();
        }
    }

    @Test
    void 그림_글꼴_글머리를_보이는_기호로_바꾼다() throws Exception {
        // Wingdings 로 적은 글머리는 그 글꼴이 없으면 §·ü 같은 엉뚱한 글자로
        // 보인다(2026-08-21 지적).
        var deck = service.readPptx(new ByteArrayInputStream(그림_글꼴_글머리_장()));

        var lines = 글을_찾는다(deck, "네모 항목").lines();
        assertThat(lines.get(0).bullet()).isEqualTo("▪");
        // 표에 없는 글자는 그냥 점으로 둔다.
        assertThat(lines.get(1).bullet()).isEqualTo("•");
    }

    private byte[] 그림_글꼴_글머리_장() throws Exception {
        try (var show = new XMLSlideShow();
             var out = new ByteArrayOutputStream()) {
            var box = show.createSlide().createTextBox();
            box.setAnchor(new java.awt.geom.Rectangle2D.Double(10, 10, 300, 100));
            var square = box.addNewTextParagraph();
            square.setBullet(true);
            square.setBulletFont("Wingdings");
            square.setBulletCharacter("§");
            square.addNewTextRun().setText("네모 항목");

            var odd = box.addNewTextParagraph();
            odd.setBullet(true);
            odd.setBulletFont("Wingdings");
            odd.setBulletCharacter("Æ");
            odd.addNewTextRun().setText("낯선 항목");
            show.write(out);
            return out.toByteArray();
        }
    }


    @Test
    void 첫_장의_쪽_번호를_읽는다() throws Exception {
        // 표지를 0쪽으로 두는 자료가 있다. 자료마다 다르니 적힌 값을 따른다
        // (2026-08-21 지적). 안 적혀 있으면 1이다.
        var deck = service.readPptx(new ByteArrayInputStream(영부터_세는_장()));

        assertThat(deck.firstSlideNo()).isZero();
        assertThat(service.readPptx(new ByteArrayInputStream(세_장짜리())).firstSlideNo())
                .isEqualTo(1);
    }

    private byte[] 영부터_세는_장() throws Exception {
        try (var show = new XMLSlideShow();
             var out = new ByteArrayOutputStream()) {
            show.createSlide();
            show.getCTPresentation().setFirstSlideNum(0);
            show.write(out);
            return out.toByteArray();
        }
    }

}
