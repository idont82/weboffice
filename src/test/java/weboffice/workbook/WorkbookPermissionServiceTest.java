package weboffice.workbook;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import weboffice.sheet.SheetMapper;
import weboffice.sheet.SheetSummaryDto;
import weboffice.support.DbTestSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class WorkbookPermissionServiceTest {

    @Autowired
    private WorkbookPermissionService permissionService;

    @Autowired
    private MemberMapper memberMapper;

    @Autowired
    private WorkbookMapper workbookMapper;

    @Autowired
    private SheetMapper sheetMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long workbookSeq;
    private Long sheetSeq;

    @BeforeEach
    void 문서와_시트를_만든다() {
        DbTestSupport.deleteAll(jdbcTemplate);
        WorkbookDto workbook = new WorkbookDto(null, "권한 문서", "owner1", null, null);
        workbookMapper.insertWorkbook(workbook, "owner1");
        workbookSeq = workbook.workbookSeq();
        SheetSummaryDto sheet = new SheetSummaryDto(null, workbookSeq, "시트1", 0, 0, 0);
        sheetMapper.insertSheet(sheet, "owner1");
        sheetSeq = sheet.sheetSeq();
    }

    @Test
    void 소유자는_등록되지_않아도_쓸_수_있다() {
        assertThat(permissionService.requireWrite(sheetSeq, "owner1"))
                .as("import 직후 자기 문서에 못 들어가는 상태를 만들지 않는다")
                .isEqualTo(WorkbookRole.OWNER);
    }

    @Test
    void EDITOR_는_쓸_수_있다() {
        memberMapper.insertMember(workbookSeq, "editor1", "EDITOR", "owner1");

        assertThatCode(() -> permissionService.requireWrite(sheetSeq, "editor1"))
                .doesNotThrowAnyException();
    }

    @Test
    void VIEWER_는_읽을_수_있지만_쓰지_못한다() {
        memberMapper.insertMember(workbookSeq, "viewer1", "VIEWER", "owner1");

        assertThatCode(() -> permissionService.requireRead(sheetSeq, "viewer1"))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> permissionService.requireWrite(sheetSeq, "viewer1"))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void 등록되지_않은_사용자는_읽지도_못한다() {
        assertThatThrownBy(() -> permissionService.requireRead(sheetSeq, "stranger"))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void 사용_중지된_구성원은_권한이_없다() {
        memberMapper.insertMember(workbookSeq, "leaver", "EDITOR", "owner1");
        jdbcTemplate.update("""
                UPDATE wo_workbook_member_map SET USE_YN = 'N'
                 WHERE WORKBOOK_SEQ = ? AND MEMBER_USER = 'leaver'
                """, workbookSeq);

        assertThatThrownBy(() -> permissionService.requireRead(sheetSeq, "leaver"))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void 없는_시트를_물으면_거부한다() {
        assertThatThrownBy(() -> permissionService.requireRead(999999L, "owner1"))
                .as("존재 여부를 권한 오류로 감춰 문서 존재를 탐지당하지 않게 한다")
                .isInstanceOf(ForbiddenException.class);
    }
}
