package weboffice.lock;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import weboffice.sheet.CellRange;

/**
 * 편집 위치(presence).
 *
 * <p>경로는 {@code /api/locks} 그대로다. 이름을 바꾸면 화면·표까지 함께 옮겨야
 * 하는데 얻는 것이 이름뿐이다. 동작은 잠금이 아니다 - 겹쳐도 막지 않는다.
 */
@RestController
@RequestMapping("/api/locks")
public class LockController {

    private final LockService lockService;

    public LockController(LockService lockService) {
        this.lockService = lockService;
    }

    /** {@code sessionKey} 는 브라우저 탭이 스스로 만든 임의 문자열이다. 색을 가르는 데만 쓴다. */
    public record AcquireRequest(Long sheetSeq, int fromRowNo, int fromColumnNo,
                                 int toRowNo, int toColumnNo, String sessionKey) {
    }

    @PostMapping
    public PresenceDto acquire(@RequestBody AcquireRequest request) {
        return lockService.acquireForCurrentUser(request.sheetSeq(),
                new CellRange(request.fromRowNo(), request.fromColumnNo(),
                        request.toRowNo(), request.toColumnNo()),
                request.sessionKey());
    }

    /** 이 시트에 지금 있는 사람들. 문서를 연 직후 첫 화면을 그릴 때 쓴다. */
    @GetMapping
    public java.util.List<PresenceDto> list(@RequestParam Long sheetSeq) {
        return lockService.listFor(sheetSeq);
    }

    @DeleteMapping("/{lockSeq}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void release(@PathVariable Long lockSeq) {
        lockService.release(lockSeq);
    }

    /**
     * 자리 비우기(POST).
     *
     * <p>DELETE 와 같은 일을 한다. {@code navigator.sendBeacon} 이 POST 만 보내기
     * 때문이다 - 페이지를 떠나는 순간의 fetch 는 중간에 죽지만 beacon 은 끝까지 간다.
     * 그것이 없으면 창을 닫은 사람의 자리가 만료까지 "작성 중" 으로 남는다.
     */
    @PostMapping("/{lockSeq}/release")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void releaseByBeacon(@PathVariable Long lockSeq) {
        lockService.release(lockSeq);
    }

    @PostMapping("/{lockSeq}/heartbeat")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void heartbeat(@PathVariable Long lockSeq) {
        lockService.heartbeat(lockSeq);
    }
}
