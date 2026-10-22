package weboffice.view;

import java.util.Locale;

/** 파일 이름으로 문서 종류를 가린다. 내용까지 뜯지 않는다 - 확장자로 충분했다. */
public enum DocKind {

    XLSX("SHEET"),
    DOCX("DOC"),
    HWP("DOC"),
    HWPX("DOC"),
    PPTX("SLIDE"),
    UNKNOWN(null);

    private final String docType;

    DocKind(String docType) {
        this.docType = docType;
    }

    /** 화면이 쓰는 종류. 모르는 형식이면 null */
    public String docType() {
        return docType;
    }

    public static DocKind of(String fileName) {
        if (fileName == null) {
            return UNKNOWN;
        }
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".xlsx")) {
            return XLSX;
        }
        if (lower.endsWith(".docx")) {
            return DOCX;
        }
        if (lower.endsWith(".hwpx")) {
            return HWPX;
        }
        if (lower.endsWith(".hwp")) {
            return HWP;
        }
        if (lower.endsWith(".pptx")) {
            return PPTX;
        }
        return UNKNOWN;
    }
}
