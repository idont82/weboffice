package weboffice.drm;

/** DRM 처리 실패. 삼키지 않는다 - 평문이 새거나 깨진 파일을 읽는 것보다 멈추는 것이 낫다. */
public class DrmException extends RuntimeException {

    public DrmException(String message) {
        super(message);
    }

    public DrmException(String message, Throwable cause) {
        super(message, cause);
    }
}
