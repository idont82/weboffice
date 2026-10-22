package weboffice.view;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 이력 남기기.
 *
 * <p>새 트랜잭션으로 담는다. 본 일이 실패해 되돌아가도 <b>무엇이 실패했는지는
 * 남아야</b> 하기 때문이다 - 오류 이력이 함께 사라지면 관리자 화면이 쓸모없다.
 *
 * <p>담기 전에 칸 길이에 맞춰 자른다. 남이 준 파일 이름은 길이가 정해져 있지
 * 않아서 안 자르면 <b>오류 이력을 담다가 또 터진다</b> - 그 자리는 이미 오류
 * 처리기 안이라 무엇이 잘못됐는지가 통째로 사라진다. 자르는 자리를 여기 하나로
 * 두면 부르는 쪽마다 길이를 세지 않아도 된다.
 */
@Service
public class ViewEventLogger {

    // wo_view_event_log 의 칸 길이. 스키마 세 벌이 모두 같은 값이다.
    private static final int CALLER_SYSTEM = 100;
    private static final int FILE_NAME = 400;
    private static final int VIEWER_NAME = 200;
    private static final int RESULT_CODE = 30;
    private static final int MESSAGE = 1000;
    private static final int CLIENT_IP = 50;
    private static final int USER_AGENT = 400;

    private final ViewEventMapper eventMapper;

    public ViewEventLogger(ViewEventMapper eventMapper) {
        this.eventMapper = eventMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(ViewEventRow row) {
        eventMapper.insertEvent(trimmed(row), "system");
    }

    private static ViewEventRow trimmed(ViewEventRow row) {
        return new ViewEventRow(row.eventType(), row.sessionSeq(),
                cut(row.callerSystem(), CALLER_SYSTEM),
                cut(row.fileName(), FILE_NAME),
                row.byteSize(),
                row.encryptedYn(),
                cut(row.viewerName(), VIEWER_NAME),
                row.tookMs(),
                cut(row.resultCode(), RESULT_CODE),
                cut(row.message(), MESSAGE),
                cut(row.clientIp(), CLIENT_IP),
                cut(row.userAgent(), USER_AGENT));
    }

    private static String cut(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
