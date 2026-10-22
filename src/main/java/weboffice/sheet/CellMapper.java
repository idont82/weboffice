package weboffice.sheet;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CellMapper {

    /**
     * 벤더 분기 지점. PostgreSQL 은 {@code INSERT ... ON CONFLICT},
     * H2 는 {@code MERGE INTO} 를 쓴다.
     */
    int upsertCells(@Param("sheetSeq") Long sheetSeq,
                    @Param("cells") List<CellRow> cells,
                    @Param("userKey") String userKey);

    /**
     * 갓 만든 시트에 첫 값을 넣는다.
     *
     * <p>겹칠 자리가 없으니 충돌 검사를 하지 않는다. 업서트로 넣던 때에는 셀
     * 15만 개에 22.6초가 걸렸다(2026-08-18 실측).
     */
    int insertCells(@Param("sheetSeq") Long sheetSeq,
                    @Param("cells") List<CellRow> cells,
                    @Param("userKey") String userKey);

    /**
     * 셀 한 줄을 넣는다. 묶음(batch)으로 부르는 자리에서만 쓴다.
     *
     * <p>드라이버가 여러 줄로 묶어 보내므로(reWriteBatchedInserts) 문장을 우리가
     * 길게 만드는 것보다 빠르다 - 매퍼가 파라미터 백육십만 개짜리 SQL 을 문자열로
     * 짓느라 시간을 다 썼다(2026-08-18 실측).
     */
    int insertCell(@Param("sheetSeq") Long sheetSeq, @Param("cell") CellRow cell,
                   @Param("userKey") String userKey);

    List<CellRow> selectCellsInRange(@Param("sheetSeq") Long sheetSeq,
                                     @Param("range") CellRange range);

    int softDeleteCells(@Param("sheetSeq") Long sheetSeq,
                        @Param("cells") List<CellRow> cells,
                        @Param("userKey") String userKey);
}
