package weboffice.view;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * 관리자 화면.
 *
 * <p>관문(판정 + 이력 남기기)은 {@link AdminGate} 가 쥐고 있다 - 관리자 화면이
 * 둘 이상으로 늘어, 화면마다 관문을 따로 두면 한쪽만 고쳐 한쪽이 열린 채로 남는다.
 */
@Controller
public class ViewAdminController {

    private final ViewAdminService adminService;
    private final AdminGate gate;

    public ViewAdminController(ViewAdminService adminService, AdminGate gate) {
        this.adminService = adminService;
        this.gate = gate;
    }

    /**
     * 관리자 첫 화면.
     *
     * <p>{@code /admin/} 만 치고 들어오는 사람이 있다. 무엇이 있는지 한 자리에서
     * 보여 준다 - 화면 주소를 외워 다니지 않게(2026-08-20 지적).
     */
    @GetMapping({ "/admin", "/admin/" })
    public String home(HttpServletRequest request) {
        String userId = gate.requireAdmin();
        gate.logAccess(request, userId, "관리자 첫 화면");
        return "forward:/admin-home.html";
    }

    @GetMapping("/admin/views")
    public String page(HttpServletRequest request) {
        String userId = gate.requireAdmin();
        gate.logAccess(request, userId, "화면");
        return "forward:/admin.html";
    }

    @GetMapping("/api/view/admin/events")
    @ResponseBody
    public List<ViewEventView> events(@RequestParam(required = false) String type,
                                      @RequestParam(defaultValue = "7") int days,
                                      @RequestParam(defaultValue = "200") int limit,
                                      HttpServletRequest request) {
        String userId = gate.requireAdmin();
        // 조회부터 하고 이력은 나중에 남긴다. 먼저 남기면 이번 열람 자체가 남긴
        // ADMIN 행이 종류를 안 가린 조회 결과에 섞여 든다.
        List<ViewEventView> found = adminService.events(type, days, limit);
        gate.logAccess(request, userId, "이력 조회");
        return found;
    }

    @GetMapping("/api/view/admin/stats")
    @ResponseBody
    public ViewStats stats(@RequestParam(defaultValue = "7") int days,
                           HttpServletRequest request) {
        String userId = gate.requireAdmin();
        ViewStats found = adminService.stats(days);
        gate.logAccess(request, userId, "통계 조회");
        return found;
    }

}
