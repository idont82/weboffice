package weboffice.sheet;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 사람마다 "어디를 어떻게 보고 있었는가".
 *
 * <p>문서 내용이 아니라 개인 보기다. 남과 나누지 않는다 - 나누면 같은 문서를 여는
 * 사람끼리 시트와 배율이 서로 끌려다닌다(2026-08-13 결정).
 */
@Mapper
public interface SheetViewMapper {

    /** 이 사람이 이 문서에서 본 시트들. 최근에 본 것이 앞에 온다. */
    List<SheetViewDto> selectViews(@Param("userKey") String userKey,
                                   @Param("workbookSeq") Long workbookSeq);

    int upsertView(@Param("userKey") String userKey, @Param("workbookSeq") Long workbookSeq,
                   @Param("sheetSeq") Long sheetSeq, @Param("zoomRatio") Double zoomRatio);
}
