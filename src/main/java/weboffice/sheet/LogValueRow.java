package weboffice.sheet;

/** 되돌리기가 읽는 로그 한 줄. 그때 값(ORG_VALUE)만 있으면 된다. */
public record LogValueRow(
        Long sheetSeq,
        int rowNo,
        int columnNo,
        String orgValue
) {
}
