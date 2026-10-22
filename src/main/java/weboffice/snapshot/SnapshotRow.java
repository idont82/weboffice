package weboffice.snapshot;

import java.time.LocalDateTime;

/**
 * 표에 담기는 스냅샷 한 줄. 본문 바이트까지 들고 다닌다.
 *
 * <p>넣을 때 생성키를 돌려받아야 해서 레코드가 아니다(다른 매퍼 DTO 와 같은 이유).
 */
public final class SnapshotRow {

    private Long snapshotSeq;
    private Long workbookSeq;
    private LocalDateTime takenAt;
    private String takenUser;
    private String reason;
    private String label;
    private String docType;
    private long bodySize;
    private byte[] body;

    public SnapshotRow() {
    }

    public SnapshotRow(Long workbookSeq, LocalDateTime takenAt, String takenUser,
                       String reason, String label, String docType,
                       long bodySize, byte[] body) {
        this.workbookSeq = workbookSeq;
        this.takenAt = takenAt;
        this.takenUser = takenUser;
        this.reason = reason;
        this.label = label;
        this.docType = docType;
        this.bodySize = bodySize;
        this.body = body;
    }

    public Long snapshotSeq() {
        return snapshotSeq;
    }

    public void setSnapshotSeq(Long snapshotSeq) {
        this.snapshotSeq = snapshotSeq;
    }

    public Long workbookSeq() {
        return workbookSeq;
    }

    public void setWorkbookSeq(Long workbookSeq) {
        this.workbookSeq = workbookSeq;
    }

    public LocalDateTime takenAt() {
        return takenAt;
    }

    public void setTakenAt(LocalDateTime takenAt) {
        this.takenAt = takenAt;
    }

    public String takenUser() {
        return takenUser;
    }

    public void setTakenEmp(String takenUser) {
        this.takenUser = takenUser;
    }

    public String reason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String label() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String docType() {
        return docType;
    }

    public void setDocType(String docType) {
        this.docType = docType;
    }

    public long bodySize() {
        return bodySize;
    }

    public void setBodySize(long bodySize) {
        this.bodySize = bodySize;
    }

    public byte[] body() {
        return body;
    }

    public void setBody(byte[] body) {
        this.body = body;
    }
}
