package weboffice.workbook;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface WorkbookMapper {

    int insertWorkbook(@Param("workbook") WorkbookDto workbook, @Param("userKey") String userKey);

    WorkbookDto selectWorkbook(@Param("workbookSeq") Long workbookSeq);

    /**
     * 내가 소유한 문서. 최근 손댄 순이다.
     *
     * <p>{@code untitled} 는 기본 제목이다. 제목도 내용도 그대로인 문서는 목록에서
     * 뺀다 - 만들어만 보고 닫은 것이라 파일함에 둘 이유가 없다.
     */
    List<WorkbookRow> selectWorkbooksByOwner(@Param("userKey") String userKey,
                                             @Param("untitled") String untitled);

    /**
     * 목록 카드에 보일 셀.
     *
     * <p>문서마다 따로 조회하면 목록 한 번에 조회가 문서 수만큼 붙는다. 한 번에
     * 모아 온다.
     */
    List<PreviewCellRow> selectPreviewCells(@Param("workbookSeqs") List<Long> workbookSeqs,
                                            @Param("rowCount") int rowCount,
                                            @Param("columnCount") int columnCount);

    /**
     * 이름이나 내용으로 문서를 찾는다.
     *
     * <p>내 문서와 공유받은 문서만 본다. {@code like} 는 대문자로 올린 {@code %말%} 이다 -
     * 벤더마다 다른 대소문자 규칙에 기대지 않는다.
     */
    List<WorkbookSearchRow> searchWorkbooks(@Param("userKey") String userKey,
                                            @Param("like") String like);

    /** 남이 나에게 공유한 문서. 내가 소유한 것은 빠진다 - 두 목록에 겹치면 두 줄이 된다. */
    List<WorkbookRow> selectWorkbooksByMember(@Param("userKey") String userKey);

    int updateWorkbookName(@Param("workbookSeq") Long workbookSeq,
                           @Param("workbookName") String workbookName,
                           @Param("userKey") String userKey);

    /** 공개 링크로 문서를 찾는다. 없으면 null. */
    WorkbookDto selectWorkbookByShareToken(@Param("shareToken") String shareToken);

    /** shareToken 에 null 을 주면 공개 해제다. 별도 구문을 두지 않는다. */
    /** 소프트 삭제. 공개 토큰도 함께 비운다 - 지운 문서가 링크로 열리면 안 된다. */
    int softDeleteWorkbook(@Param("workbookSeq") Long workbookSeq,
                           @Param("userKey") String userKey);

    /** 휴지통. 내가 소유했고 지운 지 얼마 안 된 문서만이다. */
    List<TrashRow> selectTrashByOwner(@Param("userKey") String userKey,
                                      @Param("since") java.time.LocalDateTime since);

    /**
     * 되살리기.
     *
     * <p>소유자 판정을 SQL 안에서 한다 - 지운 문서는 selectWorkbook 이 안 주므로
     * 평소 쓰는 권한 검사를 태울 수 없다.
     *
     * @return 1이면 되살렸고, 0이면 내 것이 아니거나 이미 살아 있다
     */
    int restoreWorkbook(@Param("workbookSeq") Long workbookSeq,
                        @Param("userKey") String userKey,
                        @Param("since") java.time.LocalDateTime since);

    int updateShareToken(@Param("workbookSeq") Long workbookSeq,
                         @Param("shareToken") String shareToken,
                         @Param("userKey") String userKey);

    /** @return 1이면 성공, 0이면 낙관적 락 충돌 */
    int bumpDocVer(@Param("workbookSeq") Long workbookSeq,
                   @Param("expectedDocVer") int expectedDocVer,
                   @Param("userKey") String userKey);
}
