package weboffice.workbook;

/** 권한 부족. {@code ApiExceptionHandler} 가 403 으로 옮긴다. */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
