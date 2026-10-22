package weboffice.snapshot;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import weboffice.common.AuditUser;
import weboffice.doc.DocParagraphDto;
import weboffice.doc.DocParagraphService;
import weboffice.drawing.DrawingDto;
import weboffice.drawing.DrawingService;
import weboffice.excel.CellDto;
import weboffice.excel.MergeRangeDto;
import weboffice.security.UserDirectory;
import weboffice.security.Authenticator;
import weboffice.sheet.CellRange;
import weboffice.sheet.MergeService;
import weboffice.sheet.SheetLifecycleService;
import weboffice.sheet.SheetMapper;
import weboffice.sheet.SheetService;
import weboffice.sheet.SheetSizeDto;
import weboffice.sheet.SheetSizeService;
import weboffice.sheet.SheetSummaryDto;
import weboffice.support.DbTestSupport;
import weboffice.workbook.DocType;
import weboffice.workbook.WorkbookMapper;
import weboffice.workbook.WorkbookService;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 통째로 되돌아오는가.
 *
 * <p>이 기능의 값은 "버전 기록이 못 되돌리는 것" 에 있다 - 시트·병합·크기·그림.
 * 그래서 값보다 얼개를 더 촘촘히 본다.
 */
@SpringBootTest
@ActiveProfiles("test")
class SnapshotRestoreTest {

    @Autowired
    private SnapshotService snapshotService;

    @Autowired
    private WorkbookService workbookService;

    @Autowired
    private WorkbookMapper workbookMapper;

    @Autowired
    private SheetMapper sheetMapper;

    @Autowired
    private SheetService sheetService;

    @Autowired
    private SheetLifecycleService sheetLifecycleService;

    @Autowired
    private MergeService mergeService;

    @Autowired
    private SheetSizeService sheetSizeService;

    @Autowired
    private DrawingService drawingService;

    @Autowired
    private DocParagraphService docParagraphService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private Authenticator authenticator;

    @MockitoBean
    private UserDirectory userDirectory;

    private static final AuditUser ME = AuditUser.of("me");

    private Long workbookSeq;
    private Long sheetSeq;

    @BeforeEach
    void 문서를_하나_만든다() {
        DbTestSupport.deleteAll(jdbcTemplate);
        WorkbookService.CreatedWorkbook created =
                workbookService.createBlank("복구 시험", DocType.SHEET, ME);
        workbookSeq = created.workbookSeq();
        sheetSeq = created.sheets().get(0).sheetSeq();
    }

    @Test
    void 지운_값이_돌아온다() {
        sheetService.saveDelta(sheetSeq, List.of(text(0, 0, "가"), text(1, 0, "나")), List.of(), ME);
        Long point = snapshotService.take(workbookSeq, "MANUAL", "찍음", ME);

        sheetService.saveDelta(sheetSeq, List.of(), List.of(text(1, 0, null)), ME);
        snapshotService.restore(point, ME);

        assertThat(valuesOf(sheetSeq)).containsExactly("가", "나");
    }

    @Test
    void 찍은_뒤에_넣은_값은_사라진다() {
        // 되돌린다는 것은 "그때 없던 것" 도 없어진다는 뜻이다. 덮어쓰기만 하면 남는다.
        sheetService.saveDelta(sheetSeq, List.of(text(0, 0, "가")), List.of(), ME);
        Long point = snapshotService.take(workbookSeq, "MANUAL", null, ME);

        sheetService.saveDelta(sheetSeq, List.of(text(5, 5, "나중")), List.of(), ME);
        snapshotService.restore(point, ME);

        assertThat(valuesOf(sheetSeq)).containsExactly("가");
    }

    @Test
    void 지운_시트가_돌아온다() {
        // 버전 기록이 못 하는 일이다 - 셀 이력에는 시트가 안 남는다.
        SheetSummaryDto second = sheetLifecycleService.add(workbookSeq, "둘째", ME);
        sheetService.saveDelta(second.sheetSeq(), List.of(text(0, 0, "둘째 값")), List.of(), ME);
        Long point = snapshotService.take(workbookSeq, "MANUAL", null, ME);

        sheetLifecycleService.remove(second.sheetSeq(), ME);
        snapshotService.restore(point, ME);

        List<SheetSummaryDto> sheets = sheetMapper.selectSheetsByWorkbook(workbookSeq);
        assertThat(sheets).extracting(SheetSummaryDto::sheetName)
                .containsExactly("Sheet1", "둘째");
        assertThat(valuesOf(sheets.get(1).sheetSeq())).containsExactly("둘째 값");
    }

    @Test
    void 나중에_더한_시트는_사라진다() {
        Long point = snapshotService.take(workbookSeq, "MANUAL", null, ME);

        sheetLifecycleService.add(workbookSeq, "나중 시트", ME);
        snapshotService.restore(point, ME);

        assertThat(sheetMapper.selectSheetsByWorkbook(workbookSeq))
                .extracting(SheetSummaryDto::sheetName).containsExactly("Sheet1");
    }

