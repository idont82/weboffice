package weboffice.view;

/** 코드를 달고 다니는 예외. 컨트롤러가 상태와 이력을 이것으로 정한다. */
public class ViewException extends RuntimeException {

    private final transient ViewErrorCode code;

    public ViewException(ViewErrorCode code) {
        this(code, code.word());
    }

    public ViewException(ViewErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public ViewErrorCode code() {
        return code;
    }
}
