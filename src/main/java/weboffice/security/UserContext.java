package weboffice.security;

import java.util.HashMap;
import java.util.Map;

import jakarta.servlet.http.HttpSession;

import weboffice.common.AuditUser;

/**
 * 현재 사용자.
 *
 * <p>보관은 HttpSession, 요청 중 조회는 ThreadLocal 로 한다. 서비스 계층이
 * HttpServletRequest 를 인자로 끌고 다니지 않게 하기 위한 것이다.
 *
 * <p>ThreadLocal 은 인터셉터의 afterCompletion 에서 반드시 비운다. 톰캣이
 * 스레드를 재사용하므로 비우지 않으면 다음 요청이 이전 사용자로 처리된다.
 */
public final class UserContext {

    private static final String SESSION_KEY = "weboffice.loginUser";
    private static final String GRANT_KEY = "weboffice.shareGrants";
    private static final ThreadLocal<LoginUser> CURRENT = new ThreadLocal<>();
    private static final ThreadLocal<Map<Long, String>> GRANTS = new ThreadLocal<>();
    /** 이 요청을 보낸 창. 실시간 알림에 실어 그 창이 자기 것을 건너뛰게 한다. */
    private static final ThreadLocal<String> CLIENT = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(HttpSession session, LoginUser user) {
        session.setAttribute(SESSION_KEY, user);
    }

    public static LoginUser find(HttpSession session) {
        return session == null ? null : (LoginUser) session.getAttribute(SESSION_KEY);
    }

    public static void bindToRequest(LoginUser user) {
        CURRENT.set(user);
    }

    /** 요청 머리에 실려 온 창 표시를 담는다. 없으면 담지 않는다. */
    public static void bindClient(String clientId) {
        if (clientId != null && !clientId.isBlank()) {
            CLIENT.set(clientId);
        }
    }

    /** 이 요청을 보낸 창. 모르면 null. */
    public static String currentClient() {
        return CLIENT.get();
    }

    public static void clearRequest() {
        CLIENT.remove();
        CURRENT.remove();
        // 빠뜨리면 톰캣이 스레드를 재사용할 때 다음 요청이 이전 사용자의 허가를
        // 물려받는다 - 남의 공개 문서가 열린다.
        GRANTS.remove();
    }

    /**
     * 링크를 교환한 문서를 세션에 남긴다. 같은 세션 안에서는 다시 교환하지 않아도 된다.
     *
     * <p>문서 순번뿐 아니라 <b>어느 토큰으로 들어왔는지</b>를 함께 남긴다. 순번만
     * 남기면 판정이 "허가가 있고 토큰이 있는가" 가 되어, 재발급으로 토큰이 바뀌어도
     * 여전히 참이다 - 옛 주소는 404 인데 그 주소로 이미 들어온 사람은 안 끊긴다.
     * 재발급의 뜻이 "이 링크가 샜다" 이므로 그것은 회수가 아니다(2026-08-07 실측).
     */
    public static void grantShare(HttpSession session, Long workbookSeq, String token) {
        // 세션 속성을 제자리에서 고치면 클러스터 복제가 변경을 못 알아챈다.
        Map<Long, String> next = new HashMap<>(findGrants(session));
        next.put(workbookSeq, token);
        session.setAttribute(GRANT_KEY, next);
    }

    @SuppressWarnings("unchecked")
    public static Map<Long, String> findGrants(HttpSession session) {
        if (session == null) {
            return Map.of();
        }
        Map<Long, String> granted = (Map<Long, String>) session.getAttribute(GRANT_KEY);
        return granted == null ? Map.of() : granted;
    }

    public static void bindGrants(Map<Long, String> granted) {
        GRANTS.set(granted);
    }

    public static Map<Long, String> currentGrants() {
        Map<Long, String> granted = GRANTS.get();
        return granted == null ? Map.of() : granted;
    }

    public static LoginUser current() {
        LoginUser user = CURRENT.get();
        if (user == null) {
            throw new UnauthenticatedException("인증되지 않은 요청이다");
        }
        return user;
    }

    /**
     * 있으면 주고 없으면 null.
     *
     * <p>{@link #current()} 와 나눠 두는 이유: 알림에 이름을 실을 때처럼 "없으면
     * 없는 대로" 가 맞는 자리가 있다. 단위 테스트는 ThreadLocal 을 안 채우고
     * 서비스를 직접 부르는데, 그때 알림 때문에 터지면 안 된다.
     */
    public static LoginUser findCurrent() {
        return CURRENT.get();
    }

    /** 알림에 실을 행위자. 사번이 아니라 USER_ID 와 표시 이름이다(설계 3절). */
    public static Actor currentActor() {
        return Actor.of(CURRENT.get());
    }

    public static AuditUser currentAuditUser() {
        return AuditUser.of(current().userKey());
    }
}
