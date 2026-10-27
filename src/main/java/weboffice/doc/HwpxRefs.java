package weboffice.doc;

import java.util.HashMap;
import java.util.Map;

import tools.jackson.databind.JsonNode;

import kr.dogfoot.hwpxlib.object.HWPXFile;
import kr.dogfoot.hwpxlib.object.content.header_xml.enumtype.HorizontalAlign2;
import kr.dogfoot.hwpxlib.object.content.header_xml.enumtype.LineWidth;
import kr.dogfoot.hwpxlib.object.content.header_xml.enumtype.LineSpacingType;
import kr.dogfoot.hwpxlib.object.content.header_xml.enumtype.LineType2;
import kr.dogfoot.hwpxlib.object.content.header_xml.enumtype.LineType3;
import kr.dogfoot.hwpxlib.object.content.header_xml.enumtype.UnderlineType;
import kr.dogfoot.hwpxlib.object.content.header_xml.enumtype.ValueUnit2;
import kr.dogfoot.hwpxlib.object.content.header_xml.references.CharPr;
import kr.dogfoot.hwpxlib.object.content.header_xml.references.BorderFill;
import kr.dogfoot.hwpxlib.object.content.header_xml.references.ParaPr;
import kr.dogfoot.hwpxlib.object.content.header_xml.references.borderfill.Border;

/**
 * 한글 문서의 서식 목록에 서식을 모으고 번호를 돌려준다.
 *
 * <p>워드는 문단마다 제 서식을 품지만, 한글은 서식을 {@code header.xml} 의 목록에
 * 모아 두고 본문은 번호만 가리킨다. 그래서 쓰는 쪽에 모으는 자리가 하나 더 든다.
 *
 * <p>같은 서식이 두 번 나오면 같은 번호를 돌려준다. 이러지 않으면 문단 수천 개짜리
 * 문서에서 글자모양이 수천 개로 불어나 한글이 느려지거나 파일을 거절한다.
 */
class HwpxRefs {

    /** 줄 간격을 못박았다는 표시. Univer 가 쓰는 값이다. */
    private static final int SPACING_EXACT = 2;

    private static final tools.jackson.databind.json.JsonMapper JSON_READER =
            tools.jackson.databind.json.JsonMapper.builder().build();

    private final HWPXFile file;

    /** 서식의 글자 모양 → 번호. 같은 서식을 두 번 만들지 않는다. */
    private final Map<String, String> charPrIds = new HashMap<>();

    /** 문단 서식의 글자 모양 → 번호. */
    private final Map<String, String> paraPrIds = new HashMap<>();

    /** 칸모양(테두리+바탕색)의 글자 모양 → 번호. */
    private final Map<String, String> borderFillIds = new HashMap<>();

    /** 빈 파일이 깔아 둔 첫 글자모양. 새 것은 이것을 베껴 고친다. */
    private final CharPr baseCharPr;

    /** 빈 파일이 깔아 둔 첫 문단모양. */
    private final ParaPr baseParaPr;

    HwpxRefs(HWPXFile file) {
        this.file = file;
        this.baseCharPr = file.headerXMLFile().refList().charProperties().get(0);
        this.baseParaPr = file.headerXMLFile().refList().paraProperties().get(0);
    }

    /** 서식 없는 글이 쓰는 번호. */
    String defaultCharPr() {
        return baseCharPr.id();
    }

    /** 서식 없는 문단이 쓰는 번호. */
    String defaultParaPr() {
        return baseParaPr.id();
    }

    /**
     * 문단 서식 하나의 번호.
     *
     * <p>{@code style} 은 Univer 모양의 {@code paragraphStyle} 이다 - 가로 맞춤,
     * 들여쓰기와 좌우 여백, 문단 앞뒤 간격, 줄 간격, 다음 쪽에 붙이기.
     */
    String paraPr(JsonNode style) {
        if (style == null || style.isEmpty()) {
            return defaultParaPr();
        }
        String key = style.toString();
        String known = paraPrIds.get(key);
        if (known != null) {
            return known;
        }
        var list = file.headerXMLFile().refList().paraProperties();
        ParaPr made = baseParaPr.clone();
        String id = String.valueOf(list.count());
        made.id(id);
        applyPara(made, style);
        list.add(made);
        paraPrIds.put(key, id);
        return id;
    }

    private void applyPara(ParaPr made, JsonNode style) {
        HorizontalAlign2 align = switch (style.path("horizontalAlign").asInt(0)) {
            case 2 -> HorizontalAlign2.CENTER;
            case 3 -> HorizontalAlign2.RIGHT;
            case 4 -> HorizontalAlign2.JUSTIFY;
            default -> null;
        };
        if (align != null) {
            if (made.align() == null) {
                made.createAlign();
            }
            made.align().horizontal(align);
        }
        margin(made, style, "indentStart", Side.LEFT);
        margin(made, style, "indentEnd", Side.RIGHT);
        margin(made, style, "indentFirstLine", Side.FIRST);
        hanging(made, style);
        margin(made, style, "spaceAbove", Side.ABOVE);
        margin(made, style, "spaceBelow", Side.BELOW);
        lineSpacing(made, style);
        keep(made, style);
    }

