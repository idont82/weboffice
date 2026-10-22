package weboffice.drawing;

/**
 * 올릴 수 없는 이미지.
 *
 * <p>{@code IllegalArgumentException} 을 상속하지 않는다. 상속하면 기존
 * {@code ApiExceptionHandler} 가 400 으로 잡아 주긴 하지만, "잘못된 인자" 와
 * "지원하지 않는 형식" 은 사용자에게 다른 말을 해야 한다.
 */
public class UnsupportedImageException extends RuntimeException {

    public UnsupportedImageException(String message) {
        super(message);
    }
}
