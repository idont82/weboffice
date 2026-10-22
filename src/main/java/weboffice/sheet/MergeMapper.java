package weboffice.sheet;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import weboffice.excel.MergeRangeDto;

@Mapper
public interface MergeMapper {

    int insertMerges(@Param("sheetSeq") Long sheetSeq,
                     @Param("merges") List<MergeRangeDto> merges,
                     @Param("userKey") String userKey);

    List<MergeRangeDto> selectMerges(@Param("sheetSeq") Long sheetSeq);

    int deleteAllMerges(@Param("sheetSeq") Long sheetSeq, @Param("userKey") String userKey);
}
