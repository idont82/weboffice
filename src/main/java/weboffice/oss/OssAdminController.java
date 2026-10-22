package weboffice.oss;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import weboffice.view.AdminGate;

/**
 * 오픈소스 현황 화면.
 *
 * <p>관리자만 본다. 어떤 라이브러리를 어느 버전으로 쓰는지는 공격면을 그대로 알려
 * 주는 정보라 아무에게나 열어 둘 것이 아니다 - 라이선스 점검과 취약점 확인은 관리자가
 * 하는 일이다.
 *
 * <p>목록에는 사람 정보가 없다. 사번도 이름도 들어갈 자리가 아니다.
 */
@Controller
public class OssAdminController {

    private final OssCatalog catalog;
    private final AdminGate gate;

    public OssAdminController(OssCatalog catalog, AdminGate gate) {
        this.catalog = catalog;
        this.gate = gate;
    }

    @GetMapping("/admin/oss")
    public String page(HttpServletRequest request) {
        String userId = gate.requireAdmin();
        gate.logAccess(request, userId, "오픈소스 현황");
        return "forward:/oss.html";
    }

    @GetMapping("/api/admin/oss")
    @ResponseBody
    public OssResponse list(HttpServletRequest request) {
        String userId = gate.requireAdmin();
        List<OssItem> items = catalog.items();
        gate.logAccess(request, userId, "오픈소스 현황 조회");
        return new OssResponse(items, OssOverview.of(items));
    }

    /**
     * 배포에 붙일 고지문.
     *
     * <p>{@code text/plain} 으로 내려 브라우저에서 바로 읽고 복사하게 한다 - 파일로
     * 내려받게 하면 열어 보려고 또 한 번 눌러야 한다.
     */
    @GetMapping(value = "/api/admin/oss/notice", produces = "text/plain;charset=UTF-8")
    @ResponseBody
    public String notice(HttpServletRequest request) {
        String userId = gate.requireAdmin();
        String text = OssNotice.textOf(catalog.items());
        gate.logAccess(request, userId, "오픈소스 고지문");
        return text;
    }

    /** 목록과 요약. */
    public record OssResponse(List<OssItem> items, OssOverview overview) { }

}
