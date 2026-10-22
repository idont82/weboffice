package weboffice.security;

/** 인증되지 않은 요청이 사용자 정보를 요구했을 때. {@code ApiExceptionHandler} 가 401 로 옮긴다. */
public class UnauthenticatedException extends RuntimeException {

    public UnauthenticatedException(String message) {
        super(message);
    }
}
