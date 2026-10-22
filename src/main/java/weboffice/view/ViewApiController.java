package weboffice.view;

import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import weboffice.security.UserContext;

/**
 * 부르는 시스템이 쓰는 길.
 *
 * <p>URL 방식과 직접 올리기를 모두 연다. 부르는 쪽 사정이 서로 달라 하나로 몰면
 * 누군가는 못 쓴다(설계 3.1절).
 *
 * <p>여기서 막힌 것도 이력에 남긴다. 인증 실패와 받기 실패는 {@code issue()} 밖이라
 * 남기지 않으면 아무 데도 안 남는다 - 감사가 이 기능의 뼈대다(설계 3.3·7절).
 */
@RestController
@RequestMapping("/api/view")
public class ViewApiController {

    /** URL 방식 본문. */
    public record UrlRequest(String fileUrl, String fileName, String viewerName,
                             String systemName, Map<String, String> fetchHeaders) {
    }

    private final ViewProperties properties;
    private final ViewIssueService issueService;
    private final DocumentFetcher fetcher;
    private final ViewEventLogger eventLogger;

    public ViewApiController(ViewProperties properties, ViewIssueService issueService,
                             DocumentFetcher fetcher, ViewEventLogger eventLogger) {
        this.properties = properties;
        this.issueService = issueService;
        this.fetcher = fetcher;
        this.eventLogger = eventLogger;
    }

    @PostMapping(value = "/documents", consumes = "application/json")
    public Map<String, Object> issueByUrl(@RequestBody UrlRequest request,
                                          HttpServletRequest http) {
        String systemName = named(http, request.systemName(), request.fileName(),
                request.viewerName());
        gate(http, request.fileName(), request.viewerName());
        byte[] bytes = fetched(request, systemName, http);
        return answer(issueService.issue(bytes, request.fileName(), request.viewerName(),
                systemName, http.getRemoteAddr()));
    }

    @PostMapping(value = "/documents", consumes = "multipart/form-data")
    public Map<String, Object> issueByUpload(@RequestParam("file") MultipartFile file,
                                             @RequestParam("fileName") String fileName,
                                             @RequestParam("viewerName") String viewerName,
                                             @RequestParam(value = "systemName", required = false)
                                             String systemName,
                                             HttpServletRequest http) throws java.io.IOException {
        String 부른곳 = named(http, systemName, fileName, viewerName);
        gate(http, fileName, viewerName);
        return answer(issueService.issue(file.getBytes(), fileName, viewerName,
                부른곳, http.getRemoteAddr()));
    }

    /**
     * 우리 화면에서 파일을 잠깐 본다.
     *
     * <p>남의 시스템 길({@code /documents})은 주소로 통제한다. 우리 화면이 그것을
     * 그대로 부르면 <b>사용자 PC 주소</b>로 나가 대부분 막힌다 - 사내 직원 PC 대역을
     * 다 열면 그 통제가 무의미해진다. 그래서 로그인한 사람 전용 길을 따로 둔다
     * (2026-08-20 결정).
     *
     * <p>여기에는 주소 관문이 없다. 대신 <b>세션 인증</b>이 관문이다 -
     * AuthInterceptor 의 열기 목록에 이 경로를 넣지 않는 것이 그 장치다.
     *
     * <p>부른 곳은 "웹오피스", 열람자는 로그인한 사람 이름으로 적는다. 사번은
     * 적지 않는다.
     */
    @PostMapping(value = "/mine", consumes = "multipart/form-data")
    public Map<String, Object> issueForMe(@RequestParam("file") MultipartFile file,
                                          @RequestParam("fileName") String fileName,
                                          HttpServletRequest http) throws java.io.IOException {
        properties.requireEnabled();
        return answer(issueService.issue(file.getBytes(), fileName, whoAmI(),
                "웹오피스", http.getRemoteAddr()));
    }

    /** 로그인한 사람의 이름과 직급. 사번은 담지 않는다 - 화면·이력 어디에도 안 쓴다. */
    private static String whoAmI() {
        var user = UserContext.current();
        String name = user.displayName() == null ? user.userId() : user.displayName();
        String rank = user.rank() == null ? "" : user.rank().trim();
        return rank.isEmpty() ? name : name + " " + rank;
    }

