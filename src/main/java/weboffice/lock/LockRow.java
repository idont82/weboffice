package weboffice.lock;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonAutoDetect;

import weboffice.sheet.CellRange;

/**
 * 범위 잠금 한 행. {@code WorkbookDto} 와 같은 이유(생성 키 수신)로 가변 클래스다.
 *
 * <p>{@code @JsonAutoDetect} 가 필요한 이유: 이 타입은 REST 응답 본문으로 나가는데,
 * 접근자가 record 스타일({@code lockSeq()})이라 Jackson 이 게터로 알아보지 못한다.
 * 그대로 두면 조용히 {@code {}} 가 나간다. 필드를 직접 읽게 해서 해결한다.
 * {@code @JsonProperty} 를 아홉 번 붙이는 것보다 의도가 한 줄로 드러난다.
 *
 * <p>애노테이션 패키지가 {@code com.fasterxml.jackson} 인 것은 오타가 아니다.
 * Jackson 3 는 databind·core 만 {@code tools.jackson} 으로 옮겼고 annotations 는
 * 그대로다.
 */
@JsonAutoDetect(
        fieldVisibility = JsonAutoDetect.Visibility.ANY,
        getterVisibility = JsonAutoDetect.Visibility.NONE,
        isGetterVisibility = JsonAutoDetect.Visibility.NONE)
public final class LockRow {

    private Long lockSeq;
    private Long sheetSeq;
    private int fromRowNo;
    private int fromColumnNo;
    private int toRowNo;
    private int toColumnNo;
    private String lockUser;
    /** 자리를 잡은 브라우저 탭. 색을 사람이 아니라 탭 단위로 나눈다. 생성자에 넣지
     *  않는 이유: 인자가 이미 아홉이고, 이것 하나 때문에 부르는 곳을 다 고칠 이유가 없다. */
    private String sessionKey;
    private LocalDateTime acquireDatetime;
    private LocalDateTime expireDatetime;

    public LockRow() {
    }

    public LockRow(Long lockSeq, Long sheetSeq, int fromRowNo, int fromColumnNo,
                   int toRowNo, int toColumnNo, String lockUser,
                   LocalDateTime acquireDatetime, LocalDateTime expireDatetime) {
        this.lockSeq = lockSeq;
        this.sheetSeq = sheetSeq;
        this.fromRowNo = fromRowNo;
        this.fromColumnNo = fromColumnNo;
        this.toRowNo = toRowNo;
        this.toColumnNo = toColumnNo;
        this.lockUser = lockUser;
        this.acquireDatetime = acquireDatetime;
        this.expireDatetime = expireDatetime;
    }

    public Long lockSeq() {
        return lockSeq;
    }

    public Long sheetSeq() {
        return sheetSeq;
    }

    public int fromRowNo() {
        return fromRowNo;
    }

    public int fromColumnNo() {
        return fromColumnNo;
    }

    public int toRowNo() {
        return toRowNo;
    }

    public int toColumnNo() {
        return toColumnNo;
    }

    public String lockUser() {
        return lockUser;
    }

    public LocalDateTime acquireDatetime() {
        return acquireDatetime;
    }

    public LocalDateTime expireDatetime() {
        return expireDatetime;
    }

    public void setLockSeq(Long lockSeq) {
        this.lockSeq = lockSeq;
    }

    public void setSheetSeq(Long sheetSeq) {
        this.sheetSeq = sheetSeq;
    }

    public void setFromRowNo(int fromRowNo) {
        this.fromRowNo = fromRowNo;
    }

    public void setFromColumnNo(int fromColumnNo) {
        this.fromColumnNo = fromColumnNo;
    }

    public void setToRowNo(int toRowNo) {
        this.toRowNo = toRowNo;
    }

    public void setToColumnNo(int toColumnNo) {
        this.toColumnNo = toColumnNo;
    }

    public String sessionKey() {
        return sessionKey;
    }

    public void setSessionKey(String sessionKey) {
        this.sessionKey = sessionKey;
    }

    public void setLockEmp(String lockUser) {
        this.lockUser = lockUser;
    }

    public void setAcquireDatetime(LocalDateTime acquireDatetime) {
        this.acquireDatetime = acquireDatetime;
    }

    public void setExpireDatetime(LocalDateTime expireDatetime) {
        this.expireDatetime = expireDatetime;
    }

    /** 겹침 판정은 {@link CellRange#overlaps} 하나에만 둔다. 자바와 SQL 두 곳에 두면 어긋난다. */
    public CellRange range() {
        return new CellRange(fromRowNo, fromColumnNo, toRowNo, toColumnNo);
    }
}
