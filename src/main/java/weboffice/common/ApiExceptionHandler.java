package weboffice.common;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import weboffice.doc.DocImportException;
import weboffice.drawing.UnsupportedImageException;
import weboffice.security.UnauthenticatedException;
import weboffice.view.ViewException;
import weboffice.workbook.ForbiddenException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(UnauthenticatedException.class)
    public ResponseEntity<Map<String, String>> handleUnauthenticated(UnauthenticatedException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("message", "인증이 필요합니다"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
    }

    /**
     * 보기·관리자 쪽 오류. 코드가 상태를 들고 있다.
     *
     * <p>여기 한 자리에 둔다. 2026-10-22 이전에는 컨트롤러마다 똑같은
     * {@code @ExceptionHandler} 를 들고 있었고, 새 관리자 화면을 더하면서 그
     * 복사를 빠뜨려 "관리자만 볼 수 있습니다" 가 403 이 아니라 500 으로 나갔다
     * (실제 서버로 확인). {@code AdminGate} 주석이 관문에 대해 말한 것과 같은
     * 이유다 - 여러 자리에 같은 것을 두면 한쪽만 고쳐 한쪽이 틀린 채로 남는다.
     *
     * <p>{@code ViewPageController} 만 자기 처리기를 갖는다. 그쪽은 사람이 보는
     * 화면이라 JSON 이 아니라 안내 쪽을 그려야 하고, 컨트롤러에 붙은 처리기가
     * 전역보다 먼저 잡는다.
     */
    @ExceptionHandler(ViewException.class)
    public ResponseEntity<Map<String, String>> handleView(ViewException e) {
        return ResponseEntity.status(e.code().status())
                .body(Map.of("code", e.code().name(), "message", e.getMessage()));
    }

    /**
     * 올릴 수 없는 이미지. {@code IllegalArgumentException} 과 같은 400 이지만
     * 타입을 나눠 두면 "잘못된 인자" 와 "지원하지 않는 형식" 을 다르게 말할 수 있다.
     */
    @ExceptionHandler(UnsupportedImageException.class)
    public ResponseEntity<Map<String, String>> handleUnsupportedImage(
            UnsupportedImageException e) {
        return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
    }

    /**
     * 올린 문서를 읽지 못했다. 400 이지만 타입을 나눠 두면 "확장자를 지원하지 않는다"
     * 와 "파일이 깨졌다" 를 다르게 말할 수 있다.
     */
    @ExceptionHandler(DocImportException.class)
    public ResponseEntity<Map<String, String>> handleDocImport(DocImportException e) {
        return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<Map<String, String>> handleForbidden(ForbiddenException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("message", e.getMessage()));
    }

}
