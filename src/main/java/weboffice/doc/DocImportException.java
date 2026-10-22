package weboffice.doc;

/**
 * 올린 파일을 문서로 읽지 못했다.
 *
 * <p>확장자만 맞고 속이 다른 파일, 깨진 파일, 우리가 모르는 판이 여기로 온다.
 * 빈 문서를 만들어 주지 않는다 - 올린 사람은 자기 글이 들어간 줄 알 것이다.
 */
public class DocImportException extends RuntimeException {

    public DocImportException(String message, Throwable cause) {
        super(message, cause);
    }
}