    @Test
    void 시트를_지웠다_되살리기를_두_번_해도_막히지_않는다() {
        // 지운 줄도 (문서, 순번) 짝을 쥐고 있어, 안 비켜 두면 두 번째 복원이 막힌다.
        Long point = snapshotService.take(workbookSeq, "MANUAL", null, ME);

        sheetLifecycleService.add(workbookSeq, "가", ME);
        snapshotService.restore(point, ME);
        sheetLifecycleService.add(workbookSeq, "나", ME);
        snapshotService.restore(point, ME);

        assertThat(sheetMapper.selectSheetsByWorkbook(workbookSeq)).hasSize(1);
    }

    @Test
    void 시트_이름과_탭_색과_틀고정이_돌아온다() {
        sheetLifecycleService.rename(sheetSeq, "처음 이름", ME);
        sheetLifecycleService.setTabColor(sheetSeq, "#ff0000", ME);
        sheetLifecycleService.setFreeze(sheetSeq, 2, 1, ME);
        sheetLifecycleService.setFilterRange(sheetSeq, "A1:C9", ME);
        sheetLifecycleService.setGridlines(sheetSeq, false, ME);
        Long point = snapshotService.take(workbookSeq, "MANUAL", null, ME);

        sheetLifecycleService.rename(sheetSeq, "바꾼 이름", ME);
        sheetLifecycleService.setTabColor(sheetSeq, "#0000ff", ME);
        sheetLifecycleService.setFreeze(sheetSeq, 0, 0, ME);
        sheetLifecycleService.setFilterRange(sheetSeq, null, ME);
        sheetLifecycleService.setGridlines(sheetSeq, true, ME);
        snapshotService.restore(point, ME);

        SheetSummaryDto sheet = sheetMapper.selectSheet(sheetSeq);
        assertThat(sheet.sheetName()).isEqualTo("처음 이름");
        assertThat(sheet.tabColor()).isEqualTo("#ff0000");
        assertThat(sheet.freezeRowNo()).isEqualTo(2);
        assertThat(sheet.freezeColumnNo()).isEqualTo(1);
        assertThat(sheet.filterRange()).isEqualTo("A1:C9");
        assertThat(sheet.showGridlines()).isFalse();
    }

    @Test
    void 병합과_크기가_돌아온다() {
        mergeService.replaceAll(sheetSeq, List.of(new MergeRangeDto(0, 0, 1, 1)), ME);
        sheetSizeService.saveDelta(sheetSeq,
                List.of(new SheetSizeDto("ROW", 3, 44)), List.of(), ME);
        Long point = snapshotService.take(workbookSeq, "MANUAL", null, ME);

        mergeService.replaceAll(sheetSeq, List.of(), ME);
        sheetSizeService.saveDelta(sheetSeq,
                List.of(new SheetSizeDto("ROW", 3, 99), new SheetSizeDto("COLUMN", 2, 120)),
                List.of(), ME);
        snapshotService.restore(point, ME);

        assertThat(mergeService.read(sheetSeq)).containsExactly(new MergeRangeDto(0, 0, 1, 1));
        assertThat(sheetSizeService.read(sheetSeq))
                .containsExactly(new SheetSizeDto("ROW", 3, 44));
    }

    @Test
    void 지운_그림이_돌아온다() {
        Long imageSeq = 이미지를_넣는다();
        drawingService.saveDelta(sheetSeq, List.of(drawing("d1", imageSeq)), List.of(), ME);
        Long point = snapshotService.take(workbookSeq, "MANUAL", null, ME);

        drawingService.saveDelta(sheetSeq, List.of(), List.of("d1"), ME);
        snapshotService.restore(point, ME);

        assertThat(drawingService.readDrawings(sheetSeq))
                .extracting(DrawingDto::drawingId).containsExactly("d1");
    }

    @Test
    void 글_문서의_문단이_돌아온다() {
        docParagraphService.save(workbookSeq,
                List.of(new DocParagraphDto(0, "첫 줄", null, null),
                        new DocParagraphDto(1, "둘째 줄", null, null)), List.of(), ME);
        Long point = snapshotService.take(workbookSeq, "MANUAL", null, ME);

        docParagraphService.save(workbookSeq,
                List.of(new DocParagraphDto(0, "고친 줄", null, null)), List.of(1), ME);
        snapshotService.restore(point, ME);

        assertThat(docParagraphService.textAt(workbookSeq, java.time.LocalDateTime.now()))
                .containsExactly("첫 줄", "둘째 줄");
    }