    /** 문단 여백의 네 자리와 첫 줄. */
    private enum Side { LEFT, RIGHT, FIRST, ABOVE, BELOW }

    /**
     * 문단 여백 한 자리.
     *
     * <p>담긴 길이는 모두 px 다 - 워드 내보내기가 그 값을 그대로 트윕으로 되돌린다.
     * 문단 앞뒤 간격만 pt 로 보면 1.33 배로 벌어진다.
     */
    private void margin(ParaPr made, JsonNode style, String name, Side side) {
        JsonNode at = style.path(name);
        if (at.isMissingNode() || at.isNull()) {
            return;
        }
        int written = (int) HwpxUnit.px(at.path("v").asDouble(0));
        if (made.margin() == null) {
            made.createMargin();
        }
        var margin = made.margin();
        switch (side) {
            case LEFT -> {
                margin.createLeft();
                margin.left().set(written, ValueUnit2.HWPUNIT);
            }
            case RIGHT -> {
                margin.createRight();
                margin.right().set(written, ValueUnit2.HWPUNIT);
            }
            case FIRST -> {
                margin.createIntent();
                margin.intent().set(written, ValueUnit2.HWPUNIT);
            }
            case ABOVE -> {
                margin.createPrev();
                margin.prev().set(written, ValueUnit2.HWPUNIT);
            }
            default -> {
                margin.createNext();
                margin.next().set(written, ValueUnit2.HWPUNIT);
            }
        }
    }

    /**
     * 내어쓰기.
     *
     * <p>한글은 따로 적지 않고 첫 줄 들여쓰기를 음수로 적는다. 흘리면 번호 매긴
     * 목록의 둘째 줄부터 왼쪽에 붙는다.
     */
    private void hanging(ParaPr made, JsonNode style) {
        JsonNode at = style.path("hanging");
        if (at.isMissingNode() || at.isNull()) {
            return;
        }
        if (made.margin() == null) {
            made.createMargin();
        }
        made.margin().createIntent();
        made.margin().intent().set((int) -HwpxUnit.px(at.path("v").asDouble(0)),
                ValueUnit2.HWPUNIT);
    }

    /**
     * 줄 간격.
     *
     * <p>못박은 것이면 길이로, 아니면 배수로 적는다. 한글은 배수를 백분율로
     * 적는다 - 1.5배가 150 이다.
     */
    private void lineSpacing(ParaPr made, JsonNode style) {
        double multiple = style.path("lineSpacing").asDouble(0);
        if (multiple <= 0) {
            return;
        }
        if (made.lineSpacing() == null) {
            made.createLineSpacing();
        }
        var spacing = made.lineSpacing();
        if (style.path("spacingRule").asInt(0) == SPACING_EXACT) {
            spacing.type(LineSpacingType.AT_LEAST);
            spacing.value((int) HwpxUnit.px(multiple));
            spacing.unit(ValueUnit2.HWPUNIT);
            return;
        }
        // 배수일 때 단위는 건드리지 않는다. 라이브러리의 단위 종류가 HWPUNIT 과
        // CHAR 뿐이라 백분율을 적을 길이 없고, 빈 파일이 깔아 둔 것이 맞다.
        spacing.type(LineSpacingType.PERCENT);
        spacing.value((int) Math.round(multiple * 100));
    }

    /**
     * 칸모양 하나의 번호.
     *
     * <p>한글은 테두리와 바탕색을 한 덩이(BorderFill)로 담는다. 그래서 둘을 묶어
     * 열쇠로 삼는다 - 따로 모으면 바탕색만 다른 칸이 같은 번호를 쓰게 된다.
     *
     * <p>{@code sides} 는 그은 변만 담은 JSON 이다. {@code null} 이면 한 변도 안
     * 긋는다 - 줄 세우기 용도로 쓴 표를 격자로 그리면 원본과 딴판이 된다.
     */
    String borderFill(String sides, String shade) {
        String key = sides + "|" + shade;
        String known = borderFillIds.get(key);
        if (known != null) {
            return known;
        }
        var list = file.headerXMLFile().refList().borderFills();
        BorderFill made = list.get(0).clone();
        String id = String.valueOf(list.count() + 1);
        made.id(id);
        applyFill(made, sides, shade);
        list.add(made);
        borderFillIds.put(key, id);
        return id;
    }

