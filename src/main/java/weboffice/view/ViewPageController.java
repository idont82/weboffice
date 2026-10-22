package weboffice.view;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.util.HtmlUtils;

/**
 * 사람이 여는 길.
 *
 * <p>토큰은 한 번만 쓰고, 그 뒤로는 창 세션 쿠키가 대신한다. 쿠키는 HttpOnly 라
 * 화면 코드가 읽지 못한다 - 읽을 수 있으면 그것을 퍼 나를 수 있다.
 */
@Controller
public class ViewPageController {

    /** 세션 쿠키 이름. 문서마다 다른 값이 아니라 하나를 덮어 쓴다. */
    static final String COOKIE = "WO_VIEW_SESSION";

    /** 스냅샷 몸통과 짝을 이루는 머리들. 화면(viewerSnapshot.ts)이 같은 이름으로 읽는다. */
    static final String DOC_TYPE_HEADER = "X-Wo-Doc-Type";
    static final String ENCRYPTED_HEADER = "X-Wo-Encrypted";
    static final String FILE_NAME_HEADER = "X-Wo-File-Name";
    static final String VIEWER_NAME_HEADER = "X-Wo-Viewer-Name";

    private final ViewReadService readService;
    private final ViewProperties properties;

    public ViewPageController(ViewReadService readService, ViewProperties properties) {
        this.readService = readService;
        this.properties = properties;
    }

    @GetMapping("/v/{token}")
    public String open(@PathVariable String token, HttpServletRequest request,
                       HttpServletResponse response) {
        properties.requireEnabled();
        // 쿠키를 함께 넘긴다. 새로고침이면 토큰은 이미 죽었지만 이 쿠키가 그 창을
        // 증명한다 - 안 넘기면 F5 가 곧 410 이다(설계 4절).
        ViewReadService.Opened opened = readService.open(token, cookieValue(request),
                request.getRemoteAddr(), request.getHeader("User-Agent"));

        // 쿠키 값은 세션 번호가 아니라 난수다 - 순번을 담으면 1,2,3... 을 찍어
        // 남의 문서를 열어 볼 수 있다(IDOR). DB 에는 이 값의 해시만 있다.
        Cookie cookie = new Cookie(COOKIE, opened.sessionKey());
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(properties.sessionHours() * 3600);
        // 개발 환경은 http 라 Secure 를 늘 붙이면 쿠키가 아예 안 실린다.
        // 요청이 https 로 온 경우에만 붙인다.
        cookie.setSecure(request.isSecure());
        response.addCookie(cookie);

        // 화면 얼개는 정적 파일이다. 토큰은 주소에 남지만 이미 죽은 값이다.
        return "forward:/viewer.html";
    }

    @GetMapping("/api/view/{token}/snapshot")
    @ResponseBody
    public ResponseEntity<byte[]> snapshot(@PathVariable String token,
                                           HttpServletRequest request) {
        properties.requireEnabled();
        ViewSessionRow session = fromCookie(request);
        if (session == null) {
            // 쿠키가 없으면 방금 연 창이 처음 부르는 길이다. 이미 열린 세션이고
            // 연 지 얼마 안 됐을 때만 토큰으로 받아 준다 - 링크가 새어도 8시간짜리
            // 창이 열리지는 않게 한다.
            session = readService.justOpened(token);
        }
        byte[] gzip = readService.snapshotOf(session);
        // 몸통은 변환 결과 JSON 그것뿐이다. 무슨 문서인지·누가 보는지는 머리에 싣는다 -
        // 몸통을 한 겹 감싸면 시트·글·발표 자료 세 갈래를 모두 감싸는 층이 생기고,
        // 그 층은 화면 말고 아무도 쓰지 않는다.
        ResponseEntity.BodyBuilder built = ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_ENCODING, "gzip")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header(DOC_TYPE_HEADER, session.docType())
                .header(ENCRYPTED_HEADER, "Y".equals(session.encryptedYn()) ? "Y" : "N")
                .header(FILE_NAME_HEADER, urlEncoded(session.fileName()))
                .contentType(MediaType.APPLICATION_JSON);
        // 열람자를 안 알려 준 시스템도 있다. 빈 머리를 붙이면 화면이 빈 이름으로
        // 워터마크를 찍는다 - 아예 붙이지 않는다.
        if (session.viewerName() != null && !session.viewerName().isBlank()) {
            built = built.header(VIEWER_NAME_HEADER, urlEncoded(session.viewerName()));
        }
        return built.body(gzip);
    }

    /**
     * 머리에 실을 수 있게 UTF-8 URL 로 싼다.
     *
     * <p>HTTP 머리는 한글을 실을 자리가 아니다 - 그대로 넣으면 톰캣이 ISO-8859-1
     * 로 적어 화면에서 깨진다.
     *
     * <p>{@code +} 를 {@code %20} 으로 되돌린다. {@link URLEncoder} 는 폼 규칙이라
     * 빈칸을 {@code +} 로 적는데, 화면이 쓰는 {@code decodeURIComponent} 는 그것을
     * 빈칸으로 돌리지 않는다 - "홍길동+책임" 이 그대로 뜬다.
     */
    private static String urlEncoded(String value) {
        if (value == null) {
            return "";
        }
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private ViewSessionRow fromCookie(HttpServletRequest request) {
        String value = cookieValue(request);
        if (value == null) {
            return null;
        }
        try {
            return readService.ofSessionKey(value);
        } catch (ViewException e) {
            return null;
        }
    }

    /** @return 창 세션 쿠키 값. 없으면 null */
    private static String cookieValue(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }
        for (Cookie cookie : request.getCookies()) {
            if (COOKIE.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    /**
     * 사람에게는 코드 대신 까닭을 적은 화면을 준다.
     *
     * <p>메시지를 이스케이프해서 끼운다. 지금 이 길로 오는 메시지는 전부 상수라
     * 못 뚫지만, 같은 예외 종류를 <b>남이 준 파일 이름이 섞인 메시지</b>와 함께
     * 쓴다 - 조립하는 쪽에서 막아 두지 않으면 언젠가 한 줄 추가가 구멍이 된다.
     */
    @ExceptionHandler(ViewException.class)
    public ResponseEntity<String> onError(ViewException e) {
        String html = """
                <!doctype html><meta charset="utf-8">
                <title>문서를 열 수 없습니다</title>
                <div style="font-family:system-ui;padding:60px;text-align:center;color:#333">
                  <h2>문서를 열 수 없습니다</h2>
                  <p style="color:#777">%s</p>
                  <p style="color:#aaa;font-size:13px">보내 준 시스템에서 다시 열어 주세요.</p>
                </div>
                """.formatted(HtmlUtils.htmlEscape(String.valueOf(e.getMessage())));
        return ResponseEntity.status(e.code().status())
                .contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8))
                .body(html);
    }
}
