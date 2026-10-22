package weboffice.drawing;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import weboffice.common.AuditUser;
import weboffice.sheet.SheetMapper;
import weboffice.sheet.SheetSummaryDto;
import weboffice.support.DbTestSupport;
import weboffice.workbook.WorkbookDto;
import weboffice.workbook.WorkbookMapper;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class DrawingMapperTest {

    @Autowired
    private DrawingMapper drawingMapper;

    @Autowired
    private ImageService imageService;

    @Autowired
    private SheetMapper sheetMapper;

    @Autowired
    private WorkbookMapper workbookMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long sheetSeq;
    private Long imageSeq;

    @BeforeEach
    void 문서와_시트와_이미지를_만든다() {
        DbTestSupport.deleteAll(jdbcTemplate);
        WorkbookDto workbook = new WorkbookDto(null, "그림 문서", "12345", null, null);
        workbookMapper.insertWorkbook(workbook, "12345");
        SheetSummaryDto sheet =
                new SheetSummaryDto(null, workbook.workbookSeq(), "시트1", 0, 0, 0);
        sheetMapper.insertSheet(sheet, "12345");
        sheetSeq = sheet.sheetSeq();
        imageSeq = imageService.store(workbook.workbookSeq(), TestImages.png(),
                AuditUser.of("12345"));
    }

    private DrawingDto drawing(String drawingId, int zOrder) {
        return new DrawingDto(drawingId, imageSeq,
                1, 5, 2, 10,
                6, 15, 4, 20,
                "TWO_CELL", zOrder, null);
    }

    @Test
    void 그림을_넣고_읽는다() {
        drawingMapper.upsertDrawings(sheetSeq, List.of(drawing("dw-1", 0)), "12345");

        List<DrawingDto> read = drawingMapper.selectDrawings(sheetSeq);

        assertThat(read).hasSize(1);
        DrawingDto one = read.get(0);
        assertThat(one.drawingId()).isEqualTo("dw-1");
        assertThat(one.imageSeq()).isEqualTo(imageSeq);
        assertThat(one.fromRowNo()).isEqualTo(1);
        assertThat(one.fromRowOffset()).isEqualTo(5);
        assertThat(one.fromColumnNo()).isEqualTo(2);
        assertThat(one.fromColumnOffset()).isEqualTo(10);
        assertThat(one.toRowNo()).isEqualTo(6);
        assertThat(one.toRowOffset()).isEqualTo(15);
        assertThat(one.toColumnNo()).isEqualTo(4);
        assertThat(one.toColumnOffset()).isEqualTo(20);
        assertThat(one.anchorType()).isEqualTo("TWO_CELL");
    }

    @Test
    void 같은_그림을_다시_넣으면_행이_늘지_않고_갱신된다() {
        drawingMapper.upsertDrawings(sheetSeq, List.of(drawing("dw-1", 0)), "12345");

        DrawingDto moved = new DrawingDto("dw-1", imageSeq,
                10, 0, 20, 0, 15, 0, 22, 0, "TWO_CELL", 0, null);
        drawingMapper.upsertDrawings(sheetSeq, List.of(moved), "12345");

        List<DrawingDto> read = drawingMapper.selectDrawings(sheetSeq);
        assertThat(read).hasSize(1);
        assertThat(read.get(0).fromRowNo()).isEqualTo(10);
    }

    @Test
    void 지운_그림을_다시_넣으면_되살아난다() {
        // 이 단정이 없으면 지웠던 자리에 다시 그림을 넣을 때 조회에서 사라진다.
        // 셀 업서트가 같은 함정을 갖고 있어 CellMapperTest 가 같은 단정을 건다.
        drawingMapper.upsertDrawings(sheetSeq, List.of(drawing("dw-1", 0)), "12345");
        drawingMapper.softDeleteDrawings(sheetSeq, List.of("dw-1"), "12345");
        assertThat(drawingMapper.selectDrawings(sheetSeq)).isEmpty();

        drawingMapper.upsertDrawings(sheetSeq, List.of(drawing("dw-1", 0)), "12345");

        assertThat(drawingMapper.selectDrawings(sheetSeq)).hasSize(1);
    }

    @Test
    void Z_ORDER_오름차순으로_읽힌다() {
        drawingMapper.upsertDrawings(sheetSeq,
                List.of(drawing("dw-b", 2), drawing("dw-a", 1)), "12345");

        assertThat(drawingMapper.selectDrawings(sheetSeq))
                .extracting(DrawingDto::drawingId)
                .containsExactly("dw-a", "dw-b");
    }

    @Test
    void 소프트_삭제는_행을_지우지_않는다() {
        drawingMapper.upsertDrawings(sheetSeq, List.of(drawing("dw-1", 0)), "12345");
        drawingMapper.softDeleteDrawings(sheetSeq, List.of("dw-1"), "12345");

        Integer rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM wo_sheet_drawing_line", Integer.class);
        assertThat(rows).isEqualTo(1);

        String delUser = jdbcTemplate.queryForObject(
                "SELECT DEL_USER FROM wo_sheet_drawing_line", String.class);
        assertThat(delUser).isEqualTo("12345");
    }

    @Test
    void 추가_속성이_왕복한다() {
        // 자르기와 회전이 여기 담긴다. 왕복이 깨지면 사용자가 자른 그림이
        // 새로고침 때 조용히 원래대로 돌아간다.
        DrawingDto withExtra = new DrawingDto("dw-x", imageSeq,
                0, 0, 0, 0, 3, 0, 3, 0, "TWO_CELL", 0,
                "{\"angle\":30}");
        drawingMapper.upsertDrawings(sheetSeq, List.of(withExtra), "12345");

        // PostgreSQL jsonb 는 저장할 때 재직렬화한다({"angle": 30}). 바이트가 아니라
        // 값이 살아 돌아오는지를 본다 - 받는 쪽은 어차피 JSON.parse 한다.
        assertThat(drawingMapper.selectDrawings(sheetSeq).get(0).drawingExtra())
                .containsPattern("\"angle\"\\s*:\\s*30");
    }

    @Test
    void 여러_장을_한_번에_넣는다() {
        drawingMapper.upsertDrawings(sheetSeq,
                List.of(drawing("dw-a", 0), drawing("dw-b", 1), drawing("dw-c", 2)), "12345");

        assertThat(drawingMapper.selectDrawings(sheetSeq)).hasSize(3);
    }
}
