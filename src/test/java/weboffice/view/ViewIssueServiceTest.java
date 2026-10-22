package weboffice.view;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import weboffice.support.DbTestSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
@ActiveProfiles("test")
class ViewIssueServiceTest {

    @Autowired
    private ViewIssueService issueService;

    @MockitoSpyBean
    private ViewCacheMapper cacheMapper;

    @MockitoSpyBean
    private ViewSessionMapper sessionMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void 비운다() {
        DbTestSupport.deleteAll(jdbcTemplate);
    }

    private static byte[] 엑셀한장() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            workbook.createSheet("시트1").createRow(0).createCell(0).setCellValue("값");
            workbook.write(out);
            return out.toByteArray();
        }
    }

    @Test
    void 발급하면_토큰과_세션이_생긴다() throws Exception {
        ViewIssueService.Issued issued = issueService.issue(엑셀한장(), "보고서.xlsx",
                "홍길동 책임", "전자결재", "192.0.2.92");

        assertThat(issued.token()).hasSize(32);
        assertThat(issued.docType()).isEqualTo("SHEET");
        assertThat(issued.expiresAt()).isAfter(LocalDateTime.now());

        ViewSessionRow session = sessionMapper.selectByTokenHash(ViewToken.hash(issued.token()));
        assertThat(session.state()).isEqualTo("ISSUED");
        assertThat(session.viewerName()).isEqualTo("홍길동 책임");
        assertThat(session.callerSystem()).isEqualTo("전자결재");
    }

    @Test
    void 같은_문서를_두_번_보내면_변환은_한_번이다() throws Exception {
        byte[] bytes = 엑셀한장();

        issueService.issue(bytes, "보고서.xlsx", "가", "전자결재", "192.0.2.92");
        Integer afterFirst = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM wo_view_doc_cache", Integer.class);

        issueService.issue(bytes, "보고서.xlsx", "나", "전자결재", "192.0.2.92");
        Integer afterSecond = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM wo_view_doc_cache", Integer.class);

        // 캐시 열쇠가 파일 해시라 같은 파일은 한 줄이다.
        assertThat(afterFirst).isEqualTo(1);
        assertThat(afterSecond).isEqualTo(1);
    }

    @Test
    void 발급_이력이_남는다() throws Exception {
        issueService.issue(엑셀한장(), "보고서.xlsx", "홍길동 책임", "전자결재", "192.0.2.92");

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM wo_view_event_log WHERE EVENT_TYPE = 'ISSUE'",
                Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void 못_여는_형식은_거절하고_오류_이력을_남긴다() {
        assertThatThrownBy(() -> issueService.issue(new byte[] { 1 }, "사진.png",
                "홍길동 책임", "전자결재", "192.0.2.92"))
                .isInstanceOf(ViewException.class);

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM wo_view_event_log WHERE EVENT_TYPE = 'ERROR'",
                Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void 한도를_넘으면_거절한다() {
        byte[] big = new byte[60 * 1024 * 1024];

        assertThatThrownBy(() -> issueService.issue(big, "큰파일.xlsx",
                "홍길동 책임", "전자결재", "192.0.2.92"))
                .isInstanceOf(ViewException.class)
                .hasMessageContaining("큽니다");
    }

    @Test
    void 캐시_담기가_겹쳐도_upsert라_둘_다_성공하고_줄은_하나다() throws Exception {
        // 같은 문서를 동시에 두 요청이 열면 이렇게 된다: 둘 다 처음 찾을 땐 캐시가
        // 없다고 보고 둘 다 변환해 담으려 한다. 예전엔 뒤에 담는 쪽이 FILE_HASH
        // 기본키에 부딪혀 예외를 잡고 다시 읽어 이어받아야 했다. upsert 는 같은
        // FILE_HASH 라도 부딪히지 않고 덮어쓸 뿐이라 - 그런 예외 처리 자체가
        // 필요 없다. selectCache 가 매번 없다고 답하도록 스텁해 "둘 다 캐시를
        // 못 찾아 변환한다"는 동시 상황을 그대로 흉내 낸다.
        doReturn(null).when(cacheMapper).selectCache(any(), any());
        byte[] bytes = 엑셀한장();

        ViewIssueService.Issued first = issueService.issue(bytes, "보고서.xlsx",
                "가", "전자결재", "192.0.2.92");
        ViewIssueService.Issued second = issueService.issue(bytes, "보고서.xlsx",
                "나", "전자결재", "192.0.2.92");

        assertThat(first.docType()).isEqualTo("SHEET");
        assertThat(second.docType()).isEqualTo("SHEET");
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM wo_view_doc_cache", Integer.class);
        assertThat(count)
                .as("두 번 담아도 FILE_HASH 가 같으니 줄은 하나여야 한다")
                .isEqualTo(1);
    }

    @Test
    void 캐시가_식어도_청소가_돌기_전이면_다시_열_수_있다() throws Exception {
        // 캐시 청소(deleteOld)는 10분마다 돈다. 그 사이 캐시 줄은 표에 그대로
        // 남아 있는데 INPUT_DATETIME 만 만료 밖이라 selectCache 는 없는 것으로
        // 본다. 그 틈을 손수 흉내 낸다: 캐시를 만든 뒤 INPUT_DATETIME 을 두
        // 시간 전으로 밀어 만료시킨다(기본 캐시 유효 시간은 60분이다).
        //
        // 예전 코드는 여기서 insert 가 FILE_HASH 기본키에 부딪히고, 부딪힌 뒤
        // 다시 읽어도 여전히 만료라 raced == null 이라 원래 충돌 예외를 그대로
        // 던졌다 - 청소가 돌 때까지 그 문서를 아무도 못 열었다. PostgreSQL 에서는
        // 이 다시 읽기 자체가 25P02(트랜잭션이 죽었다)로 터져 더 나빴다. upsert
        // 는 부딪히지 않으니 이 문제가 통째로 없어진다.
        byte[] bytes = 엑셀한장();
        issueService.issue(bytes, "보고서.xlsx", "가", "전자결재", "192.0.2.92");

        jdbcTemplate.update("UPDATE wo_view_doc_cache SET INPUT_DATETIME = ?",
                LocalDateTime.now().minusHours(2));

        ViewIssueService.Issued issued = issueService.issue(bytes, "보고서.xlsx",
                "나", "전자결재", "192.0.2.92");

        assertThat(issued.docType()).isEqualTo("SHEET");
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM wo_view_doc_cache", Integer.class);
        assertThat(count)
                .as("다시 담아도 FILE_HASH 가 같으니 줄은 여전히 하나여야 한다")
                .isEqualTo(1);
        LocalDateTime inputDatetime = jdbcTemplate.queryForObject(
                "SELECT INPUT_DATETIME FROM wo_view_doc_cache", LocalDateTime.class);
        assertThat(inputDatetime)
                .as("INPUT_DATETIME 이 새 값으로 덮어써져야 한다")
                .isAfter(LocalDateTime.now().minusMinutes(1));
    }

    @Test
    void 알수_없는_예외도_이력을_남기고_뷰어_예외로_바뀐다() throws Exception {
        doThrow(new IllegalStateException("디스크가 찼다"))
                .when(sessionMapper).insertSession(any(), any());

        assertThatThrownBy(() -> issueService.issue(엑셀한장(), "보고서.xlsx",
                "홍길동 책임", "전자결재", "192.0.2.92"))
                .isInstanceOf(ViewException.class);

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM wo_view_event_log "
                        + "WHERE EVENT_TYPE = 'ERROR' AND RESULT_CODE = 'CONVERT_FAILED'",
                Integer.class);
        assertThat(count).isEqualTo(1);
    }
}
