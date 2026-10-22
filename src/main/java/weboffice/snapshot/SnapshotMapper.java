package weboffice.snapshot;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 복구 지점. 본문은 무거워 따로 읽는다. */
@Mapper
public interface SnapshotMapper {

    int insertSnapshot(@Param("row") SnapshotRow row, @Param("userKey") String userKey);

    /** 목록. 본문(BODY)은 안 읽는다 - 함께 읽으면 목록 한 번에 수십 MB 를 끌어온다. */
    List<SnapshotListRow> selectSnapshots(@Param("workbookSeq") Long workbookSeq);

    /** 솎을 때 쓰는 머리말. 이유와 시각만 본다. */
    List<SnapshotHead> selectHeads(@Param("workbookSeq") Long workbookSeq);

    SnapshotRow selectSnapshot(@Param("snapshotSeq") Long snapshotSeq);

    /** 정말 지운다. 눌러 담아도 수백 KB 라, 표시만 하면 자리가 안 준다. */
    int deleteSnapshots(@Param("snapshotSeqs") List<Long> snapshotSeqs);

    /** 문서를 지울 때 함께 지운다. 문서를 참조하므로 남겨 두면 지우기가 막힌다. */
    int deleteByWorkbook(@Param("workbookSeq") Long workbookSeq);

    /**
     * 최근에 고쳐졌는데 그 뒤로 찍은 것이 없는 문서.
     *
     * <p>편집 이력(셀·문단)에서 마지막 손댄 때를 본다. 문서 표의 수정 시각은 제목을
     * 바꿀 때만 움직여 편집을 못 알아본다.
     *
     * @param idleUntil 이 시각보다 앞서 편집이 멈춘 문서만. 한창 고치는 중에는 안 찍는다
     * @param since     이 시각 뒤에 고친 문서만. 오래 전에 멈춘 문서를 매번 다시 훑지 않는다
     */
    List<Long> selectWorkbooksNeedingSnapshot(@Param("idleUntil") LocalDateTime idleUntil,
                                              @Param("since") LocalDateTime since);
}
