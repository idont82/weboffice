package weboffice.workbook;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 별표·색 딱지와 색 이름. 모두 사람 단위다. */
@Mapper
public interface WorkbookMarkMapper {

    /** 내가 표시해 둔 문서 전부. 목록 한 번에 한 번만 조회한다. */
    List<WorkbookMarkRow> selectMarks(@Param("userKey") String userKey);

    void upsertMark(@Param("userKey") String userKey,
                    @Param("workbookSeq") Long workbookSeq,
                    @Param("starredYn") String starredYn,
                    @Param("colorCode") String colorCode);

    /** 별표도 색도 없어지면 행을 지운다. 남기면 표시한 적 없는 문서와 뒤섞인다. */
    void deleteMark(@Param("userKey") String userKey,
                    @Param("workbookSeq") Long workbookSeq);

    List<ColorLabelRow> selectColorLabels(@Param("userKey") String userKey);

    void upsertColorLabel(@Param("userKey") String userKey,
                          @Param("colorCode") String colorCode,
                          @Param("labelName") String labelName);

    /** 이름을 지우면 기본 색 이름으로 돌아간다. */
    void deleteColorLabel(@Param("userKey") String userKey,
                          @Param("colorCode") String colorCode);
}
