package weboffice.sheet;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import weboffice.common.AuditUser;
import weboffice.support.DbTestSupport;
import weboffice.workbook.WorkbookDto;
import weboffice.workbook.WorkbookMapper;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class StyleServiceTest {

    private static final AuditUser USER = AuditUser.of("12345");

    /** 화면이 보내는 모양 그대로다. 숫자 서식은 n.pattern 에 들어간다. */
    private static final String NUMBER_STYLE = "{\"n\":{\"pattern\":\"#,##0.00\"}}";
    private static final String DATE_STYLE = "{\"n\":{\"pattern\":\"yyyy-mm-dd\"}}";
    /** 엑셀 사용자 정의 서식에는 큰따옴표가 흔하다. JSON 이 깨지면 안 된다. */
    private static final String WON_STYLE = "{\"n\":{\"pattern\":\"#,##0\\\"원\\\"\"}}";

    @Autowired
    private StyleService styleService;

    @Autowired
    private WorkbookMapper workbookMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long workbookSeq;

    @BeforeEach
    void 문서_하나를_만든다() {
        DbTestSupport.deleteAll(jdbcTemplate);
        WorkbookDto workbook = new WorkbookDto(null, "서식 문서", "12345", null, null);
        workbookMapper.insertWorkbook(workbook, "12345");
        workbookSeq = workbook.workbookSeq();
    }

    @Test
    void 서식이_없으면_null_을_돌려준다() {
        assertThat(styleService.resolveStyle(workbookSeq, null, USER)).isNull();
        assertThat(styleService.resolveStyle(workbookSeq, "", USER)).isNull();
    }

    @Test
    void 같은_서식은_같은_행을_재사용한다() {
        Long first = styleService.resolveStyle(workbookSeq, NUMBER_STYLE, USER);
        Long second = styleService.resolveStyle(workbookSeq, NUMBER_STYLE, USER);

        assertThat(first).isNotNull().isEqualTo(second);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM wo_cell_style_master WHERE WORKBOOK_SEQ = ?",
                Integer.class, workbookSeq))
                .as("해시 중복 제거가 동작하면 행이 하나만 생긴다")
                .isEqualTo(1);
    }

    @Test
    void 다른_서식은_다른_행을_만든다() {
        Long a = styleService.resolveStyle(workbookSeq, NUMBER_STYLE, USER);
        Long b = styleService.resolveStyle(workbookSeq, DATE_STYLE, USER);

        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void 저장된_서식_값은_JSON_이다() {
        styleService.resolveStyle(workbookSeq, NUMBER_STYLE, USER);

        String styleValue = jdbcTemplate.queryForObject(
                "SELECT STYLE_VALUE FROM wo_cell_style_master WHERE WORKBOOK_SEQ = ?",
                String.class, workbookSeq);

        // 바이트가 아니라 뜻을 본다. PostgreSQL jsonb 는 저장할 때 재직렬화해
        // 콜론 뒤에 공백을 넣는다({"numberFormat": "#,##0.00"}). 문자열을 단정하면
        // H2 에서만 통과하고 실 DB 에서 깨진다(2026-08-05 실측).
        assertThat(numberFormatIn(styleValue))
                .as("PostgreSQL 은 jsonb, Oracle 은 CHECK (IS JSON) 이라 JSON 이 아니면 저장이 거부된다")
                .isEqualTo("#,##0.00");
    }

    /** 저장된 서식 JSON 에서 값을 꺼낸다. 표현이 벤더마다 달라 파서로 읽는다. */
    private String numberFormatIn(String styleValueJson) {
        return tools.jackson.databind.json.JsonMapper.builder().build()
                .readTree(styleValueJson).path("n").path("pattern").asString();
    }

    @Test
    void 서식_코드는_32자_16진_해시다() {
        styleService.resolveStyle(workbookSeq, NUMBER_STYLE, USER);

        String styleCode = jdbcTemplate.queryForObject(
                "SELECT STYLE_CODE FROM wo_cell_style_master WHERE WORKBOOK_SEQ = ?",
                String.class, workbookSeq);

        assertThat(styleCode.trim())
                .as("STYLE_CODE 가 CHAR(32) 이므로 MD5 16진 표현이 정확히 들어맞는다")
                .hasSize(32).matches("[0-9a-f]{32}");
    }

    @Test
    void 큰따옴표가_든_서식도_깨지지_않는다() {
        Long styleSeq = styleService.resolveStyle(workbookSeq, WON_STYLE, USER);

        assertThat(styleSeq).isNotNull();
        assertThat(numberFormatIn(jdbcTemplate.queryForObject(
                "SELECT STYLE_VALUE FROM wo_cell_style_master WHERE STYLE_SEQ = ?",
                String.class, styleSeq)))
                .as("엑셀 사용자 정의 서식에는 큰따옴표가 흔하다. JSON 이 깨지면 안 된다")
                .isEqualTo("#,##0\"원\"");

        // 되읽는 경로도 확인한다. 이 값이 null 이면 화면에서 서식이 통째로 사라진다.
        assertThat(styleService.findNumberFormat(styleSeq)).isEqualTo("#,##0\"원\"");
    }

    @Test
    void 다른_문서의_같은_서식은_별개_행이다() {
        WorkbookDto other = new WorkbookDto(null, "다른 문서", "12345", null, null);
        workbookMapper.insertWorkbook(other, "12345");

        Long a = styleService.resolveStyle(workbookSeq, NUMBER_STYLE, USER);
        Long b = styleService.resolveStyle(other.workbookSeq(), "#,##0.00", USER);

        assertThat(a)
                .as("서식 사전은 문서 단위다. 문서를 지울 때 남의 서식까지 지우면 안 된다")
                .isNotEqualTo(b);
    }

    @Test
    void 서식_전체를_그대로_보관한다() {
        // 숫자 서식만 남기면 테두리·정렬·글꼴이 저장 뒤에 사라진다(2026-08-06 지적).
        String style = "{\"bd\":{\"b\":{\"s\":1,\"cl\":{\"rgb\":\"#000\"}}},\"ht\":2}";

        Long styleSeq = styleService.resolveStyle(workbookSeq, style, USER);

        assertThat(styleService.findStyle(styleSeq)).isEqualTo(style);
    }

    @Test
    void 같은_서식은_한_행만_둔다() {
        String style = "{\"ht\":2,\"vt\":1}";

        Long first = styleService.resolveStyle(workbookSeq, style, USER);
        Long second = styleService.resolveStyle(workbookSeq, style, USER);

        assertThat(second).isEqualTo(first);
    }

    @Test
    void 서식_안의_숫자_서식은_따로_꺼낼_수_있다() {
        // xlsx 로 내보낼 때 POI 가 숫자 서식만 따로 받는다.
        Long styleSeq = styleService.resolveStyle(workbookSeq,
                "{\"n\":{\"pattern\":\"#,##0\"},\"ht\":2}", USER);

        assertThat(styleService.findNumberFormat(styleSeq)).isEqualTo("#,##0");
    }

    @Test
    void 빈_서식은_저장하지_않는다() {
        // 서식 없는 셀이 사전에 행을 만들면 표가 빈 값으로 찬다.
        assertThat(styleService.resolveStyle(workbookSeq, null, USER)).isNull();
        assertThat(styleService.resolveStyle(workbookSeq, "{}", USER)).isNull();
    }

    @Test
    void 여러_서식을_한_번에_읽는다() {
        // 셀마다 따로 물으면 서식 종류만큼 왕복이 쌓인다. 시트 한 장에 서식이
        // 165가지라 여는 데만 0.6초를 여기서 썼다(2026-08-20 실측).
        Long first = styleService.resolveStyle(workbookSeq, "{\"bl\":1}", USER);
        Long second = styleService.resolveStyle(workbookSeq, "{\"it\":1}", USER);

        var found = styleService.findStyles(List.of(first, second));

        assertThat(found).containsOnlyKeys(first, second);
        assertThat(found.get(first)).contains("bl");
        assertThat(found.get(second)).contains("it");
    }

    @Test
    void 빈_목록이면_빈_사전이다() {
        assertThat(styleService.findStyles(List.of())).isEmpty();
    }
}
