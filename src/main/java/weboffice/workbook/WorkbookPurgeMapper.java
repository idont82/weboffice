package weboffice.workbook;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 휴지통 비우기.
 *
 * <p>여기서는 <b>정말</b> 지운다. 다른 매퍼가 쓰는 소프트 삭제(지움 표시)와 다르다 -
 * 비우기를 눌렀는데 자리가 그대로면 비운 것이 아니다.
 *
 * <p>문서를 <b>묶어서</b> 받는다. 하나씩 돌면 문서마다 표 열일곱 개를 두드려
 * 사백 건이면 왕복이 팔천 번이다(2026-09-03 지적). 묶으면 표마다 한 번이다.
 *
 * <p>지우는 차례는 참조의 역순이다({@code WorkbookPurge.xml}).
 */
@Mapper
public interface WorkbookPurgeMapper {

    /**
     * 비울 문서.
     *
     * @param deletedBefore 이 때보다 앞서 버린 것만. "오늘 것은 남긴다" 가 이 값으로 정해진다
     */
    List<Long> selectPurgeTargets(@Param("userKey") String userKey,
                                  @Param("deletedBefore") LocalDateTime deletedBefore);

    int deleteUserSheetViews(@Param("workbookSeqs") List<Long> workbookSeqs);

    int deleteDrawings(@Param("workbookSeqs") List<Long> workbookSeqs);

    int deleteImages(@Param("workbookSeqs") List<Long> workbookSeqs);

    int deleteCellLogs(@Param("workbookSeqs") List<Long> workbookSeqs);

    int deleteDocLogs(@Param("workbookSeqs") List<Long> workbookSeqs);

    int deleteLocks(@Param("workbookSeqs") List<Long> workbookSeqs);

    int deleteSizes(@Param("workbookSeqs") List<Long> workbookSeqs);

    int deleteMerges(@Param("workbookSeqs") List<Long> workbookSeqs);

    int deleteCells(@Param("workbookSeqs") List<Long> workbookSeqs);

    int deleteStyles(@Param("workbookSeqs") List<Long> workbookSeqs);

    int deleteSheets(@Param("workbookSeqs") List<Long> workbookSeqs);

    int deleteParagraphs(@Param("workbookSeqs") List<Long> workbookSeqs);

    int deleteResources(@Param("workbookSeqs") List<Long> workbookSeqs);

    int deleteMembers(@Param("workbookSeqs") List<Long> workbookSeqs);

    int deleteSnapshots(@Param("workbookSeqs") List<Long> workbookSeqs);

    int deleteMarks(@Param("workbookSeqs") List<Long> workbookSeqs);

    /** 마지막. 주인과 "지운 것" 임을 다시 확인한다 - 그 사이에 되살렸다면 안 지운다. */
    int deleteWorkbooks(@Param("workbookSeqs") List<Long> workbookSeqs,
                        @Param("userKey") String userKey);
}
