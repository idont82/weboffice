package weboffice.view;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 열람.
 *
 * <p>토큰은 <b>한 번만</b> 쓴다. 열리는 순간 상태를 OPENED 로 바꾸고, 그 뒤로는
 * 창 세션 쿠키가 자리를 대신한다 - 링크가 새어도 다시 안 열린다(설계 4절).
 */
@Service
public class ViewReadService {

    /**
     * 연 결과. 쿠키에 넣을 난수({@code sessionKey})는 DB 에 평문으로 남지
     * 않는다 - 해시만 담고, 이 반환값에만 평문으로 실어 컨트롤러에 건넨다.
     * 보기 토큰과 같은 방식이다.
     */
    public record Opened(ViewSessionRow session, String sessionKey) {
    }

    private final ViewSessionMapper sessionMapper;
    private final ViewCacheMapper cacheMapper;
    private final ViewEventLogger eventLogger;
    private final ViewProperties properties;

    public ViewReadService(ViewSessionMapper sessionMapper, ViewCacheMapper cacheMapper,
                           ViewEventLogger eventLogger, ViewProperties properties) {
        this.sessionMapper = sessionMapper;
        this.cacheMapper = cacheMapper;
        this.eventLogger = eventLogger;
        this.properties = properties;
    }

    /**
     * 토큰으로 처음 여는 길. 두 번째부터는 {@link #ofSessionKey} 가 받는다.
     *
     * @param sessionKey 브라우저가 실어 보낸 창 세션 쿠키 값. 없으면 null
     */
    @Transactional
    public Opened open(String token, String sessionKey, String clientIp, String userAgent) {
        ViewSessionRow session = sessionMapper.selectByTokenHash(ViewToken.hash(token));
        if (session == null) {
            throw new ViewException(ViewErrorCode.TOKEN_EXPIRED);
        }
        if (!"ISSUED".equals(session.state())) {
            // 새로고침이다. /v/{token} 은 forward 라 주소창에 토큰이 그대로 남고
            // F5 는 같은 요청을 다시 보낸다 - 토큰만 보면 이미 죽은 값이라 410 이
            // 되지만, 설계 4절은 "새로고침은 계속 된다" 고 약속한다. 쿠키가 바로
            // 이 세션을 가리키고 수명 안이면 화면을 그대로 다시 준다.
            return reopened(session, sessionKey);
        }
        if (session.expiresAt().isBefore(LocalDateTime.now())) {
            throw new ViewException(ViewErrorCode.TOKEN_EXPIRED);
        }
        // 쿠키에는 SESSION_SEQ 가 아니라 이 난수를 담는다. 순번을 그대로 담으면
        // 1,2,3... 을 찍어 남의 문서를 열어 볼 수 있다(IDOR) - 실측으로 드러났다.
        String made = ViewToken.make();
        sessionMapper.markOpened(session.sessionSeq(), ViewToken.hash(made));
        eventLogger.log(new ViewEventRow("OPEN", session.sessionSeq(), session.callerSystem(),
                session.fileName(), session.byteSize(), session.encryptedYn(),
                session.viewerName(), null, "OK", null, clientIp, userAgent));
        return new Opened(session, made);
    }

    /**
     * 이미 열린 링크를 그 창이 다시 여는 길(새로고침).
     *
     * <p>쿠키가 <b>그 토큰의 세션</b>을 가리켜야 한다. 아무 세션 쿠키나 통과시키면
     * 제 문서 하나를 연 사람이 새어 나온 남의 링크까지 다시 열 수 있다.
     *
     * <p>OPEN 이력은 다시 남기지 않는다 - 한 번 연 창이 몇 번 새로고침했는지는
     * 열람 건수가 아니라 마지막 본 시각(LAST_SEEN_AT)이 말한다.
     */
    private Opened reopened(ViewSessionRow session, String sessionKey) {
        if (sessionKey == null || sessionKey.isBlank()) {
            throw new ViewException(ViewErrorCode.TOKEN_USED);
        }
        ViewSessionRow byCookie;
        try {
            byCookie = ofSessionKey(sessionKey);
        } catch (ViewException e) {
            // 쿠키가 낡았거나 모르는 값이다 - 링크가 새어 남이 연 것과 같이 다룬다.
            throw new ViewException(ViewErrorCode.TOKEN_USED);
        }
        if (!byCookie.sessionSeq().equals(session.sessionSeq())) {
            throw new ViewException(ViewErrorCode.TOKEN_USED);
        }
        sessionMapper.touch(byCookie.sessionSeq());
        return new Opened(byCookie, sessionKey);
    }

    /** 화면이 열리자마자 스냅샷을 부르는 길. 연 지 2분 안쪽만 받아 준다. */
    public ViewSessionRow justOpened(String token) {
        ViewSessionRow session = sessionMapper.selectByTokenHash(ViewToken.hash(token));
        if (session == null || session.openedAt() == null) {
            throw new ViewException(ViewErrorCode.TOKEN_EXPIRED);
        }
        if (session.openedAt().plusMinutes(2).isBefore(LocalDateTime.now())) {
            throw new ViewException(ViewErrorCode.TOKEN_EXPIRED);
        }
        return session;
    }

    /**
     * 이미 연 창이 다시 부를 때. 세션 수명 안이면 통과다.
     *
     * <p>쿠키에 담긴 난수로 찾는다 - 순번으로 찾으면 남의 세션 번호를 넣어도
     * 그 세션이 열려 있고 수명 안이기만 하면 통과해 버린다(IDOR).
     */
    public ViewSessionRow ofSessionKey(String sessionKey) {
        ViewSessionRow session = sessionMapper.selectBySessionKeyHash(ViewToken.hash(sessionKey));
        if (session == null || session.openedAt() == null) {
            throw new ViewException(ViewErrorCode.TOKEN_EXPIRED);
        }
        if (session.openedAt().plusHours(properties.sessionHours())
                .isBefore(LocalDateTime.now())) {
            throw new ViewException(ViewErrorCode.TOKEN_EXPIRED);
        }
        return session;
    }

    /** 스냅샷 gzip 바이트. 캐시가 식었으면 다시 받아야 한다 - 원본을 안 들고 있다. */
    public byte[] snapshotOf(ViewSessionRow session) {
        ViewCacheRow cached = cacheMapper.selectCache(session.fileHash(),
                LocalDateTime.now().minusMinutes(properties.cacheMinutes()));
        if (cached == null) {
            throw new ViewException(ViewErrorCode.TOKEN_EXPIRED,
                    "문서가 만료되었습니다. 보내 준 시스템에서 다시 열어 주세요");
        }
        sessionMapper.touch(session.sessionSeq());
        return cached.snapshotGzip();
    }
}