    /**
     * 남의 URL 에서 받아 온다.
     *
     * <p>받기는 {@link ViewIssueService#issue} 밖이라 실패해도 그쪽 오류 이력에
     * 안 걸린다. FETCH_FAILED · TOO_LARGE 는 "어느 시스템이 어떤 URL 로 무엇을
     * 못 받았나" 가 곧 원인 규명이라 여기서 남긴다.
     */
    private byte[] fetched(UrlRequest request, String systemName, HttpServletRequest http) {
        try {
            return fetcher.fetch(request.fileUrl(), request.fetchHeaders(),
                    properties.maxFileMb());
        } catch (ViewException e) {
            logError(e, systemName, request.fileName(), request.viewerName(), http);
            throw e;
        }
    }

    /**
     * 부르는 시스템 이름. 없으면 받지 않는다.
     *
     * <p>주소로만 가리면 이력에 대역 이름까지만 좁혀진다 - 같은 대역의 어느
     * 프로그램이 불렀는지는 알 수 없다. 그쪽이 스스로 밝히게 해서 이력을 쓸모
     * 있게 만든다(2026-08-20 결정).
     *
     * <p><b>믿을 수 있는 값이 아니다.</b> 부르는 쪽이 적어 보내는 글이라 위조할 수
     * 있다. 진짜 관문은 주소다 - 이 값은 이력에만 쓰고 권한 판정에는 안 쓴다.
     */
    private String named(HttpServletRequest http, String systemName, String fileName,
                         String viewerName) {
        // 꺼져 있으면 그것이 먼저다. 꺼 둔 기능이 "이름을 안 보냈다" 고 답하면
        // 부르는 쪽이 엉뚱한 것을 고친다.
        properties.requireEnabled();

        if (systemName == null || systemName.isBlank()) {
            throw logged(new ViewException(ViewErrorCode.MISSING_SYSTEM_NAME), null,
                    fileName, viewerName, http);
        }
        return systemName.trim();
    }

    /**
     * 키와 주소로 부르는 시스템을 가린다. 못 가리면 여기서 끝난다.
     *
     * <p>키가 틀린 것(401)과 키는 맞는데 주소가 목록 밖인 것(403)을 갈라 돌려준다.
     * 부르는 쪽이 모두 우리 시스템이라 무엇을 고쳐야 하는지 알려 주는 편이 낫다고
     * 보았다(설계 8절).
     */
    private ViewCaller gate(HttpServletRequest http, String fileName, String viewerName) {
        properties.requireEnabled();

        String apiKey = http.getHeader("X-WO-API-KEY");

        // 주소가 첫 관문이다. 대역 밖에서 온 것은 이름도 못 얻는다.
        ViewCaller caller = CallerGate.byAddress(properties, http.getRemoteAddr());
        if (caller == null) {
            // 주소를 안 적고 열쇠로만 여는 시스템이 남아 있을 수 있다. 주소를 적어 둔
            // 시스템은 여기서 안 걸린다 - 걸리면 대역 밖에서도 열쇠만으로 통과한다.
            caller = CallerGate.byKeyOnly(properties, apiKey);
            if (caller == null) {
                // 막혔어도 열쇠로 이름은 알아본다. "어느 시스템이 대역 밖에서 불렀나" 가
                // 곧 방화벽·설정 문의의 답이다. 이름을 못 찾으면 null 로 남는다.
                ViewCaller named = CallerGate.byKey(properties, apiKey);
                throw logged(new ViewException(ViewErrorCode.FORBIDDEN_ADDRESS),
                        named == null ? null : named.name(), fileName, viewerName, http);
            }
        }
        if (!CallerGate.keyMatches(caller, apiKey)) {
            // 주소로는 누구인지 가려냈다 - 어느 시스템이 틀린 열쇠로 불렀는지 남긴다.
            throw logged(new ViewException(ViewErrorCode.UNAUTHORIZED), caller.name(),
                    fileName, viewerName, http);
        }
        return caller;
    }

    /** 이력을 남기고 그 예외를 돌려준다 - 부르는 쪽에서 {@code throw} 로 쓴다. */
    private ViewException logged(ViewException e, String callerSystem, String fileName,
                                 String viewerName, HttpServletRequest http) {
        logError(e, callerSystem, fileName, viewerName, http);
        return e;
    }

    private void logError(ViewException e, String callerSystem, String fileName,
                          String viewerName, HttpServletRequest http) {
        eventLogger.log(new ViewEventRow("ERROR", null, callerSystem, fileName, null, null,
                viewerName, null, e.code().name(), e.getMessage(), http.getRemoteAddr(),
                http.getHeader("User-Agent")));
    }

    private static Map<String, Object> answer(ViewIssueService.Issued issued) {
        return Map.of(
                "viewUrl", "/v/" + issued.token(),
                "expiresAt", issued.expiresAt().toString(),
                "docType", issued.docType(),
                "encrypted", issued.encrypted());
    }

}
