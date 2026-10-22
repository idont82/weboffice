package weboffice.sheet;

import java.util.List;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import weboffice.support.DbTestSupport;
import weboffice.workbook.WorkbookDto;
import weboffice.workbook.WorkbookMapper;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class SheetMapperTest {

    @Autowired
    private SheetMapper sheetMapper;

    @Autowired
    private WorkbookMapper workbookMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long workbookSeq;

    @BeforeEach
    void 문서_하나를_만든다() {
        DbTestSupport.deleteAll(jdbcTemplate);
        WorkbookDto workbook = new WorkbookDto(null, "다중 시트 문서", "12345", null, null);
        workbookMapper.insertWorkbook(workbook, "12345");
        workbookSeq = workbook.workbookSeq();
    }

    @Test
    void 시트는_SHEET_NO_오름차순으로_조회된다() {
        insertSheet("3분기", 2);
        insertSheet("1분기", 0);
        insertSheet("2분기", 1);

        List<SheetSummaryDto> sheets = sheetMapper.selectSheetsByWorkbook(workbookSeq);

        assertThat(sheets).extracting(SheetSummaryDto::sheetName)
                .containsExactly("1분기", "2분기", "3분기");
    }

    @Test
    void 같은_문서에_같은_SHEET_NO_를_두_번_넣을_수_없다() {
        insertSheet("첫째", 0);

        Assertions.assertThatThrownBy(() -> insertSheet("둘째", 0))
                .as("UKC00_WORKBOOK_SHEET_LINE01 이 막아야 한다")
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void 시트_경계값을_갱신한다() {
        SheetSummaryDto sheet = insertSheet("시트1", 0);

        int updated = sheetMapper.updateSheetBounds(sheet.sheetSeq(), 999, 50, "12345");

        assertThat(updated).isEqualTo(1);
        SheetSummaryDto reloaded = sheetMapper.selectSheet(sheet.sheetSeq());
        assertThat(reloaded.maxRowNo()).isEqualTo(999);
        assertThat(reloaded.maxColumnNo()).isEqualTo(50);
    }

    @Test
    void 소프트삭제된_시트는_조회되지_않는다() {
        SheetSummaryDto sheet = insertSheet("삭제 대상", 0);
        jdbcTemplate.update("""
                UPDATE wo_workbook_sheet_line
                   SET DEL_DATETIME = CURRENT_TIMESTAMP, DEL_USER = '12345'
                 WHERE SHEET_SEQ = ?
                """, sheet.sheetSeq());

        assertThat(sheetMapper.selectSheet(sheet.sheetSeq())).isNull();
        assertThat(sheetMapper.selectSheetsByWorkbook(workbookSeq)).isEmpty();
    }

    private SheetSummaryDto insertSheet(String name, int sheetNo) {
        SheetSummaryDto sheet = new SheetSummaryDto(null, workbookSeq, name, sheetNo, 0, 0);
        sheetMapper.insertSheet(sheet, "12345");
        return sheet;
    }
}
