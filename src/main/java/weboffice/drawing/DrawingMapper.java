package weboffice.drawing;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DrawingMapper {

    List<DrawingDto> selectDrawings(@Param("sheetSeq") Long sheetSeq);

    /**
     * 벤더 분기 지점. PostgreSQL 은 {@code INSERT ... ON CONFLICT},
     * H2 는 {@code MERGE INTO} 를 쓴다. {@code CellMapper.upsertCells} 와 같다.
     */
    int upsertDrawings(@Param("sheetSeq") Long sheetSeq,
                       @Param("drawings") List<DrawingDto> drawings,
                       @Param("userKey") String userKey);

    int softDeleteDrawings(@Param("sheetSeq") Long sheetSeq,
                           @Param("drawingIds") List<String> drawingIds,
                           @Param("userKey") String userKey);
}
