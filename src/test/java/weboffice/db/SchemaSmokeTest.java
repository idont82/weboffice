package weboffice.db;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import weboffice.support.DbTestSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 스키마가 실제로 만들어졌는지, MERP 표준을 지키는지 확인한다.
 *
 * <p>감사 컬럼 검사를 테스트로 두는 이유: 표준 위반은 컴파일도 실행도 막지 않는다.
 * 테이블을 하나 더 추가할 때 감사 컬럼을 빠뜨려도 아무 일이 일어나지 않다가,
 * 나중에 "누가 지웠는지 모르겠다"로 드러난다.
 */
@SpringBootTest
@ActiveProfiles("test")
class SchemaSmokeTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * 이 클래스는 원래 INFORMATION_SCHEMA 만 봐서 정리가 필요 없었다. 이미지 표에
     * 실제로 행을 넣는 검사가 생기면서 필요해졌다 - 없으면 두 검사가 서로의 행을 본다.
     */
    @BeforeEach
    void 데이터를_비운다() {
        DbTestSupport.deleteAll(jdbcTemplate);
    }

    @Test
    void 스물세_개_테이블이_생성된다() {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES
                 WHERE UPPER(TABLE_SCHEMA) = 'PUBLIC' AND LOWER(TABLE_NAME) LIKE 'wo\\_%'
                """, Integer.class);

        // 2026-08-06 에 wo_sheet_size_line(행 높이·열 너비)이 늘었다.
        // 2026-08-11 에 wo_workbook_resource_line(필터 같은 비-셀 상태)이 늘었다.
        // 2026-08-11 에 wo_doc_paragraph_line(글 문서 문단)이 늘었다.
        // 2026-08-13 에 wo_user_sheet_view(사람마다 보던 시트·배율)가 늘었다.
        // 이 표에는 감사 컬럼 여섯이 없다 - 문서 내용이 아니라 개인 보기이고,
        // 언제 봤는지는 SEEN_DATETIME 하나로 충분하다.
        // 2026-08-20 에 wo_doc_change_log(글 문단 이력)가 늘었다. 글 문서에는
        // 이력이 없어 버전으로 되돌릴 길이 없었다.
        // 2026-08-20 에 외부 뷰어 표 셋(세션·캐시·이력)이 늘었다.
        // 2026-08-31 에 wo_user_workbook_mark(별표·색 딱지)와
        // wo_user_color_label(색에 붙인 이름)이 늘었다. 둘 다 개인 것이다.
        // 2026-08-31 에 wo_workbook_snapshot(복구 지점)이 늘었다. 문서를 통째로
        // 담아 통째로 되돌린다 - 버전 이력이 못 되돌리는 얼개까지 담는다.
        // 2026-10-22 에 wo_user_account(등록된 사용자)와 wo_user_invite(초대)가
        // 늘었다. 로그인 없는 모드에서는 둘 다 비어 있다 - 손님은 쿠키의 열쇠가
        // 곧 신원이라 적어 둘 것이 없다.
        assertThat(count).isEqualTo(23);
    }

    @Test
    void 감사_컬럼_여섯_개가_전_테이블에_있다() {
        Integer tablesMissingAudit = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM (
                    SELECT TABLE_NAME FROM INFORMATION_SCHEMA.COLUMNS
                     WHERE UPPER(TABLE_SCHEMA) = 'PUBLIC' AND LOWER(TABLE_NAME) LIKE 'wo\\_%'
                       AND COLUMN_NAME IN ('INPUT_DATETIME','INPUT_USER','MOD_DATETIME',
                                           'MOD_USER','DEL_DATETIME','DEL_USER')
                     GROUP BY TABLE_NAME HAVING COUNT(*) <> 6
                )
                """, Integer.class);

        assertThat(tablesMissingAudit)
                .as("감사 컬럼 6개가 빠진 테이블이 있으면 MERP 표준 위반이다")
                .isZero();
    }

    @Test
    void 이미지_표에_바이트를_넣고_뺀다() {
        Long workbookSeq = 문서를_만든다();

        byte[] png = {(byte) 0x89, 80, 78, 71};
        jdbcTemplate.update("""
                INSERT INTO wo_sheet_image_master
                       (WORKBOOK_SEQ, IMAGE_HASH, MIME_TYPE, IMAGE_SIZE, IMAGE_DATA)
                VALUES (?, ?, ?, ?, ?)
                """, workbookSeq, "a".repeat(64), "image/png", png.length, png);

        byte[] read = jdbcTemplate.queryForObject(
                "SELECT IMAGE_DATA FROM wo_sheet_image_master", byte[].class);

        assertThat(read).isEqualTo(png);
    }

    @Test
    void 같은_문서에_같은_해시를_두_번_넣으면_거부된다() {
        Long workbookSeq = 문서를_만든다();

        String sql = """
                INSERT INTO wo_sheet_image_master
                       (WORKBOOK_SEQ, IMAGE_HASH, MIME_TYPE, IMAGE_SIZE, IMAGE_DATA)
                VALUES (?, ?, ?, ?, ?)
                """;
        byte[] png = {1, 2, 3};
        jdbcTemplate.update(sql, workbookSeq, "b".repeat(64), "image/png", 3, png);

        // UK 가 없으면 이 단정이 통과하지 못한다 - 중복 제거가 조용히 깨진 상태다.
        assertThatThrownBy(() ->
                jdbcTemplate.update(sql, workbookSeq, "b".repeat(64), "image/png", 3, png))
                .isInstanceOf(DuplicateKeyException.class);
    }

    private Long 문서를_만든다() {
        jdbcTemplate.update(
                "INSERT INTO wo_workbook_master (WORKBOOK_NAME, OWNER_USER) VALUES (?, ?)",
                "그림 문서", "12345");
        return jdbcTemplate.queryForObject(
                "SELECT MAX(WORKBOOK_SEQ) FROM wo_workbook_master", Long.class);
    }
}
