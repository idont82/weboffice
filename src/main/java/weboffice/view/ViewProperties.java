package weboffice.view;

import java.util.List;
import java.util.Locale;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 외부 뷰어 설정.
 *
 * <p>수명·한도를 코드에 박지 않는 이유: 운영에서 재는 값이라 배포 없이 고칠 수
 * 있어야 한다. 관리자는 사번이 아니라 계정으로 적는다 - 설정 파일에 사번을 적으면
 * 그 자체가 개인 식별자를 흘리는 길이다(설계 7절).
 */
@ConfigurationProperties(prefix = "weboffice.view")
public class ViewProperties {

    private boolean enabled = true;
    private int tokenMinutes = 5;
    private int sessionHours = 8;
    private int cacheMinutes = 60;
    private int maxFileMb = 50;
    private List<String> admins = List.of();
    private List<ViewCaller> callers = List.of();

    public boolean enabled() {
        return enabled;
    }

    /**
     * 비상 차단기(설계 9절). 꺼져 있으면 여기서 끝난다.
     *
     * <p>운영자가 {@code enabled: false} 로 문을 닫았다고 믿는데 그대로 열려 있으면
     * 그 믿음 자체가 사고다 - 발급과 보기 두 입구가 모두 이것을 먼저 묻는다.
     *
     * <p><b>관리자 화면은 이 판정을 안 탄다.</b> 거기는 이력을 보는 자리라 문을 닫는
     * 순간이야말로 "무슨 일이 있었나" 를 봐야 할 때다. 차단기에 함께 걸면 사고를
     * 멈춘 다음 그 사고를 못 들여다본다.
     */
    public void requireEnabled() {
        if (!enabled) {
            throw new ViewException(ViewErrorCode.DISABLED);
        }
    }

    public int tokenMinutes() {
        return tokenMinutes;
    }

    public int sessionHours() {
        return sessionHours;
    }

    public int cacheMinutes() {
        return cacheMinutes;
    }

    public int maxFileMb() {
        return maxFileMb;
    }

    public List<String> admins() {
        return admins;
    }

    public List<ViewCaller> callers() {
        return callers;
    }

    /**
     * 관리자인가.
     *
     * <p>목록이 비면 <b>아무도 아니다</b>. 막힌 채로 두는 것이 열린 채로 두는 것보다
     * 안전하다.
     */
    public boolean isAdmin(String userId) {
        if (userId == null || admins.isEmpty()) {
            return false;
        }
        String asked = userId.trim().toLowerCase(Locale.ROOT);
        return admins.stream()
                .map(one -> one.trim().toLowerCase(Locale.ROOT))
                .anyMatch(asked::equals);
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setTokenMinutes(int tokenMinutes) {
        this.tokenMinutes = tokenMinutes;
    }

    public void setSessionHours(int sessionHours) {
        this.sessionHours = sessionHours;
    }

    public void setCacheMinutes(int cacheMinutes) {
        this.cacheMinutes = cacheMinutes;
    }

    public void setMaxFileMb(int maxFileMb) {
        this.maxFileMb = maxFileMb;
    }

    public void setAdmins(List<String> admins) {
        this.admins = admins == null ? List.of() : admins;
    }

    public void setCallers(List<ViewCaller> callers) {
        this.callers = callers == null ? List.of() : callers;
    }
}
