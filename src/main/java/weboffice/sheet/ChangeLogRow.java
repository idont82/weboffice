package weboffice.sheet;

/** wo_cell_change_log 한 행. orgValue/modValue 는 JSON 문자열이다. */
public record ChangeLogRow(
        Long sheetSeq,
        int rowNo,
        int columnNo,
        String changeType,
        String orgValue,
        String modValue
) {
}