    @Test
    void 문서_이름도_돌아온다() {
        Long point = snapshotService.take(workbookSeq, "MANUAL", null, ME);

        workbookService.rename(workbookSeq, "바꾼 제목", ME);
        snapshotService.restore(point, ME);

        assertThat(workbookMapper.selectWorkbook(workbookSeq).workbookName())
                .isEqualTo("복구 시험");
    }

    @Test
    void 되돌리기_직전_모습이_따로_남는다() {
        // 잘못 고른 복구 지점이 마지막 손이 되면 이 기능이 사고를 키운다.
        sheetService.saveDelta(sheetSeq, List.of(text(0, 0, "옛것")), List.of(), ME);
        Long point = snapshotService.take(workbookSeq, "MANUAL", null, ME);
        sheetService.saveDelta(sheetSeq, List.of(text(0, 0, "새것")), List.of(), ME);

        snapshotService.restore(point, ME);

        assertThat(snapshotService.list(workbookSeq))
                .extracting(SnapshotSummaryDto::reason)
                .containsExactly("BEFORE_RESTORE", "MANUAL");
        Long before = snapshotService.list(workbookSeq).get(0).snapshotSeq();
        snapshotService.restore(before, ME);
        assertThat(valuesOf(sheetSeq)).containsExactly("새것");
    }

    @Test
    void 새_문서로_복원하면_원본은_그대로다() {
        sheetService.saveDelta(sheetSeq, List.of(text(0, 0, "옛것")), List.of(), ME);
        Long point = snapshotService.take(workbookSeq, "MANUAL", null, ME);
        sheetService.saveDelta(sheetSeq, List.of(text(0, 0, "새것")), List.of(), ME);

        Long copySeq = snapshotService.copyToNewWorkbook(point, ME);

        assertThat(valuesOf(sheetSeq)).containsExactly("새것");
        Long copiedSheet = sheetMapper.selectSheetsByWorkbook(copySeq).get(0).sheetSeq();
        assertThat(valuesOf(copiedSheet)).containsExactly("옛것");
        assertThat(workbookMapper.selectWorkbook(copySeq).workbookName())
                .isEqualTo("복구 시험 (복구본)");
    }

    @Test
    void 목록은_최근_것이_앞이다() {
        Long first = snapshotService.take(workbookSeq, "MANUAL", "하나", ME);
        Long second = snapshotService.take(workbookSeq, "MANUAL", "둘", ME);

        assertThat(snapshotService.list(workbookSeq))
                .extracting(SnapshotSummaryDto::snapshotSeq)
                .containsExactly(second, first);
    }

    @Test
    void 본문은_눌러서_담는다() {
        // 340줄 시트가 1~2MB 다. 그대로 담으면 표가 금방 부푼다.
        List<CellDto> many = new java.util.ArrayList<>();
        for (int row = 0; row < 200; row += 1) {
            many.add(text(row, 0, "되풀이되는 값"));
        }
        sheetService.saveDelta(sheetSeq, many, List.of(), ME);

        snapshotService.take(workbookSeq, "MANUAL", null, ME);

        SnapshotSummaryDto head = snapshotService.list(workbookSeq).get(0);
        Long packed = jdbcTemplate.queryForObject(
                "SELECT LENGTH(BODY) FROM wo_workbook_snapshot WHERE SNAPSHOT_SEQ = ?",
                Long.class, head.snapshotSeq());
        assertThat(packed).isLessThan(head.bodySize() / 5);
    }

    private static CellDto text(int rowNo, int columnNo, String value) {
        return new CellDto(rowNo, columnNo, CellDto.ValueType.STRING, value,
                (BigDecimal) null, null, null, null, null);
    }

    private static DrawingDto drawing(String drawingId, Long imageSeq) {
        return new DrawingDto(drawingId, imageSeq, 0, 0, 0, 0, 3, 0, 3, 0, "TWO_CELL", 0, null);
    }

    /** 그림은 이미지 줄을 가리킨다. 바이트는 스냅샷에 안 담기므로 그대로 남아 있어야 한다. */
    private Long 이미지를_넣는다() {
        jdbcTemplate.update("INSERT INTO wo_sheet_image_master"
                + " (WORKBOOK_SEQ, IMAGE_HASH, MIME_TYPE, IMAGE_SIZE, IMAGE_DATA,"
                + " INPUT_DATETIME, INPUT_USER)"
                + " VALUES (?, ?, 'image/png', 3, ?, CURRENT_TIMESTAMP, 'me')",
                workbookSeq, "hash-1", new byte[] { 1, 2, 3 });
        return jdbcTemplate.queryForObject(
                "SELECT IMAGE_SEQ FROM wo_sheet_image_master WHERE IMAGE_HASH = 'hash-1'",
                Long.class);
    }

    private List<String> valuesOf(Long sheetSeq) {
        return sheetService.readCells(sheetSeq, new CellRange(0, 0, 100, 100)).stream()
                .map(CellDto::textValue)
                .toList();
    }
}
