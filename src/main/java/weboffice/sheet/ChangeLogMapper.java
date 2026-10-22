package weboffice.sheet;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ChangeLogMapper {

    int insertLogs(@Param("logs") List<ChangeLogRow> logs, @Param("userKey") String userKey);

    /** 버전 목록을 묶는 데 쓰는 가벼운 조회. 값은 안 읽는다. */
    List<LogHeadRow> selectWorkbookLogHeads(@Param("workbookSeq") Long workbookSeq,
                                            @Param("limit") int limit);

    /** 그 시각 뒤에 생긴 변경 전부. 오래된 것이 앞이다 - 그것이 그때의 값이다. */
    List<LogValueRow> selectWorkbookLogsAfter(@Param("workbookSeq") Long workbookSeq,
                                              @Param("afterAt") java.time.LocalDateTime afterAt);

    /** 한 버전(시각 구간) 안에서 바뀐 칸들. 최근 것이 앞이다. */
    List<VersionChangeRow> selectWorkbookLogsBetween(
            @Param("workbookSeq") Long workbookSeq,
            @Param("fromAt") java.time.LocalDateTime fromAt,
            @Param("toAt") java.time.LocalDateTime toAt,
            @Param("limit") int limit);

    /** 칸 하나의 수정 기록. 최근 것이 앞이다. */
    List<CellHistoryLogRow> selectCellHistory(@Param("sheetSeq") Long sheetSeq,
                                              @Param("rowNo") int rowNo,
                                              @Param("columnNo") int columnNo,
                                              @Param("limit") int limit);

    List<ChangeLogRow> selectRecentLogs(@Param("sheetSeq") Long sheetSeq,
                                        @Param("limit") int limit);
}
