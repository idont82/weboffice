package weboffice.excel;

import java.io.ByteArrayInputStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import weboffice.common.AuditUser;
import weboffice.drawing.DrawingService;
import weboffice.drawing.ImageService;
import weboffice.sheet.SheetMapper;
import weboffice.support.DbTestSupport;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class ExcelIoServiceTest {

    private static final AuditUser USER = AuditUser.of("owner1");

    @Autowired
    private ExcelIoService excelIoService;

    @Autowired
    private ExcelImportService importService;

    @Autowired
    private SheetMapper sheetMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DrawingService drawingService;

    @Autowired
    private ImageService imageService;

    @BeforeEach
    void 데이터를_비운다() {
        DbTestSupport.deleteAll(jdbcTemplate);
    }

    @Test
    void 올린_그림이_DB에_남는다() throws Exception {
        Long workbookSeq = excelIoService.importToDatabase(
                new ByteArrayInputStream(TestWorkbooks.withPicture()), "그림.xlsx", USER);

        Long sheetSeq = sheetMapper.selectSheetsByWorkbook(workbookSeq).get(0).sheetSeq();
        var drawings = drawingService.readDrawings(sheetSeq);

        assertThat(drawings).hasSize(1);
        assertThat(drawings.get(0).fromRowNo()).isEqualTo(1);
        assertThat(drawings.get(0).fromColumnNo()).isEqualTo(1);
        assertThat(imageService.load(drawings.get(0).imageSeq()).imageData())
                .isEqualTo(TestWorkbooks.onePixelPng());
    }

    @Test
    void DB의_그림이_내려받기에_담긴다() throws Exception {
        Long workbookSeq = excelIoService.importToDatabase(
                new ByteArrayInputStream(TestWorkbooks.withPicture()), "그림.xlsx", USER);

        byte[] exported = excelIoService.exportFromDatabase(workbookSeq);

        SheetDto again = importService.importFirstSheet(new ByteArrayInputStream(exported));
        assertThat(again.pictures()).hasSize(1);
        assertThat(again.pictures().get(0).bytes()).isEqualTo(TestWorkbooks.onePixelPng());
        assertThat(again.pictures().get(0).fromRowNo()).isEqualTo(1);
    }

    @Test
    void 같은_그림이_두_장_들어와도_이미지_행은_하나다() throws Exception {
        // 해시 중복 제거가 import 경로에서도 도는지 본다. 안 돌면 그림 많은
        // 문서를 올릴 때마다 DB 가 통째로 부푼다.
        Long workbookSeq = excelIoService.importToDatabase(
                new ByteArrayInputStream(TestWorkbooks.withTwoSamePictures()), "그림둘.xlsx", USER);

        Integer imageRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM wo_sheet_image_master WHERE WORKBOOK_SEQ = ?",
                Integer.class, workbookSeq);
        assertThat(imageRows).isEqualTo(1);

        Long sheetSeq = sheetMapper.selectSheetsByWorkbook(workbookSeq).get(0).sheetSeq();
        assertThat(drawingService.readDrawings(sheetSeq))
                .as("그림 배치는 두 장 그대로여야 한다")
                .hasSize(2);
    }

    @Test
    void 그림이_없는_문서도_그대로_돈다() throws Exception {
        Long workbookSeq = excelIoService.importToDatabase(
                new ByteArrayInputStream(TestWorkbooks.mixedSheet()), "그림없음.xlsx", USER);

        SheetDto again = importService.importFirstSheet(
                new ByteArrayInputStream(excelIoService.exportFromDatabase(workbookSeq)));

        assertThat(again.pictures()).isEmpty();
    }

    @Test
    void xlsx를_DB에_넣고_다시_뽑아도_값이_보존된다() throws Exception {
        byte[] original = TestWorkbooks.mixedSheet();

        Long workbookSeq = excelIoService.importToDatabase(
                new ByteArrayInputStream(original), "왕복.xlsx", USER);
        byte[] exported = excelIoService.exportFromDatabase(workbookSeq);

        SheetDto before = importService.importFirstSheet(new ByteArrayInputStream(original));
        SheetDto after = importService.importFirstSheet(new ByteArrayInputStream(exported));

        assertThat(before.cells()).hasSize(9);
        assertThat(after.cells())
                .as("DB 를 거쳐도 셀 수와 값이 그대로여야 한다")
                .usingRecursiveFieldByFieldElementComparator()
                .containsExactlyInAnyOrderElementsOf(before.cells());
    }

    @Test
    void 다중_시트가_왕복에서_보존된다() throws Exception {
        Long workbookSeq = excelIoService.importToDatabase(
                new ByteArrayInputStream(TestWorkbooks.threeSheets()), "3시트.xlsx", USER);

        byte[] exported = excelIoService.exportFromDatabase(workbookSeq);

        WorkbookDataDto after = importService.importWorkbook(
                new ByteArrayInputStream(exported), "확인");
        assertThat(after.sheets()).extracting(SheetDto::sheetName)
                .as("첫 시트만 저장하면 나머지가 소실된다")
                .containsExactly("1분기", "2분기", "3분기");
        assertThat(after.sheets().get(2).cells()).isNotEmpty();
    }

    @Test
    void 병합_범위가_왕복에서_보존된다() throws Exception {
        Long workbookSeq = excelIoService.importToDatabase(
                new ByteArrayInputStream(TestWorkbooks.mixedSheet()), "병합.xlsx", USER);

        byte[] exported = excelIoService.exportFromDatabase(workbookSeq);

        SheetDto after = importService.importFirstSheet(new ByteArrayInputStream(exported));
        assertThat(after.mergeRanges()).hasSize(1);
    }

    @Test
    void 시트_경계값이_저장된다() throws Exception {
        Long workbookSeq = excelIoService.importToDatabase(
                new ByteArrayInputStream(TestWorkbooks.mixedSheet()), "경계.xlsx", USER);

        var sheet = sheetMapper.selectSheetsByWorkbook(workbookSeq).get(0);
        assertThat(sheet.maxRowNo())
                .as("경계값이 0 이면 export 가 첫 행만 뽑아 나머지가 소실된다")
                .isEqualTo(1);
        assertThat(sheet.maxColumnNo()).isEqualTo(7);
    }

    @Test
    void 가져오기는_변경이력을_남기지_않는다() throws Exception {
        // 새로 만든 문서에는 "고치기 전" 이 없다. 파일 자체가 첫 모습이라 이력이
        // 뜻을 갖지 않는다. 그런데도 셀 수만큼 이력을 쓰느라 큰 파일 올리기가
        // 30초를 넘겼다(2026-08-18 지적: 이력 표가 셀 표보다 크다).
        Long workbookSeq = excelIoService.importToDatabase(
                new ByteArrayInputStream(TestWorkbooks.mixedSheet()), "셈.xlsx", USER);

        Integer logs = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM wo_cell_change_log", Integer.class);
        assertThat(logs).isZero();
        assertThat(sheetMapper.selectSheetsByWorkbook(workbookSeq)).isNotEmpty();
    }
}
