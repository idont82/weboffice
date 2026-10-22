package weboffice.doc;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import weboffice.sheet.LogHeadRow;

@Mapper
public interface DocChangeLogMapper {

    int insertLogs(@Param("logs") List<DocChangeLogRow> logs, @Param("userKey") String userKey);

    /** 버전 목록을 묶는 데 쓰는 가벼운 조회. 값은 안 읽는다. */
    List<LogHeadRow> selectLogHeads(@Param("workbookSeq") Long workbookSeq,
                                    @Param("limit") int limit);

    /** 한 버전(시각 구간) 안에서 바뀐 문단들. 최근 것이 앞이다. */
    List<DocVersionChangeRow> selectLogsBetween(@Param("workbookSeq") Long workbookSeq,
                                                @Param("fromAt") LocalDateTime fromAt,
                                                @Param("toAt") LocalDateTime toAt,
                                                @Param("limit") int limit);

    /** 그때 뒤에 생긴 문단 변경 전부. 오래된 것이 앞이다. */
    List<DocLogValueRow> selectLogsAfter(@Param("workbookSeq") Long workbookSeq,
                                         @Param("afterAt") LocalDateTime afterAt);
}
