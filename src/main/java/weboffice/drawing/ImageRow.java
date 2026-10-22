package weboffice.drawing;

/**
 * 이미지 한 행.
 *
 * <p>record 가 아닌 이유: MyBatis 의 {@code useGeneratedKeys} 가 IMAGE_SEQ 를
 * setter 로 되돌려 주므로 불변 타입을 쓸 수 없다. {@code LockRow}·{@code WorkbookDto}
 * 와 같은 사정이며, 접근자 이름을 record 스타일로 맞춰 호출부에서 구분이 없게 했다.
 *
 * <p>{@code @JsonAutoDetect} 는 붙이지 않는다. 이 타입은 REST 응답 본문으로
 * 나가지 않는다 - 업로드 응답은 컨트롤러가 Map 으로 만들고, 다운로드는 바이트다.
 * 응답으로 내보내게 되면 그때 붙여야 한다. 안 붙이면 조용히 {@code {}} 가 나간다.
 */
public final class ImageRow {

    private Long imageSeq;
    private Long workbookSeq;
    private String imageHash;
    private String mimeType;
    private int imageSize;
    private byte[] imageData;

    public ImageRow() {
    }

    public ImageRow(Long imageSeq, Long workbookSeq, String imageHash,
                    String mimeType, int imageSize, byte[] imageData) {
        this.imageSeq = imageSeq;
        this.workbookSeq = workbookSeq;
        this.imageHash = imageHash;
        this.mimeType = mimeType;
        this.imageSize = imageSize;
        this.imageData = imageData;
    }

    public Long imageSeq() {
        return imageSeq;
    }

    public Long workbookSeq() {
        return workbookSeq;
    }

    public String imageHash() {
        return imageHash;
    }

    public String mimeType() {
        return mimeType;
    }

    public int imageSize() {
        return imageSize;
    }

    public byte[] imageData() {
        return imageData;
    }

    public void setImageSeq(Long imageSeq) {
        this.imageSeq = imageSeq;
    }

    public void setWorkbookSeq(Long workbookSeq) {
        this.workbookSeq = workbookSeq;
    }

    public void setImageHash(String imageHash) {
        this.imageHash = imageHash;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public void setImageSize(int imageSize) {
        this.imageSize = imageSize;
    }

    public void setImageData(byte[] imageData) {
        this.imageData = imageData;
    }
}
