package weboffice.sheet;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SheetMapper {

    int insertSheet(@Param("sheet") SheetSummaryDto sheet, @Param("userKey") String userKey);

    List<SheetSummaryDto> selectSheetsByWorkbook(@Param("workbookSeq") Long workbookSeq);

    SheetSummaryDto selectSheet(@Param("sheetSeq") Long sheetSeq);

    int updateSheetName(@Param("sheetSeq") Long sheetSeq,
                        @Param("sheetName") String sheetName,
                        @Param("userKey") String userKey);

    int updateGridline(@Param("sheetSeq") Long sheetSeq,
                       @Param("gridlineYn") String gridlineYn,
                       @Param("userKey") String userKey);

    int updateFilterRange(@Param("sheetSeq") Long sheetSeq,
                          @Param("filterRange") String filterRange,
                          @Param("userKey") String userKey);

    int updateTabColor(@Param("sheetSeq") Long sheetSeq,
                       @Param("tabColor") String tabColor,
                       @Param("userKey") String userKey);

    int updateFreeze(@Param("sheetSeq") Long sheetSeq,
                     @Param("freezeRowNo") int freezeRowNo,
                     @Param("freezeColumnNo") int freezeColumnNo,
                     @Param("userKey") String userKey);

    int updateSheetNo(@Param("sheetSeq") Long sheetSeq,
                      @Param("sheetNo") int sheetNo,
                      @Param("userKey") String userKey);

    int softDeleteSheet(@Param("sheetSeq") Long sheetSeq, @Param("userKey") String userKey);

    /**
     * 지운 시트까지. 복구 지점으로 되돌릴 때 쓴다.
     *
     * <p>지운 줄도 (문서, 순번) 짝을 쥐고 있어, 안 보고 새로 넣으면 순번이 겹쳐
     * 막힌다. 되살릴 수 있으면 되살리는 편이 낫다 - 시트 번호가 그대로라 열려
     * 있는 화면과 개인 보기 기록이 남의 시트를 가리키지 않는다.
     */
    List<SheetSummaryDto> selectDeletedSheetsByWorkbook(@Param("workbookSeq") Long workbookSeq);

    /** 지운 시트를 되살린다. */
    int reviveSheet(@Param("sheetSeq") Long sheetSeq, @Param("userKey") String userKey);

    int updateSheetBounds(@Param("sheetSeq") Long sheetSeq,
                          @Param("maxRowNo") int maxRowNo,
                          @Param("maxColumnNo") int maxColumnNo,
                          @Param("userKey") String userKey);
}
