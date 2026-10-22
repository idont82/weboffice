package weboffice.doc;

/** wo_doc_change_log 한 행. 값은 문단 JSON 문자열이다. */
public record DocChangeLogRow(
        Long workbookSeq,
        int paragraphNo,
        String changeType,
        String orgValue,
        String modValue
) {
}
