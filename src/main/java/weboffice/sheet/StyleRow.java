package weboffice.sheet;

/** wo_cell_style_master 한 행. 생성 키를 받아야 하므로 가변 클래스다. */
public final class StyleRow {

    private Long styleSeq;
    private Long workbookSeq;
    private String styleCode;
    private String styleValue;

    public StyleRow() {
    }

    public StyleRow(Long styleSeq, Long workbookSeq, String styleCode, String styleValue) {
        this.styleSeq = styleSeq;
        this.workbookSeq = workbookSeq;
        this.styleCode = styleCode;
        this.styleValue = styleValue;
    }

    public Long styleSeq() {
        return styleSeq;
    }

    public Long workbookSeq() {
        return workbookSeq;
    }

    public String styleCode() {
        return styleCode;
    }

    public String styleValue() {
        return styleValue;
    }

    public void setStyleSeq(Long styleSeq) {
        this.styleSeq = styleSeq;
    }

    public void setWorkbookSeq(Long workbookSeq) {
        this.workbookSeq = workbookSeq;
    }

    public void setStyleCode(String styleCode) {
        this.styleCode = styleCode;
    }

    public void setStyleValue(String styleValue) {
        this.styleValue = styleValue;
    }
}
