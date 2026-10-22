package weboffice.view;

import org.springframework.http.HttpStatus;

/** 부르는 시스템이 무엇이 틀렸는지 알아야 한다. 코드와 상태를 한곳에 못 박는다. */
public enum ViewErrorCode {

    MISSING_SYSTEM_NAME(HttpStatus.BAD_REQUEST, "부르는 시스템 이름(systemName)이 없습니다"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "열쇠가 없거나 맞지 않습니다"),
    FORBIDDEN_ADDRESS(HttpStatus.FORBIDDEN, "허용되지 않은 주소입니다"),
    FETCH_FAILED(HttpStatus.BAD_GATEWAY, "문서를 받아 오지 못했습니다"),
    TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "문서가 너무 큽니다"),
    DECRYPT_FAILED(HttpStatus.UNPROCESSABLE_ENTITY, "잠긴 문서를 풀지 못했습니다"),
    UNSUPPORTED_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "볼 수 없는 형식입니다"),
    CONVERT_FAILED(HttpStatus.UNPROCESSABLE_ENTITY, "문서를 읽지 못했습니다"),
    TOKEN_EXPIRED(HttpStatus.GONE, "링크가 만료되었습니다"),
    TOKEN_USED(HttpStatus.GONE, "이미 열린 링크입니다"),
    // 운영자가 내린 비상 차단기(weboffice.view.enabled=false). 부르는 시스템이
    // "지금은 안 되지만 우리가 틀린 것은 아니다" 로 알아듣게 503 을 준다.
    DISABLED(HttpStatus.SERVICE_UNAVAILABLE, "문서 뷰어가 꺼져 있습니다"),
    NOT_ADMIN(HttpStatus.FORBIDDEN, "관리자만 볼 수 있습니다");

    private final HttpStatus status;
    private final String word;

    ViewErrorCode(HttpStatus status, String word) {
        this.status = status;
        this.word = word;
    }

    public HttpStatus status() {
        return status;
    }

    /** 사람에게 보일 말. 화면과 응답이 같은 말을 쓴다. */
    public String word() {
        return word;
    }
}
