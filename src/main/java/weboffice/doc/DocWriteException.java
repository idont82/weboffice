package weboffice.doc;

/**
 * 문서를 쓰지 못했다.
 *
 * <p>읽기 쪽 {@link DocImportException} 과 짝이다. 반쯤 만들어진 파일을 내주지
 * 않는다 - 열리지 않는 파일을 받는 쪽이 더 나쁘다.
 */
public class DocWriteException extends RuntimeException {

    public DocWriteException(String message, Throwable cause) {
        super(message, cause);
    }
}
