package weboffice.doc;

/** 되돌리기가 읽는 문단 로그 한 줄. 그때 값(ORG_VALUE)만 있으면 된다. */
public record DocLogValueRow(
        int paragraphNo,
        String orgValue
) {
}
