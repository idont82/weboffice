package weboffice.excel;

import java.util.HashMap;
import java.util.Map;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.extensions.XSSFCellBorder.BorderSide;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Univer 서식 JSON ↔ POI {@link CellStyle}.
 *
 * <p>DB 와 화면은 서식 전체를 들고 있었는데 내보내기가 숫자 서식만 실었다. 내려받은
 * 파일의 {@code cellXfs} 가 기본 하나뿐이라 배경색·글자색·굵기·테두리가 전부
 * 빠졌다(2026-08-07 실측). 그 매핑을 여기 한 곳에 모은다.
 *
 * <p>양방향을 한 클래스에 두는 이유: 왕복이 맞는지는 두 방향을 나란히 봐야 알 수
 * 있다. 나눠 두면 한쪽만 고쳐 서식이 왕복마다 조금씩 달라진다 - {@link Emu} 를
 * 한 곳에 모은 것과 같은 이유다.
 *
 * <p>Univer 값 규약(0.25.1):
 * <ul>
 *   <li>{@code bl}/{@code it} 0·1, {@code ul}/{@code st} 는 {@code {s:0|1}}
 *   <li>{@code cl}/{@code bg} 는 {@code {rgb:"#RRGGBB"}}
 *   <li>{@code bd} 는 {@code {t,b,l,r: {s: BorderStyleTypes, cl:{rgb}}}}
 *   <li>{@code ht} 1=왼쪽 2=가운데 3=오른쪽, {@code vt} 1=위 2=가운데 3=아래
 *   <li>{@code tb} 1=넘침 2=자르기 3=줄바꿈(WrapStrategy)
 * </ul>
 */
public final class CellStyleMapper {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    // Univer HorizontalAlign / VerticalAlign / WrapStrategy
    private static final int H_LEFT = 1;
    private static final int H_CENTER = 2;
    private static final int H_RIGHT = 3;
    private static final int V_TOP = 1;
    private static final int V_MIDDLE = 2;
    private static final int V_BOTTOM = 3;
    private static final int WRAP_OVERFLOW = 1;
    private static final int WRAP_CLIP = 2;
    private static final int WRAP_WRAP = 3;

    /**
     * Univer {@code BorderStyleTypes} 순서 그대로다. 인덱스가 곧 값이므로 순서를
     * 바꾸면 테두리 모양이 통째로 어긋난다.
     */
    private static final BorderStyle[] BORDERS = {
            BorderStyle.NONE, BorderStyle.THIN, BorderStyle.HAIR, BorderStyle.DOTTED,
            BorderStyle.DASHED, BorderStyle.DASH_DOT, BorderStyle.DASH_DOT_DOT,
            BorderStyle.DOUBLE, BorderStyle.MEDIUM, BorderStyle.MEDIUM_DASHED,
            BorderStyle.MEDIUM_DASH_DOT, BorderStyle.MEDIUM_DASH_DOT_DOT,
            BorderStyle.SLANTED_DASH_DOT, BorderStyle.THICK,
    };

    /** 서식 JSON 문자열 → CellStyle. 같은 서식을 쓰는 셀들이 한 벌을 나눠 쓴다. */
    private final Map<String, CellStyle> cache = new HashMap<>();

    /**
     * 글꼴 생김새 → Font. 서식이 달라도 글꼴이 같으면 한 벌을 나눠 쓴다.
     *
     * <p>없을 때는 굵기만 다른 서식들이 저마다 글꼴을 하나씩 차지해 {@code fonts}
     * 수가 {@code cellXfs} 수와 같아졌다(2026-08-07 실측). 엑셀의 글꼴 상한은
     * 32,767 이고, 그 전에 파일이 쓸데없이 커진다.
     */
    private final Map<String, Font> fonts = new HashMap<>();

    /**
     * 서식표. 테두리를 직접 찾아볼 때 쓴다.
     *
     * <p>엑셀이 아닌 도구가 만든 파일은 xf 에 applyBorder 를 안 적고, POI 는 그
     * 깃발이 없으면 테두리를 없는 것으로 준다(2026-08-13 실측).
     */
    private final org.apache.poi.xssf.model.StylesTable stylesSource;

