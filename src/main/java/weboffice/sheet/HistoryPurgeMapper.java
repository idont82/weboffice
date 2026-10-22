package weboffice.sheet;

import java.time.LocalDateTime;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 오래된 편집 이력 지우기.
 *
 * <p>한 번에 다 지우지 않고 {@code limit} 만큼씩 나눠 지운다. 백만 줄을 한 문장으로
 * 지우면 그동안 표가 잠기고 WAL 이 부푼다.
 *
 * @return 지운 줄 수. 0 이면 더 지울 것이 없다
 */
@Mapper
public interface HistoryPurgeMapper {

    int deleteOldCellLogs(@Param("before") LocalDateTime before, @Param("limit") int limit);

    int deleteOldDocLogs(@Param("before") LocalDateTime before, @Param("limit") int limit);
}
