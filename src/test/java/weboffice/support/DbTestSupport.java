package weboffice.support;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 테스트 간 데이터 격리.
 *
 * <p>FK 역순으로 지운다. 순서가 어긋나면 참조 무결성 위반으로 실패하는데,
 * 그 실패는 테스트 대상과 무관해 원인을 찾는 데 시간이 든다.
 */
public final class DbTestSupport {

    private static final String[] TABLES_IN_DELETE_ORDER = {
            "wo_view_event_log",
            "wo_view_session",
            "wo_view_doc_cache",
            // 개인 보기(어느 시트를 몇 배로 보고 있었는가). 시트를 참조한다.
            "wo_user_sheet_view",
            // 그림 행이 이미지와 시트를 모두 참조하므로 가장 먼저 지운다.
            "wo_sheet_drawing_line",
            "wo_sheet_image_master",
            "wo_cell_change_log",
            "wo_doc_change_log",
            "wo_sheet_lock_line",
            "wo_sheet_size_line",
            "wo_sheet_merge_line",
            "wo_sheet_cell_line",
            "wo_cell_style_master",
            "wo_workbook_sheet_line",
            "wo_doc_paragraph_line",
            "wo_workbook_resource_line",
            "wo_workbook_member_map",
            // 복구 지점. 문서를 참조한다.
            "wo_workbook_snapshot",
            // 별표·색 딱지와 색 이름. 사람 것이라 FK 는 없지만 함께 비운다.
            "wo_user_workbook_mark",
            "wo_user_color_label",
            "wo_workbook_master",
            // 등록된 사용자와 초대. 초대가 사용자를 참조하므로 먼저 지운다.
            // 안 비우면 이메일 고유 제약에 걸려 다음 시험이 등록하지 못한다.
            "wo_user_invite",
            "wo_user_account",
    };

    private DbTestSupport() {
    }

    public static void deleteAll(JdbcTemplate jdbcTemplate) {
        for (String table : TABLES_IN_DELETE_ORDER) {
            jdbcTemplate.update("DELETE FROM " + table);
        }
    }
}
