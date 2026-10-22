package weboffice.view;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;

/** 관리자 화면이 쓰는 조회와 셈. 화면은 숫자만 받아 그린다. */
@Service
public class ViewAdminService {

    private final ViewEventQueryMapper queryMapper;

    public ViewAdminService(ViewEventQueryMapper queryMapper) {
        this.queryMapper = queryMapper;
    }

    public List<ViewEventView> events(String eventType, int days, int limit) {
        return queryMapper.selectEvents(eventType, LocalDateTime.now().minusDays(days), limit);
    }

    public ViewStats stats(int days) {
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        Integer average = queryMapper.averageTookMs("ISSUE", since);
        return new ViewStats(
                queryMapper.countByType("ISSUE", since),
                queryMapper.countByType("OPEN", since),
                queryMapper.countByType("ERROR", since),
                average == null ? 0 : average,
                queryMapper.countCacheRows());
    }
}
