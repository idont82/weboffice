package weboffice.view;

import java.time.LocalDateTime;

/**
 * 보기 세션 한 줄.
 *
 * <p>{@code sessionSeq} 는 담을 때 DB 가 채워 준다. 그래서 record 가 아니라
 * 값이 바뀌는 클래스여야 하지만, MyBatis 의 {@code useGeneratedKeys} 는 setter 를
 * 부른다 - 아래 클래스 모양을 쓴다.
 */
public class ViewSessionRow {

    private Long sessionSeq;
    private final String tokenHash;
    private final String fileName;
    private final String docType;
    private final String fileHash;
    private final Long byteSize;
    private final String callerSystem;
    private final String viewerName;
    private final String encryptedYn;
    private String state;
    private final LocalDateTime expiresAt;
    private LocalDateTime openedAt;
    private LocalDateTime lastSeenAt;
    // 창 세션 쿠키에 실리는 난수의 해시. 발급 시점엔 없고(NULL) 열리는 순간 채워진다.
    // sessionSeq 를 그대로 쿠키에 담으면 1,2,3... 을 순서대로 찍어 남의 문서를
    // 열어 볼 수 있다(IDOR) - 보기 토큰과 같은 방식으로 막는다.
    private String sessionKeyHash;

    public ViewSessionRow(Long sessionSeq, String tokenHash, String fileName, String docType,
                          String fileHash, Long byteSize, String callerSystem, String viewerName,
                          String encryptedYn, String state, LocalDateTime expiresAt,
                          LocalDateTime openedAt, LocalDateTime lastSeenAt, String sessionKeyHash) {
        this.sessionSeq = sessionSeq;
        this.tokenHash = tokenHash;
        this.fileName = fileName;
        this.docType = docType;
        this.fileHash = fileHash;
        this.byteSize = byteSize;
        this.callerSystem = callerSystem;
        this.viewerName = viewerName;
        this.encryptedYn = encryptedYn;
        this.state = state;
        this.expiresAt = expiresAt;
        this.openedAt = openedAt;
        this.lastSeenAt = lastSeenAt;
        this.sessionKeyHash = sessionKeyHash;
    }

    public Long sessionSeq() {
        return sessionSeq;
    }

    public void setSessionSeq(Long sessionSeq) {
        this.sessionSeq = sessionSeq;
    }

    public String tokenHash() {
        return tokenHash;
    }

    public String fileName() {
        return fileName;
    }

    public String docType() {
        return docType;
    }

    public String fileHash() {
        return fileHash;
    }

    public Long byteSize() {
        return byteSize;
    }

    public String callerSystem() {
        return callerSystem;
    }

    public String viewerName() {
        return viewerName;
    }

    public String encryptedYn() {
        return encryptedYn;
    }

    public String state() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public LocalDateTime expiresAt() {
        return expiresAt;
    }

    public LocalDateTime openedAt() {
        return openedAt;
    }

    public void setOpenedAt(LocalDateTime openedAt) {
        this.openedAt = openedAt;
    }

    public LocalDateTime lastSeenAt() {
        return lastSeenAt;
    }

    public void setLastSeenAt(LocalDateTime lastSeenAt) {
        this.lastSeenAt = lastSeenAt;
    }

    public String sessionKeyHash() {
        return sessionKeyHash;
    }

    public void setSessionKeyHash(String sessionKeyHash) {
        this.sessionKeyHash = sessionKeyHash;
    }
}
