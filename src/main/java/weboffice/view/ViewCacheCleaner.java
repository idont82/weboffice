package weboffice.view;

import java.time.LocalDateTime;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 식은 캐시 지우기.
 *
 * <p>조회에서도 만료를 거르지만(설계 5절) 표에 계속 쌓이면 DB 가 커진다. 10분마다
 * 한 번 훑는다.
 */
@Component
public class ViewCacheCleaner {

    private final ViewCacheMapper cacheMapper;
    private final ViewProperties properties;

    public ViewCacheCleaner(ViewCacheMapper cacheMapper, ViewProperties properties) {
        this.cacheMapper = cacheMapper;
        this.properties = properties;
    }

    @Scheduled(fixedDelay = 600_000)
    public void sweep() {
        cacheMapper.deleteOld(LocalDateTime.now().minusMinutes(properties.cacheMinutes()));
    }
}
