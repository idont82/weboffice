package weboffice.sheet;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

import weboffice.excel.CellDto;

/**
 * 시트를 열 때 내려보내는 셀 묶음.
 *
 * <p>셀마다 서식 JSON 을 통째로 실으면 같은 서식이 수천 번 되풀이된다. IT 업무
 * 진행표 2026 은 시트 한 장이 6MB, 아홉 장이 41MB 였다 - 서버가 만드는 데 1.5초,
 * 브라우저가 읽는 데 또 그만큼 걸려 빈 화면이 몇 초 이어졌다(2026-08-20 지적).
 *
 * <p>서식은 사전에 한 번만 싣고 셀은 그 번호만 든다. 같은 시트에서 2.1MB 가
 * 0.54MB 가 된다(실측). 비어 있는 칸은 아예 싣지 않는다.
 */
public record CellPage(List<String> styles, List<Cell> cells) {

    /** 셀 하나. 값이 없는 자리는 JSON 에 안 실린다. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Cell(
            int rowNo,
            int columnNo,
            CellDto.ValueType valueType,
            String textValue,
            BigDecimal numberValue,
            LocalDateTime datetimeValue,
            String booleanYn,
            String formulaValue,
            /** {@link #styles} 안의 자리. 서식이 없으면 비운다. */
            Integer styleNo
    ) {
    }

    /** 셀 목록을 사전과 번호로 나눈다. 서식이 같으면 한 번만 싣는다. */
    public static CellPage of(List<CellDto> cells) {
        List<String> styles = new ArrayList<>();
        Map<String, Integer> numberOf = new HashMap<>();
        List<Cell> rows = new ArrayList<>(cells.size());
        for (CellDto cell : cells) {
            Integer styleNo = null;
            if (cell.styleValue() != null) {
                styleNo = numberOf.computeIfAbsent(cell.styleValue(), value -> {
                    styles.add(value);
                    return styles.size() - 1;
                });
            }
            rows.add(new Cell(cell.rowNo(), cell.columnNo(), cell.valueType(),
                    cell.textValue(), cell.numberValue(), cell.datetimeValue(),
                    cell.booleanYn(), cell.formulaValue(), styleNo));
        }
        return new CellPage(styles, rows);
    }
}