    private void applyFill(BorderFill made, String sides, String shade) {
        JsonNode drawn = read(sides);
        made.createLeftBorder();
        made.createRightBorder();
        made.createTopBorder();
        made.createBottomBorder();
        edge(made.leftBorder(), drawn.path("left"));
        edge(made.rightBorder(), drawn.path("right"));
        edge(made.topBorder(), drawn.path("top"));
        edge(made.bottomBorder(), drawn.path("bottom"));
        if (shade == null || shade.isBlank()) {
            made.removeFillBrush();
            return;
        }
        made.createFillBrush();
        made.fillBrush().createWinBrush();
        made.fillBrush().winBrush().faceColor(shade);
        made.fillBrush().winBrush().hatchColor(shade);
    }

    /** 변 하나. 안 그은 변은 선 없음으로 둔다. */
    private void edge(Border border, JsonNode drawn) {
        if (drawn.isMissingNode() || drawn.isNull()) {
            border.type(LineType2.NONE);
            border.width(LineWidth.MM_0_1);
            border.color("#000000");
            return;
        }
        border.type(LineType2.SOLID);
        border.width(LineWidth.MM_0_12);
        String color = drawn.path("color").asString("#000000");
        border.color(color.isBlank() ? "#000000" : color);
    }

    private JsonNode read(String json) {
        if (json == null || json.isBlank()) {
            return JSON_READER.createObjectNode();
        }
        try {
            return JSON_READER.readTree(json);
        } catch (RuntimeException cannotRead) {
            return JSON_READER.createObjectNode();
        }
    }

    /** 다음 쪽에 붙이기. 제목만 앞 쪽에 홀로 남는 것을 막는다. */
    private void keep(ParaPr made, JsonNode style) {
        boolean next = style.path("keepNext").asInt(0) == 1;
        boolean lines = style.path("keepLines").asInt(0) == 1;
        if (!next && !lines) {
            return;
        }
        if (made.breakSetting() == null) {
            made.createBreakSetting();
        }
        if (next) {
            made.breakSetting().keepWithNext(true);
        }
        if (lines) {
            made.breakSetting().keepLines(true);
        }
    }

    /**
     * 글자 서식 하나의 번호.
     *
     * <p>{@code style} 은 Univer 모양이다 - {@code fs}(크기 pt), {@code bl}·{@code it},
     * {@code ul}·{@code st}, {@code cl.rgb}(글자색).
     */
    String charPr(JsonNode style) {
        if (style == null || style.isEmpty()) {
            return defaultCharPr();
        }
        String key = style.toString();
        String known = charPrIds.get(key);
        if (known != null) {
            return known;
        }
        var list = file.headerXMLFile().refList().charProperties();
        CharPr made = baseCharPr.clone();
        String id = String.valueOf(list.count());
        made.id(id);
        apply(made, style);
        list.add(made);
        charPrIds.put(key, id);
        return id;
    }

    /** 문서 기본 글자 크기를 바꾼다. 서식 없는 글이 이것으로 그려진다. */
    void defaults(DocContent.DocDefaults defaults) {
        if (defaults == null || defaults.fontSize() <= 0) {
            return;
        }
        baseCharPr.height(HwpxUnit.pt(defaults.fontSize()));
    }

    private void apply(CharPr made, JsonNode style) {
        double size = style.path("fs").asDouble(0);
        if (size > 0) {
            made.height(HwpxUnit.pt(size));
        }
        bool(made, style, "bl", true);
        bool(made, style, "it", false);
        // 값을 본다. 있고 없고만 보면 "밑줄 끔"({"s":0})까지 밑줄이 된다.
        if (style.path("ul").path("s").asInt(0) == 1) {
            made.createUnderline();
            made.underline().type(UnderlineType.BOTTOM);
            made.underline().shape(LineType3.SOLID);
        } else {
            made.removeUnderline();
        }
        if (style.path("st").path("s").asInt(0) == 1) {
            made.createStrikeout();
            made.strikeout().shape(LineType2.SOLID);
        } else {
            made.removeStrikeout();
        }
        String color = style.path("cl").path("rgb").asString(null);
        if (color != null && !color.isBlank()) {
            made.textColor(color);
        }
    }

    /**
     * 굵게·기울임을 켜고 끈다.
     *
     * <p>베낀 서식에 이미 켜져 있을 수 있으므로 끄는 것도 해야 한다 - 안 그러면
     * 굵은 글 하나가 뒤따르는 모든 서식을 굵게 만든다.
     */
    private void bool(CharPr made, JsonNode style, String name, boolean bold) {
        boolean on = style.path(name).asInt(0) == 1;
        if (bold) {
            if (on) {
                made.createBold();
            } else {
                made.removeBold();
            }
            return;
        }
        if (on) {
            made.createItalic();
        } else {
            made.removeItalic();
        }
    }
}