    private CellStyleMapper(org.apache.poi.xssf.model.StylesTable stylesSource) {
        this.stylesSource = stylesSource;
    }

    public static CellStyleMapper create() {
        return new CellStyleMapper(null);
    }

    public static CellStyleMapper create(org.apache.poi.xssf.model.StylesTable styles) {
        return new CellStyleMapper(styles);
    }

    // ------------------------------------------------------------------ 내보내기

    /**
     * 서식 JSON 을 CellStyle 로 바꾼다. 서식이 없으면 {@code null}.
     *
     * <p>캐시가 문자열 그대로를 키로 쓴다. 같은 서식이 같은 문자열로 오는 것은
     * {@code STYLE_CODE} 가 그 문자열의 MD5 이기 때문에 보장된다.
     */
    public CellStyle toPoi(Workbook workbook, String styleValue) {
        if (styleValue == null || styleValue.isBlank()) {
            return null;
        }
        return cache.computeIfAbsent(styleValue, json -> build(workbook, json));
    }

    private CellStyle build(Workbook workbook, String styleValue) {
        JsonNode style;
        try {
            style = JSON.readTree(styleValue);
        } catch (RuntimeException e) {
            // 서식 하나 때문에 내려받기 전체가 실패하는 것보다 그 서식을 잃는 편이 낫다.
            return null;
        }

        CellStyle poi = workbook.createCellStyle();
        applyNumberFormat(workbook, poi, style);
        applyFont(workbook, poi, style);
        applyFill(poi, style);
        applyBorders(poi, style);
        applyAlignment(poi, style);
        return poi;
    }

