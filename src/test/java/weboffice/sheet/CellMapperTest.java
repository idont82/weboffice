package weboffice.sheet;

import java.math.BigDecimal;
import java.util.List;

import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import weboffice.support.DbTestSupport;
import weboffice.workbook.WorkbookDto;
import weboffice.workbook.WorkbookMapper;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class CellMapperTest {

    @Autowired
    private CellMapper cellMapper;

    @Autowired
    private SheetMapper sheetMapper;

    @Autowired
    private WorkbookMapper workbookMapper;

    @Autowired
    private SqlSessionFactory sqlSessionFactory;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long sheetSeq;

    @BeforeEach
    void 문서와_시트를_만든다() {
        DbTestSupport.deleteAll(jdbcTemplate);
        WorkbookDto workbook = new WorkbookDto(null, "셀 문서", "12345", null, null);
        workbookMapper.insertWorkbook(workbook, "12345");
        SheetSummaryDto sheet =
                new SheetSummaryDto(null, workbook.workbookSeq(), "시트1", 0, 0, 0);
        sheetMapper.insertSheet(sheet, "12345");
        sheetSeq = sheet.sheetSeq();
    }

    @Test
    void 벤더_분기가_실제로_동작한다() {
        // P0 에서 DatabaseIdProvider 빈이 없어 databaseId 붙은 구문이 전부 무시된 전례가 있다.
        // 그때 기본 구문이 조용히 선택돼 테스트가 통과했다. 이 단정이 그 상태를 잡는다.
        // 특정 벤더를 못 박지 않는다. 같은 테스트를 H2 와 실 PostgreSQL 양쪽에서
        // 돌리기 때문이다(2026-08-05). 잡으려는 것은 "null" 이다 - null 이면
        // databaseId 붙은 구문이 전부 무시되고 기본 구문만 선택되는데, 그래도
        // 테스트는 통과해 버린다. P0 에서 실제로 그 상태였다.
        assertThat(sqlSessionFactory.getConfiguration().getDatabaseId())
                .as("null 이면 databaseId 붙은 구문이 전부 무시되고 기본 구문만 선택된다")
                .isNotNull()
                .isIn("h2", "postgresql");
    }

    @Test
    void 셀을_넣고_범위로_읽는다() {
        cellMapper.upsertCells(sheetSeq, List.of(
                numberCell(0, 0, "10"),
                numberCell(0, 1, "20"),
                numberCell(5, 5, "30")), "12345");

        List<CellRow> inRange = cellMapper.selectCellsInRange(sheetSeq, new CellRange(0, 0, 1, 1));

        assertThat(inRange).hasSize(2);
        assertThat(inRange).extracting(CellRow::numberValue)
                .extracting(BigDecimal::intValue)
                .containsExactlyInAnyOrder(10, 20);
    }

    @Test
    void 같은_좌표에_두_번_넣으면_갱신된다() {
        cellMapper.upsertCells(sheetSeq, List.of(numberCell(0, 0, "10")), "12345");
        cellMapper.upsertCells(sheetSeq, List.of(numberCell(0, 0, "99")), "67890");

        List<CellRow> cells = cellMapper.selectCellsInRange(sheetSeq, new CellRange(0, 0, 0, 0));

        assertThat(cells).hasSize(1);
        assertThat(cells.get(0).numberValue().intValue()).isEqualTo(99);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT MOD_USER FROM wo_sheet_cell_line
                 WHERE SHEET_SEQ = ? AND ROW_NO = 0 AND COLUMN_NO = 0
                """, String.class, sheetSeq))
                .as("갱신 경로는 MOD_USER 를 찍어야 한다")
                .isEqualTo("67890");
    }

    @Test
    void 소프트삭제된_셀은_조회되지_않는다() {
        cellMapper.upsertCells(sheetSeq, List.of(numberCell(0, 0, "10")), "12345");

        cellMapper.softDeleteCells(sheetSeq, List.of(numberCell(0, 0, "10")), "12345");

        assertThat(cellMapper.selectCellsInRange(sheetSeq, new CellRange(0, 0, 0, 0))).isEmpty();
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM wo_sheet_cell_line WHERE SHEET_SEQ = ?
                """, Integer.class, sheetSeq))
                .as("소프트삭제이므로 행 자체는 남는다")
                .isEqualTo(1);
    }

    @Test
    void 삭제된_셀에_다시_값을_넣으면_되살아난다() {
        cellMapper.upsertCells(sheetSeq, List.of(numberCell(0, 0, "10")), "12345");
        cellMapper.softDeleteCells(sheetSeq, List.of(numberCell(0, 0, "10")), "12345");

        cellMapper.upsertCells(sheetSeq, List.of(numberCell(0, 0, "77")), "12345");

        List<CellRow> cells = cellMapper.selectCellsInRange(sheetSeq, new CellRange(0, 0, 0, 0));
        assertThat(cells)
                .as("업서트가 DEL_DATETIME 을 NULL 로 되돌리지 않으면 지웠던 셀이 영영 안 보인다")
                .hasSize(1);
        assertThat(cells.get(0).numberValue().intValue()).isEqualTo(77);
    }

    @Test
    void 수식과_캐시값을_함께_저장한다() {
        cellMapper.upsertCells(sheetSeq, List.of(
                new CellRow(0, 0, "NUMBER", null, new BigDecimal("30"),
                        null, null, "A1+A2", null)), "12345");

        CellRow cell = cellMapper.selectCellsInRange(sheetSeq, new CellRange(0, 0, 0, 0)).get(0);

        assertThat(cell.formulaValue()).isEqualTo("A1+A2");
        assertThat(cell.numberValue().intValue())
                .as("서버는 수식을 재계산하지 않는다. 캐시 결과값을 그대로 보존해야 한다")
                .isEqualTo(30);
    }

    @Test
    void 소수점_열째_자리까지_보존한다() {
        cellMapper.upsertCells(sheetSeq, List.of(
                numberCell(0, 0, "1234.1234567891")), "12345");

        CellRow cell = cellMapper.selectCellsInRange(sheetSeq, new CellRange(0, 0, 0, 0)).get(0);

        assertThat(cell.numberValue())
                .as("NUMERIC(28,10) 이므로 소수점 10자리가 남아야 한다")
                .isEqualByComparingTo(new BigDecimal("1234.1234567891"));
    }

    @Test
    void 여러_셀을_한_번에_업서트한다() {
        cellMapper.upsertCells(sheetSeq, List.of(
                numberCell(0, 0, "1"), numberCell(0, 1, "2"), numberCell(1, 0, "3")), "12345");

        cellMapper.upsertCells(sheetSeq, List.of(
                numberCell(0, 0, "10"), numberCell(2, 2, "4")), "12345");

        List<CellRow> cells = cellMapper.selectCellsInRange(sheetSeq, new CellRange(0, 0, 9, 9));
        assertThat(cells)
                .as("일괄 업서트에서 기존 셀은 갱신되고 새 셀은 추가돼야 한다")
                .hasSize(4);
        assertThat(cells.get(0).numberValue().intValue()).isEqualTo(10);
    }

    @Test
    void 범위_밖의_셀은_삭제되지_않는다() {
        cellMapper.upsertCells(sheetSeq, List.of(
                numberCell(0, 0, "1"), numberCell(5, 5, "2")), "12345");

        cellMapper.softDeleteCells(sheetSeq, List.of(numberCell(0, 0, "1")), "12345");

        assertThat(cellMapper.selectCellsInRange(sheetSeq, new CellRange(0, 0, 9, 9)))
                .as("삭제는 넘긴 좌표만 대상으로 한다")
                .hasSize(1);
    }

    private CellRow numberCell(int rowNo, int columnNo, String value) {
        return new CellRow(rowNo, columnNo, "NUMBER", null, new BigDecimal(value),
                null, null, null, null);
    }
}
