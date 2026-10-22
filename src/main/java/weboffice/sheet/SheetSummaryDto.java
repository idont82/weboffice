package weboffice.sheet;

import com.fasterxml.jackson.annotation.JsonAutoDetect;

/**
 * 시트 한 개의 메타데이터. {@code WorkbookDto} 와 같은 이유(생성 키 수신)로 가변 클래스다.
 *
 * <p>{@code @JsonAutoDetect} 는 {@code LockRow} 와 같은 이유다. 접근자가 record
 * 스타일이라 Jackson 이 게터로 알아보지 못해 응답 본문에 {@code {}} 만 나간다.
 */
@JsonAutoDetect(
        fieldVisibility = JsonAutoDetect.Visibility.ANY,
        getterVisibility = JsonAutoDetect.Visibility.NONE,
        isGetterVisibility = JsonAutoDetect.Visibility.NONE)
public final class SheetSummaryDto {

    private Long sheetSeq;
    private Long workbookSeq;
    private String sheetName;
    private int sheetNo;
    private int maxRowNo;
    private int maxColumnNo;
    /**
     * 격자선(눈금선)을 보일지. DB 에는 Y/N 으로 담고 여기서는 참/거짓으로 다룬다.
     *
     * <p>새로 만든 객체의 기본값이 참인 이유: 넣을 때 이 열을 보내지 않으므로 DB
     * 기본값('Y')이 붙는다. 자바 쪽 기본값이 거짓이면 넣은 직후의 객체와 다시 읽은
     * 객체가 달라진다.
     */
    private boolean showGridlines = true;
    /** 틀 고정. 위에서 몇 행을 붙박아 둘지. 0 이면 없다. */
    private int freezeRowNo;
    /** 틀 고정. 왼쪽에서 몇 열을 붙박아 둘지. 0 이면 없다. */
    private int freezeColumnNo;
    /** 시트 탭 색(#rrggbb). 없으면 색을 안 칠한 것이다. */
    private String tabColor;
    /**
     * 걸러 보기(필터)를 건 자리({@code A1:D20}). 없으면 안 건 것이다.
     *
     * <p>화면에서만 걸러 보면 새로고침에 사라지고 내려받은 파일에도 안 남는다.
     */
    private String filterRange;

    public SheetSummaryDto() {
    }

    public SheetSummaryDto(Long sheetSeq, Long workbookSeq, String sheetName,
                           int sheetNo, int maxRowNo, int maxColumnNo) {
        this.sheetSeq = sheetSeq;
        this.workbookSeq = workbookSeq;
        this.sheetName = sheetName;
        this.sheetNo = sheetNo;
        this.maxRowNo = maxRowNo;
        this.maxColumnNo = maxColumnNo;
    }

    public Long sheetSeq() {
        return sheetSeq;
    }

    public Long workbookSeq() {
        return workbookSeq;
    }

    public String sheetName() {
        return sheetName;
    }

    public int sheetNo() {
        return sheetNo;
    }

    public int maxRowNo() {
        return maxRowNo;
    }

    public int maxColumnNo() {
        return maxColumnNo;
    }

    public boolean showGridlines() {
        return showGridlines;
    }

    public int freezeRowNo() {
        return freezeRowNo;
    }

    public int freezeColumnNo() {
        return freezeColumnNo;
    }

    public String tabColor() {
        return tabColor;
    }

    public String filterRange() {
        return filterRange;
    }

    public void setSheetSeq(Long sheetSeq) {
        this.sheetSeq = sheetSeq;
    }

    public void setWorkbookSeq(Long workbookSeq) {
        this.workbookSeq = workbookSeq;
    }

    public void setSheetName(String sheetName) {
        this.sheetName = sheetName;
    }

    public void setSheetNo(int sheetNo) {
        this.sheetNo = sheetNo;
    }

    public void setMaxRowNo(int maxRowNo) {
        this.maxRowNo = maxRowNo;
    }

    public void setMaxColumnNo(int maxColumnNo) {
        this.maxColumnNo = maxColumnNo;
    }

    public void setShowGridlines(boolean showGridlines) {
        this.showGridlines = showGridlines;
    }

    public void setFreezeRowNo(int freezeRowNo) {
        this.freezeRowNo = freezeRowNo;
    }

    public void setFreezeColumnNo(int freezeColumnNo) {
        this.freezeColumnNo = freezeColumnNo;
    }

    public void setTabColor(String tabColor) {
        this.tabColor = tabColor;
    }

    public void setFilterRange(String filterRange) {
        this.filterRange = filterRange;
    }
}