    private void applyNumberFormat(Workbook workbook, CellStyle poi, JsonNode style) {
        String pattern = numberFormatOf(style);
        if (pattern != null && !pattern.isEmpty()) {
            poi.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat(pattern));
        }
    }

    private void applyFont(Workbook workbook, CellStyle poi, JsonNode style) {
        boolean bold = style.path("bl").asInt(0) == 1;
        boolean italic = style.path("it").asInt(0) == 1;
        boolean underline = style.path("ul").path("s").asInt(0) == 1;
        boolean strike = style.path("st").path("s").asInt(0) == 1;
        String family = style.path("ff").asString(null);
        int size = style.path("fs").asInt(0);
        String color = rgbOf(style.path("cl"));

        if (!bold && !italic && !underline && !strike
                && family == null && size == 0 && color == null) {
            return;
        }

        String key = bold + "|" + italic + "|" + underline + "|" + strike
                + "|" + family + "|" + size + "|" + color;
        poi.setFont(fonts.computeIfAbsent(key, ignored -> {
            Font font = workbook.createFont();
            font.setBold(bold);
            font.setItalic(italic);
            font.setStrikeout(strike);
            if (underline) {
                font.setUnderline(Font.U_SINGLE);
            }
            if (family != null) {
                font.setFontName(family);
            }
            if (size > 0) {
                font.setFontHeightInPoints((short) size);
            }
            if (color != null && font instanceof XSSFFont xssfFont) {
                xssfFont.setColor(xssfColor(color));
            }
            return font;
        }));
    }

    private void applyFill(CellStyle poi, JsonNode style) {
        String color = rgbOf(style.path("bg"));
        if (color == null || !(poi instanceof XSSFCellStyle xssf)) {
            return;
        }
        // 패턴을 먼저 세우지 않으면 색을 넣어도 칠해지지 않는다.
        xssf.setFillForegroundColor(xssfColor(color));
        xssf.setFillPattern(FillPatternType.SOLID_FOREGROUND);
    }

    private void applyBorders(CellStyle poi, JsonNode style) {
        JsonNode border = style.path("bd");
        if (border.isMissingNode() || border.isNull()) {
            return;
        }
        side(poi, border.path("t"), BorderSide.TOP, poi::setBorderTop);
        side(poi, border.path("b"), BorderSide.BOTTOM, poi::setBorderBottom);
        side(poi, border.path("l"), BorderSide.LEFT, poi::setBorderLeft);
        side(poi, border.path("r"), BorderSide.RIGHT, poi::setBorderRight);
    }

    private void side(CellStyle poi, JsonNode node, BorderSide which,
                      java.util.function.Consumer<BorderStyle> setStyle) {
        if (node.isMissingNode() || node.isNull()) {
            return;
        }
        int index = node.path("s").asInt(0);
        if (index <= 0 || index >= BORDERS.length) {
            return;
        }
        setStyle.accept(BORDERS[index]);

        String color = rgbOf(node.path("cl"));
        // 인덱스 색(setTopBorderColor)은 24비트를 못 담는다. XSSF 경로에서만 색을 준다.
        if (color != null && poi instanceof XSSFCellStyle xssf) {
            xssf.setBorderColor(which, xssfColor(color));
        }
    }

    private void applyAlignment(CellStyle poi, JsonNode style) {
        switch (style.path("ht").asInt(0)) {
            case H_LEFT -> poi.setAlignment(HorizontalAlignment.LEFT);
            case H_CENTER -> poi.setAlignment(HorizontalAlignment.CENTER);
            case H_RIGHT -> poi.setAlignment(HorizontalAlignment.RIGHT);
            default -> { }
        }
        switch (style.path("vt").asInt(0)) {
            case V_TOP -> poi.setVerticalAlignment(VerticalAlignment.TOP);
            case V_MIDDLE -> poi.setVerticalAlignment(VerticalAlignment.CENTER);
            case V_BOTTOM -> poi.setVerticalAlignment(VerticalAlignment.BOTTOM);
            default -> { }
        }
        if (style.path("tb").asInt(0) == WRAP_WRAP) {
            poi.setWrapText(true);
        }
    }

    // ------------------------------------------------------------------ 가져오기

    /**
     * POI CellStyle 을 Univer 서식 JSON 으로 바꾼다. 담을 것이 없으면 {@code null}.
     *
     * <p>기본값은 적지 않는다. 전부 적으면 셀마다 같은 기본 서식이 붙어
     * {@code wo_cell_style_master} 가 한 벌로 모이지 못한다.
     */
    public String toUniver(CellStyle poi, String numberFormat, Font font) {
        ObjectNode style = JSON.createObjectNode();

        if (numberFormat != null && !numberFormat.isEmpty()) {
            style.putObject("n").put("pattern", numberFormat);
        }
        writeFont(style, font);
        writeFill(style, poi);
        writeBorders(style, poi);
        writeAlignment(style, poi);

        return style.isEmpty() ? null : style.toString();
    }

    private void writeFont(ObjectNode style, Font font) {
        if (font == null) {
            return;
        }
        if (font.getBold()) {
            style.put("bl", 1);
        }
        if (font.getItalic()) {
            style.put("it", 1);
        }
        if (font.getStrikeout()) {
            style.putObject("st").put("s", 1);
        }
        if (font.getUnderline() != Font.U_NONE) {
            style.putObject("ul").put("s", 1);
        }
        // 기본 글꼴은 적지 않는다. POI 가 이름 없는 글꼴을 주지 않으므로 값으로 가른다.
        String name = font.getFontName();
        if (name != null && !name.isEmpty() && !"Calibri".equals(name)) {
            style.put("ff", name);
        }
        if (font.getFontHeightInPoints() > 0 && font.getFontHeightInPoints() != 11) {
            style.put("fs", font.getFontHeightInPoints());
        }
        if (font instanceof XSSFFont xssfFont) {
            // 자동 색은 적지 않는다. POI 는 색을 안 준 글꼴에도 "자동" 을 돌려주는데
            // 그것을 적으면 서식이 없는 셀까지 서식을 갖게 된다.
            XSSFColor color = xssfFont.getXSSFColor();
            String hex = color == null || color.isAuto() ? null : hexOf(color);
            if (hex != null) {
                style.putObject("cl").put("rgb", hex);
            }
        }
    }

    private void writeFill(ObjectNode style, CellStyle poi) {
        if (!(poi instanceof XSSFCellStyle xssf)
                || xssf.getFillPattern() != FillPatternType.SOLID_FOREGROUND) {
            return;
        }
        String color = hexOf(xssf.getFillForegroundXSSFColor());
        if (color != null) {
            style.putObject("bg").put("rgb", color);
        }
    }

    private void writeBorders(ObjectNode style, CellStyle poi) {
        ObjectNode border = JSON.createObjectNode();
        writeSide(border, "t", borderStyle(poi, BorderSide.TOP),
                borderColor(poi, BorderSide.TOP));
        writeSide(border, "b", borderStyle(poi, BorderSide.BOTTOM),
                borderColor(poi, BorderSide.BOTTOM));
        writeSide(border, "l", borderStyle(poi, BorderSide.LEFT),
                borderColor(poi, BorderSide.LEFT));
        writeSide(border, "r", borderStyle(poi, BorderSide.RIGHT),
                borderColor(poi, BorderSide.RIGHT));
        if (!border.isEmpty()) {
            style.set("bd", border);
        }
    }

    /**
     * 한 쪽 테두리 모양.
     *
     * <p>엑셀이 아닌 도구가 만든 파일은 xf 에 {@code applyBorder} 를 안 적는다.
     * POI 는 그 깃발이 없으면 테두리를 없는 것으로 준다. 그래서 깃발이 내려간
     * 서식은 {@code borderId} 가 가리키는 테두리를 직접 본다(2026-08-13 실측:
     * 테두리를 그린 파일인데 격자가 통째로 사라졌다).
     */
    private BorderStyle borderStyle(CellStyle poi, BorderSide side) {
        BorderStyle told = switch (side) {
            case TOP -> poi.getBorderTop();
            case BOTTOM -> poi.getBorderBottom();
            case LEFT -> poi.getBorderLeft();
            case RIGHT -> poi.getBorderRight();
            default -> BorderStyle.NONE;
        };
        if (told != BorderStyle.NONE || !(poi instanceof XSSFCellStyle xssf)) {
            return told;
        }
        var drawn = borderOf(xssf);
        return drawn == null ? told : drawn.getBorderStyle(side);
    }

    /** 서식이 가리키는 테두리 한 벌. 없거나 못 찾으면 null. */
    private org.apache.poi.xssf.usermodel.extensions.XSSFCellBorder borderOf(
            XSSFCellStyle style) {
        var xf = style.getCoreXf();
        if (stylesSource == null || xf == null || !xf.isSetBorderId()
                || xf.getBorderId() == 0) {
            return null;
        }
        try {
            return stylesSource.getBorderAt((int) xf.getBorderId());
        } catch (RuntimeException notThere) {
            // 서식표가 어긋난 파일이다. 테두리 하나 때문에 파일을 못 열 수는 없다.
            return null;
        }
    }

    private void writeSide(ObjectNode border, String key, BorderStyle poiStyle, String color) {
        if (poiStyle == null || poiStyle == BorderStyle.NONE) {
            return;
        }
        int index = indexOf(poiStyle);
        if (index <= 0) {
            return;
        }
        ObjectNode side = border.putObject(key);
        side.put("s", index);
        // 색이 없으면 검정으로 둔다. 없는 채로 두면 화면이 기본 회색으로 그린다.
        side.putObject("cl").put("rgb", color == null ? "#000000" : color);
    }

    private String borderColor(CellStyle poi, BorderSide side) {
        if (!(poi instanceof XSSFCellStyle xssf)) {
            return null;
        }
        String told = hexOf(xssf.getBorderColor(side));
        if (told != null) {
            return told;
        }
        var drawn = borderOf(xssf);
        return drawn == null ? null : hexOf(drawn.getBorderColor(side));
    }

    private void writeAlignment(ObjectNode style, CellStyle poi) {
        switch (poi.getAlignment()) {
            case LEFT -> style.put("ht", H_LEFT);
            case CENTER -> style.put("ht", H_CENTER);
            case RIGHT -> style.put("ht", H_RIGHT);
            default -> { }
        }
        switch (poi.getVerticalAlignment()) {
            case TOP -> style.put("vt", V_TOP);
            case CENTER -> style.put("vt", V_MIDDLE);
            // BOTTOM 은 적지 않는다. POI 가 세로 정렬을 안 준 셀에도 BOTTOM 을
            // 돌려주고, Univer 도 값이 없으면 아래로 그린다 - 적으면 서식이 없는
            // 셀까지 서식을 갖게 되고 화면 결과는 같다.
            default -> { }
        }
        if (poi.getWrapText()) {
            style.put("tb", WRAP_WRAP);
        }
    }

    // ------------------------------------------------------------------ 공통

    /**
     * 서식 JSON 에서 숫자 서식만 꺼낸다.
     *
     * <p>문자열을 뒤지지 않고 파서를 쓴다. PostgreSQL 의 jsonb 는 저장할 때 값을
     * 재직렬화해 콜론 뒤에 공백을 붙이는데, 그것 때문에 숫자 서식이 통째로 사라진
     * 적이 있다(2026-08-05 실 PG 실측).
     */
    public static String numberFormatOf(String styleValue) {
        if (styleValue == null || styleValue.isBlank()) {
            return null;
        }
        try {
            return numberFormatOf(JSON.readTree(styleValue));
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String numberFormatOf(JsonNode style) {
        JsonNode pattern = style.path("n").path("pattern");
        if (!pattern.isMissingNode() && !pattern.isNull()) {
            return pattern.asString();
        }
        JsonNode legacy = style.get("numberFormat");
        return legacy == null || legacy.isNull() ? null : legacy.asString();
    }

    private static int indexOf(BorderStyle style) {
        for (int i = 1; i < BORDERS.length; i++) {
            if (BORDERS[i] == style) {
                return i;
            }
        }
        return 0;
    }

    private static String rgbOf(JsonNode node) {
        JsonNode rgb = node.path("rgb");
        if (rgb.isMissingNode() || rgb.isNull()) {
            return null;
        }
        String value = rgb.asString();
        return value == null || value.isBlank() ? null : value;
    }

    /**
     * {@code #RRGGBB} · {@code rgb(r,g,b)} 를 XSSFColor 로 바꾼다.
     *
     * <p>두 모양이 다 온다. 엑셀 클립보드에서 붙여넣으면 {@code rgb(237,125,49)} 로,
     * 툴바에서 고르면 {@code #ED7D31} 로 들어온다(2026-08-06 실측).
     */
    private static XSSFColor xssfColor(String value) {
        int[] rgb = parseRgb(value);
        return new XSSFColor(new byte[] { (byte) rgb[0], (byte) rgb[1], (byte) rgb[2] }, null);
    }

    private static int[] parseRgb(String value) {
        String text = value.trim();
        if (text.startsWith("#")) {
            String hex = text.substring(1);
            if (hex.length() == 3) {
                hex = "" + hex.charAt(0) + hex.charAt(0) + hex.charAt(1) + hex.charAt(1)
                        + hex.charAt(2) + hex.charAt(2);
            }
            if (hex.length() >= 6) {
                return new int[] {
                        Integer.parseInt(hex.substring(0, 2), 16),
                        Integer.parseInt(hex.substring(2, 4), 16),
                        Integer.parseInt(hex.substring(4, 6), 16),
                };
            }
        }
        if (text.toLowerCase().startsWith("rgb")) {
            String[] parts = text.replaceAll("[^0-9,]", "").split(",");
            if (parts.length >= 3) {
                return new int[] {
                        Integer.parseInt(parts[0]),
                        Integer.parseInt(parts[1]),
                        Integer.parseInt(parts[2]),
                };
            }
        }
        return new int[] { 0, 0, 0 };
    }

    private static String hexOf(XSSFColor color) {
        if (color == null) {
            return null;
        }
        // 명암(tint)까지 입힌 값을 쓴다. 엑셀은 "흰색 배경1, 15% 더 어둡게" 를
        // 테마 0 + tint -0.15 로 적는데, tint 를 안 보면 그냥 흰색이 된다 -
        // 업무 진행표의 회색 막대가 통째로 사라지고 머리글은 검게 나왔다
        // (2026-08-19 지적). POI 의 getRGBWithTint 는 안 쓴다 - 채널마다 따로
        // 세고 값을 버려서 엑셀과 한 단계씩 어긋난다({@link Tint}).
        byte[] rgb = Tint.applied(color.getRGB(), color.getTint());
        if (rgb == null || rgb.length < 3) {
            return null;
        }
        return String.format("#%02X%02X%02X", rgb[0] & 0xFF, rgb[1] & 0xFF, rgb[2] & 0xFF);
    }
}
