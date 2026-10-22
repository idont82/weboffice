package weboffice.sheet;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SheetSizeMapper {

    List<SheetSizeDto> selectSizes(@Param("sheetSeq") Long sheetSeq);

    /** 벤더 분기 지점. 되살리기를 빠뜨리면 기본으로 되돌린 줄을 다시 못 조정한다. */
    int upsertSizes(@Param("sheetSeq") Long sheetSeq,
                    @Param("sizes") List<SheetSizeDto> sizes,
                    @Param("userKey") String userKey);

    int softDeleteSizes(@Param("sheetSeq") Long sheetSeq,
                        @Param("sizes") List<SheetSizeDto> sizes,
                        @Param("userKey") String userKey);
}
