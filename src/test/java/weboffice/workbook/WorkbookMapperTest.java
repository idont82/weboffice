package weboffice.workbook;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import weboffice.support.DbTestSupport;

import org.springframework.dao.DuplicateKeyException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class WorkbookMapperTest {

    @Autowired
    private WorkbookMapper workbookMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void 데이터_초기화() {
        DbTestSupport.deleteAll(jdbcTemplate);
    }

    @Test
    void 문서를_저장하면_생성된_키가_채워진다() {
        WorkbookDto dto = new WorkbookDto(null, "2026년 예산", "12345", null, null);

        workbookMapper.insertWorkbook(dto, "12345");

        assertThat(dto.workbookSeq()).isNotNull();
    }

    @Test
    void 저장한_문서를_다시_읽으면_감사_컬럼이_채워져_있다() {
        WorkbookDto dto = new WorkbookDto(null, "2026년 예산", "12345", null, null);
        workbookMapper.insertWorkbook(dto, "12345");

        WorkbookDto found = workbookMapper.selectWorkbook(dto.workbookSeq());

        assertThat(found.workbookName()).isEqualTo("2026년 예산");
        assertThat(found.docVer()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT INPUT_USER FROM wo_workbook_master WHERE WORKBOOK_SEQ = ?",
                String.class, dto.workbookSeq())).isEqualTo("12345");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT INPUT_DATETIME FROM wo_workbook_master WHERE WORKBOOK_SEQ = ?",
                java.sql.Timestamp.class, dto.workbookSeq())).isNotNull();
    }

    @Test
    void 소프트삭제된_문서는_조회되지_않는다() {
        WorkbookDto dto = new WorkbookDto(null, "삭제 대상", "12345", null, null);
        workbookMapper.insertWorkbook(dto, "12345");
        jdbcTemplate.update("""
                UPDATE wo_workbook_master
                   SET DEL_DATETIME = CURRENT_TIMESTAMP, DEL_USER = '12345'
                 WHERE WORKBOOK_SEQ = ?
                """, dto.workbookSeq());

        assertThat(workbookMapper.selectWorkbook(dto.workbookSeq())).isNull();
    }

    @Test
    void 낙관적_락은_기대_버전이_맞을_때만_올라간다() {
        WorkbookDto dto = new WorkbookDto(null, "동시 import", "12345", null, null);
        workbookMapper.insertWorkbook(dto, "12345");

        assertThat(workbookMapper.bumpDocVer(dto.workbookSeq(), 1, "12345")).isEqualTo(1);
        assertThat(workbookMapper.selectWorkbook(dto.workbookSeq()).docVer()).isEqualTo(2);
    }

    @Test
    void 낙관적_락은_기대_버전이_어긋나면_아무것도_바꾸지_않는다() {
        WorkbookDto dto = new WorkbookDto(null, "동시 import", "12345", null, null);
        workbookMapper.insertWorkbook(dto, "12345");

        assertThat(workbookMapper.bumpDocVer(dto.workbookSeq(), 99, "12345"))
                .as("갱신 행 수 0 이 충돌 신호다. 예외가 아니라 반환값으로 판정한다")
                .isZero();
        assertThat(workbookMapper.selectWorkbook(dto.workbookSeq()).docVer()).isEqualTo(1);
    }

    @Test
    void 공개_토큰을_넣고_찾는다() {
        WorkbookDto workbook = new WorkbookDto(null, "공개 문서", "12345", null, null);
        workbookMapper.insertWorkbook(workbook, "12345");

        workbookMapper.updateShareToken(workbook.workbookSeq(), "a".repeat(32), "12345");

        WorkbookDto found = workbookMapper.selectWorkbookByShareToken("a".repeat(32));
        assertThat(found).isNotNull();
        assertThat(found.workbookSeq()).isEqualTo(workbook.workbookSeq());
        assertThat(found.shareToken()).isEqualTo("a".repeat(32));
    }

    @Test
    void 토큰을_지우면_찾히지_않는다() {
        WorkbookDto workbook = new WorkbookDto(null, "공개 문서", "12345", null, null);
        workbookMapper.insertWorkbook(workbook, "12345");
        workbookMapper.updateShareToken(workbook.workbookSeq(), "b".repeat(32), "12345");

        workbookMapper.updateShareToken(workbook.workbookSeq(), null, "12345");

        assertThat(workbookMapper.selectWorkbookByShareToken("b".repeat(32))).isNull();
        assertThat(workbookMapper.selectWorkbook(workbook.workbookSeq()).shareToken()).isNull();
    }

    @Test
    void 같은_토큰을_두_문서에_쓸_수_없다() {
        // 충돌은 사실상 없지만, 충돌하면 남의 문서가 열린다.
        WorkbookDto first = new WorkbookDto(null, "문서1", "12345", null, null);
        WorkbookDto second = new WorkbookDto(null, "문서2", "12345", null, null);
        workbookMapper.insertWorkbook(first, "12345");
        workbookMapper.insertWorkbook(second, "12345");
        workbookMapper.updateShareToken(first.workbookSeq(), "c".repeat(32), "12345");

        assertThatThrownBy(() ->
                workbookMapper.updateShareToken(second.workbookSeq(), "c".repeat(32), "12345"))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void 토큰이_없는_문서가_여럿이어도_된다() {
        // 유니크 인덱스가 NULL 을 걸러내지 못하면 두 번째 문서를 못 만든다.
        workbookMapper.insertWorkbook(new WorkbookDto(null, "문서1", "12345", null, null), "12345");

        assertThatCode(() -> workbookMapper.insertWorkbook(
                new WorkbookDto(null, "문서2", "12345", null, null), "12345"))
                .doesNotThrowAnyException();
    }
}
