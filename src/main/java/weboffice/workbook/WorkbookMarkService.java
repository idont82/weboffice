package weboffice.workbook;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 별표(즐겨찾기)와 색 딱지.
 *
 * <p>색은 일곱 가지로 못박는다. macOS 파인더의 태그와 같은 수인데, 이보다 많으면
 * 색만 보고 무슨 뜻인지 못 알아본다 - 색은 분류가 아니라 눈에 띄게 하는 것이다.
 * 더 잘게 나누고 싶으면 이름을 붙여 쓴다.
 */
@Service
public class WorkbookMarkService {

    /** 고를 수 있는 색. 순서가 화면의 칩 순서다. */
    public static final List<String> COLORS =
            List.of("RED", "ORANGE", "YELLOW", "GREEN", "BLUE", "PURPLE", "GRAY");

    /** 이름을 안 지었을 때 부르는 말. */
    private static final Map<String, String> DEFAULT_NAMES = Map.of(
            "RED", "빨강", "ORANGE", "주황", "YELLOW", "노랑", "GREEN", "초록",
            "BLUE", "파랑", "PURPLE", "보라", "GRAY", "회색");

    /** 이름은 짧게. 칩 하나에 들어가야 한다. */
    private static final int NAME_MAX = 20;

    private final WorkbookMarkMapper markMapper;

    public WorkbookMarkService(WorkbookMarkMapper markMapper) {
        this.markMapper = markMapper;
    }

    /** 내 표시 전부. 문서 번호로 찾는다. */
    public Map<Long, WorkbookMarkRow> marksOf(String userKey) {
        return markMapper.selectMarks(userKey).stream()
                .collect(Collectors.toMap(WorkbookMarkRow::workbookSeq,
                        Function.identity(), (first, second) -> first));
    }

    /**
     * 표시를 바꾼다.
     *
     * <p>별표와 색을 함께 받는다. 둘이 한 행이라 따로 받으면 다른 쪽을 지우게 된다 -
     * 화면이 지금 상태를 알고 있으니 통째로 보내게 한다.
     */
    @Transactional
    public void mark(String userKey, Long workbookSeq, boolean starred, String color) {
        String colorCode = normalizeColor(color);
        if (!starred && colorCode == null) {
            markMapper.deleteMark(userKey, workbookSeq);
            return;
        }
        markMapper.upsertMark(userKey, workbookSeq, starred ? "Y" : "N", colorCode);
    }

    /** 색 이름 일곱 개. 안 지은 것은 기본 이름으로 채워 언제나 일곱 줄이다. */
    public List<ColorLabelRow> colorLabels(String userKey) {
        Map<String, String> named = markMapper.selectColorLabels(userKey).stream()
                .collect(Collectors.toMap(ColorLabelRow::colorCode, ColorLabelRow::labelName,
                        (first, second) -> first, LinkedHashMap::new));
        return COLORS.stream()
                .map(code -> new ColorLabelRow(code,
                        named.getOrDefault(code, DEFAULT_NAMES.get(code))))
                .toList();
    }

    /** 색에 이름을 붙인다. 비우면 기본 이름으로 돌아간다. */
    @Transactional
    public void renameColor(String userKey, String color, String name) {
        String colorCode = normalizeColor(color);
        if (colorCode == null) {
            throw new IllegalArgumentException("모르는 색입니다: " + color);
        }
        String wanted = name == null ? "" : name.trim();
        if (wanted.isEmpty() || wanted.equals(DEFAULT_NAMES.get(colorCode))) {
            markMapper.deleteColorLabel(userKey, colorCode);
            return;
        }
        markMapper.upsertColorLabel(userKey, colorCode,
                wanted.length() <= NAME_MAX ? wanted : wanted.substring(0, NAME_MAX));
    }

    /**
     * 아는 색이면 그대로, 아니면 null.
     *
     * <p>모르는 색을 오류로 던지지 않는다 - 색을 떼는 것과 같은 뜻으로 받는다.
     * 화면이 보내는 값은 우리가 준 목록에서 온다.
     */
    private static String normalizeColor(String color) {
        if (color == null) {
            return null;
        }
        String upper = color.trim().toUpperCase(java.util.Locale.ROOT);
        return COLORS.contains(upper) ? upper : null;
    }
}
