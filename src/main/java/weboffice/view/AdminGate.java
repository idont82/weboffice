package weboffice.view;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.stereotype.Component;

import weboffice.security.UserAccountMapper;
import weboffice.security.UserAccountRow;
import weboffice.security.UserContext;

/**
 * 관리자 관문.
 *
 * <p>관리자 화면이 둘 이상으로 늘었다. 관문이 화면마다 따로 있으면 한쪽만 고쳐
 * 한쪽이 열린 채로 남는다 - 판정과 이력 남기기를 한 자리에 둔다.
 *
 * <p>두 길로 판정한다. 등록된 사용자의 역할이 {@code ADMIN} 이거나, 설정의
 * 계정 목록({@code weboffice.view.admins})에 있으면 통과다. 목록이 비고 등록된
 * 관리자도 없으면 아무도 못 들어간다 - 막힌 채로 두는 것이 열린 채로 두는
 * 것보다 안전하다(설계 7절).
 *
 * <p>로그인 없는 모드에서는 둘 다 빈다. 손님은 역할을 가질 수 없고 목록에
 * 적을 계정도 없다 - 그래서 관리자 화면이 통째로 닫힌다. 감사 화면이 열람
 * 기록·문서 이름·접속 IP 를 다 보여 주므로 그것이 맞다.
 */
@Component
public class AdminGate {

    private final ViewProperties properties;
    private final ViewEventLogger eventLogger;
    private final UserAccountMapper accounts;

    public AdminGate(ViewProperties properties, ViewEventLogger eventLogger,
                     UserAccountMapper accounts) {
        this.properties = properties;
        this.eventLogger = eventLogger;
        this.accounts = accounts;
    }

    /** 관리자가 아니면 여기서 끊는다. 맞으면 계정을 준다. */
    public String requireAdmin() {
        String userId = UserContext.current().userId();
        if (!properties.isAdmin(userId) && !hasAdminRole()) {
            throw new ViewException(ViewErrorCode.NOT_ADMIN);
        }
        return userId;
    }

    /**
     * 등록된 사용자의 역할을 본다.
     *
     * <p>신원 열쇠로 찾는다. 로그인 없는 모드의 손님 열쇠는 이 표에 없으니
     * null 이 돌아오고 통과하지 못한다.
     */
    private boolean hasAdminRole() {
        UserAccountRow found = accounts.selectByKey(UserContext.current().userKey());
        return found != null && found.isAdmin() && found.canSignIn();
    }

    /** 관리자 열람 자체도 이력에 남긴다. 감사 화면을 누가 봤는지도 감사 대상이다. */
    public void logAccess(HttpServletRequest request, String userId, String what) {
        eventLogger.log(new ViewEventRow("ADMIN", null, null, null, null, null, userId,
                null, "OK", what, request.getRemoteAddr(), request.getHeader("User-Agent")));
    }
}
