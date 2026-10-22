package weboffice.view;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ViewEventQueryMapper {

    /** @param eventType null 이면 모두 */
    List<ViewEventView> selectEvents(@Param("eventType") String eventType,
                                     @Param("since") LocalDateTime since,
                                     @Param("limit") int limit);

    int countByType(@Param("eventType") String eventType,
                    @Param("since") LocalDateTime since);

    Integer averageTookMs(@Param("eventType") String eventType,
                          @Param("since") LocalDateTime since);

    int countCacheRows();
}
