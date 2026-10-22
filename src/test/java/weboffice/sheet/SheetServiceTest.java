package weboffice.sheet;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import weboffice.common.AuditUser;
import weboffice.excel.CellDto;
import weboffice.support.DbTestSupport;
import weboffice.workbook.WorkbookDto;
import weboffice.workbook.WorkbookMapper;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class SheetServiceTest {

    private static final AuditUser USER = AuditUser.of("12345");

    @Autowired
    private SheetService sheetService;

    @Autowired
    private SheetMapper sheetMapper;

    @Autowired
    private ChangeLogMapper changeLogMapper;

    @Autowired
    private WorkbookMapper workbookMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long sheetSeq;

    @BeforeEach
    void 문서와_시트를_만든다() {
        DbTestSupport.deleteAll(jdbcTemplate);
        WorkbookDto workbook = new WorkbookDto(null, "이력 문서", "12345", null, null);
        workbookMapper.insertWorkbook(workbook, "12345");
        SheetSummaryDto sheet =
                new SheetSummaryDto(null, workbook.workbookSeq(), "시트1", 0, 0, 0);
        sheetMapper.insertSheet(sheet, "12345");
        sheetSeq = sheet.sheetSeq();
    }

    @Test
    void 새_셀을_저장하면_INSERT_이력이_남는다() {
        sheetService.saveDelta(sheetSeq, List.of(number(0, 0, "10")), List.of(), USER);

        assertThat(logTypes()).containsExactly("INSERT");
        assertThat(jdbcTemplate.queryForObject("""
                SELECT ORG_VALUE FROM wo_cell_change_log WHERE SHEET_SEQ = ?
                """, String.class, sheetSeq))
                .as("새 셀은 변경 전 값이 없다")
                .isNull();
    }

    @Test
    void 기존_셀을_바꾸면_UPDATE_이력에_변경_전후가_모두_남는다() {
        sheetService.saveDelta(sheetSeq, List.of(number(0, 0, "10")), List.of(), USER);
        sheetService.saveDelta(sheetSeq, List.of(number(0, 0, "99")), List.of(), USER);

        assertThat(logTypes()).containsExactly("INSERT", "UPDATE");
        String orgValue = jdbcTemplate.queryForObject("""
                SELECT ORG_VALUE FROM wo_cell_change_log
                 WHERE SHEET_SEQ = ? AND CHANGE_TYPE = 'UPDATE'
                """, String.class, sheetSeq);
        String modValue = jdbcTemplate.queryForObject("""
                SELECT MOD_VALUE FROM wo_cell_change_log
                 WHERE SHEET_SEQ = ? AND CHANGE_TYPE = 'UPDATE'
                """, String.class, sheetSeq);

        // jsonb 는 콜론 뒤에 공백을 넣는다. 바이트가 아니라 값을 본다.
        assertThat(orgValue).containsPattern("\"numberValue\"\\s*:\\s*10");
        assertThat(modValue).containsPattern("\"numberValue\"\\s*:\\s*99");
    }

    @Test
    void 셀을_지우면_DELETE_이력이_남고_조회에서_사라진다() {
        sheetService.saveDelta(sheetSeq, List.of(number(0, 0, "10")), List.of(), USER);

        sheetService.saveDelta(sheetSeq, List.of(), List.of(number(0, 0, "10")), USER);

        assertThat(logTypes()).containsExactly("INSERT", "DELETE");
        assertThat(sheetService.readCells(sheetSeq, new CellRange(0, 0, 0, 0))).isEmpty();
    }

    @Test
    void 저장하면_시트_경계값이_갱신된다() {
        sheetService.saveDelta(sheetSeq,
                List.of(number(0, 0, "10"), number(120, 40, "20")), List.of(), USER);

        SheetSummaryDto sheet = sheetMapper.selectSheet(sheetSeq);
        assertThat(sheet.maxRowNo()).isEqualTo(120);
        assertThat(sheet.maxColumnNo()).isEqualTo(40);
    }

    @Test
    void 경계값은_줄어들지_않는다() {
        sheetService.saveDelta(sheetSeq, List.of(number(120, 40, "20")), List.of(), USER);

        sheetService.saveDelta(sheetSeq, List.of(number(0, 0, "10")), List.of(), USER);

        SheetSummaryDto sheet = sheetMapper.selectSheet(sheetSeq);
        assertThat(sheet.maxRowNo())
                .as("작은 범위를 저장했다고 시트가 줄어들면 안 된다")
                .isEqualTo(120);
    }

    @Test
    void 경계값_갱신도_사번을_찍는다() {
        // 인자 자리가 어긋나 시트 이름이 사번 자리에 들어가도 둘 다 String 이라
        // 컴파일러가 잡지 못한다. 이 단정만이 잡는다.
        sheetService.saveDelta(sheetSeq, List.of(number(50, 3, "1")), List.of(), USER);

        assertThat(jdbcTemplate.queryForObject("""
                SELECT MOD_USER FROM wo_workbook_sheet_line WHERE SHEET_SEQ = ?
                """, String.class, sheetSeq)).isEqualTo("12345");
    }

    @Test
    void 서식은_스타일_사전을_거쳐_저장되고_읽을_때_되살아난다() {
        // 서식 전체를 왕복시킨다. 숫자 서식만 남기던 때에는 테두리·정렬이 사라졌다.
        sheetService.saveDelta(sheetSeq, List.of(
                new CellDto(0, 0, CellDto.ValueType.NUMBER, null, new BigDecimal("1234.56"),
                        null, null, null, "{\"n\":{\"pattern\":\"#,##0.00\"}}")),
                List.of(), USER);

        List<CellDto> read = sheetService.readCells(sheetSeq, new CellRange(0, 0, 0, 0));

        assertThat(read).hasSize(1);
        assertThat(read.get(0).styleValue()).isEqualTo("{\"n\":{\"pattern\":\"#,##0.00\"}}");
    }

    @Test
    void 빈_delta_는_아무것도_하지_않는다() {
        assertThat(sheetService.saveDelta(sheetSeq, List.of(), List.of(), USER)).isZero();
        assertThat(logTypes()).isEmpty();
    }

    @Test
    void 최근_이력을_최신순으로_읽는다() {
        // selectRecentLogs 는 서비스가 쓰지 않아 이 테스트가 없으면 실행되는 곳이 없다.
        // FETCH FIRST 에 바인드 파라미터를 쓸 수 있는지도 여기서만 검증된다.
        sheetService.saveDelta(sheetSeq, List.of(number(0, 0, "1")), List.of(), USER);
        sheetService.saveDelta(sheetSeq, List.of(number(0, 1, "2")), List.of(), USER);
        sheetService.saveDelta(sheetSeq, List.of(number(0, 2, "3")), List.of(), USER);

        List<ChangeLogRow> logs = changeLogMapper.selectRecentLogs(sheetSeq, 2);

        assertThat(logs).hasSize(2);
        assertThat(logs).extracting(ChangeLogRow::columnNo).containsExactly(2, 1);
    }

    private List<String> logTypes() {
        return jdbcTemplate.queryForList("""
                SELECT CHANGE_TYPE FROM wo_cell_change_log
                 WHERE SHEET_SEQ = ? ORDER BY LOG_SEQ
                """, String.class, sheetSeq);
    }

    private CellDto number(int rowNo, int columnNo, String value) {
        return new CellDto(rowNo, columnNo, CellDto.ValueType.NUMBER, null,
                new BigDecimal(value), null, null, null, null);
    }

    @Test
    void 셀이_만_개여도_한꺼번에_저장한다() {
        // 한 문장에 다 밀어 넣으면 파라미터 상한(65,535)을 넘어 통째로 실패한다.
        // 큰 xlsx 를 올리면 여기서 터졌다(2026-08-13 지적: 올리는 중 실패 난다).
        List<CellDto> cells = new java.util.ArrayList<>();
        for (int row = 0; row < 1000; row++) {
            for (int column = 0; column < 10; column++) {
                cells.add(new CellDto(row, column, CellDto.ValueType.STRING,
                        "값", null, null, null, null, null));
            }
        }

        int saved = sheetService.saveDelta(sheetSeq, cells, List.of(), USER);

        assertThat(saved).isEqualTo(10_000);
    }
}
