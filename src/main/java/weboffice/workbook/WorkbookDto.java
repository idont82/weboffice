package weboffice.workbook;

import com.fasterxml.jackson.annotation.JsonAutoDetect;

/**
 * 문서 마스터.
 *
 * <p>record 가 아닌 이유: MyBatis 의 {@code useGeneratedKeys} 는 INSERT 후 생성된
 * 키를 setter 로 되돌려 주므로 불변 타입을 쓸 수 없다. 대신 접근자 이름을 record
 * 스타일로 맞춰 호출부에서 구분이 필요 없게 했다.
 *
 * <p>그 대가로 Jackson 이 접근자를 게터로 알아보지 못한다. 응답 본문으로 내보내는
 * 순간 조용히 {@code {}} 가 나가므로 {@code @JsonAutoDetect} 를 미리 붙여 둔다
 * ({@code LockRow} 와 {@code SheetSummaryDto} 가 실제로 그 일을 겪었다).
 */
@JsonAutoDetect(
        fieldVisibility = JsonAutoDetect.Visibility.ANY,
        getterVisibility = JsonAutoDetect.Visibility.NONE,
        isGetterVisibility = JsonAutoDetect.Visibility.NONE)
public final class WorkbookDto {

    private Long workbookSeq;
    private String workbookName;
    private String ownerUser;
    private Integer docVer;
    /** 문서 종류. 기존 생성자를 쓰는 곳이 많아 세터로만 채운다. */
    private String docType;
    private String useYn;
    /**
     * 공개 링크 토큰. 있으면 공개, 없으면 비공개다.
     *
     * <p>기존 5인자 생성자를 쓰는 곳이 많아 생성자에 넣지 않고 세터로만 채운다.
     */
    private String shareToken;

    public WorkbookDto() {
    }

    public WorkbookDto(Long workbookSeq, String workbookName, String ownerUser,
                       Integer docVer, String useYn) {
        this.workbookSeq = workbookSeq;
        this.workbookName = workbookName;
        this.ownerUser = ownerUser;
        this.docVer = docVer;
        this.useYn = useYn;
    }

    public Long workbookSeq() {
        return workbookSeq;
    }

    public String workbookName() {
        return workbookName;
    }

    public String ownerUser() {
        return ownerUser;
    }

    public String docType() {
        return docType;
    }

    public void setDocType(String docType) {
        this.docType = docType;
    }

    public Integer docVer() {
        return docVer;
    }

    public String useYn() {
        return useYn;
    }

    public void setWorkbookSeq(Long workbookSeq) {
        this.workbookSeq = workbookSeq;
    }

    public void setWorkbookName(String workbookName) {
        this.workbookName = workbookName;
    }

    public void setOwnerEmp(String ownerUser) {
        this.ownerUser = ownerUser;
    }

    public void setDocVer(Integer docVer) {
        this.docVer = docVer;
    }

    public void setUseYn(String useYn) {
        this.useYn = useYn;
    }

    public String shareToken() {
        return shareToken;
    }

    public void setShareToken(String shareToken) {
        this.shareToken = shareToken;
    }
}
