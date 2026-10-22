package weboffice.view;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import weboffice.support.DbTestSupport;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class ViewMapperTest {

    @Autowired
    private ViewCacheMapper cacheMapper;

    @Autowired
    private ViewSessionMapper sessionMapper;

    @Autowired
    private ViewEventMapper eventMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void 비운다() {
        DbTestSupport.deleteAll(jdbcTemplate);
    }

    @Test
    void 캐시를_담고_해시로_찾는다() {
        cacheMapper.upsertCache(new ViewCacheRow("해시1", "SHEET",
                new byte[] { 1, 2, 3 }, 100L, 250, "N", null), "system");

        ViewCacheRow found = cacheMapper.selectCache("해시1",
                LocalDateTime.now().minusMinutes(60));

        assertThat(found).isNotNull();
        assertThat(found.docType()).isEqualTo("SHEET");
        assertThat(found.snapshotGzip()).containsExactly(1, 2, 3);
    }

    @Test
    void 같은_해시로_두_번_담으면_덮어쓴다() {
        // FILE_HASH 가 기본키라 두 번째 담기는 insert 가 아니라 갱신이어야 한다.
        // insert 였다면 기본키 충돌로 예외가 났을 것이다.
        cacheMapper.upsertCache(new ViewCacheRow("해시3", "SHEET",
                new byte[] { 1 }, 10L, 10, "N", null), "system");

        cacheMapper.upsertCache(new ViewCacheRow("해시3", "DOC",
                new byte[] { 2, 2 }, 20L, 20, "Y", null), "system");

        ViewCacheRow found = cacheMapper.selectCache("해시3",
                LocalDateTime.now().minusMinutes(60));
        assertThat(found.docType()).isEqualTo("DOC");
        assertThat(found.snapshotGzip()).containsExactly(2, 2);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM wo_view_doc_cache WHERE FILE_HASH = '해시3'",
                Integer.class))
                .as("덮어쓰므로 줄은 여전히 하나여야 한다")
                .isEqualTo(1);
    }

    @Test
    void 오래된_캐시는_못_찾는다() {
        // 만료는 지우기 전에도 조회에서 걸러야 한다. 배치가 늦게 돌 수 있다.
        cacheMapper.upsertCache(new ViewCacheRow("해시2", "SHEET",
                new byte[] { 9 }, 1L, 10, "N", null), "system");

        assertThat(cacheMapper.selectCache("해시2", LocalDateTime.now().plusMinutes(1))).isNull();
    }

    @Test
    void 세션을_담고_토큰_해시로_찾는다() {
        ViewSessionRow row = new ViewSessionRow(null, "토큰해시", "보고서.xlsx", "SHEET",
                "파일해시", 1234L, "전자결재", "홍길동 책임", "Y", "ISSUED",
                LocalDateTime.now().plusMinutes(5), null, null, null);

        sessionMapper.insertSession(row, "system");

        assertThat(row.sessionSeq()).isNotNull();
        ViewSessionRow found = sessionMapper.selectByTokenHash("토큰해시");
        assertThat(found.fileName()).isEqualTo("보고서.xlsx");
        assertThat(found.state()).isEqualTo("ISSUED");
        assertThat(found.encryptedYn()).isEqualTo("Y");
    }

    @Test
    void 열면_상태와_시각과_세션_열쇠_해시가_바뀐다() {
        ViewSessionRow row = new ViewSessionRow(null, "토큰해시2", "보고서.xlsx", "SHEET",
                "파일해시", 1L, "전자결재", "홍길동 책임", "N", "ISSUED",
                LocalDateTime.now().plusMinutes(5), null, null, null);
        sessionMapper.insertSession(row, "system");

        sessionMapper.markOpened(row.sessionSeq(), "세션열쇠해시");

        ViewSessionRow found = sessionMapper.selectBySeq(row.sessionSeq());
        assertThat(found.state()).isEqualTo("OPENED");
        assertThat(found.openedAt()).isNotNull();
        assertThat(found.sessionKeyHash()).isEqualTo("세션열쇠해시");

        // 순번이 아니라 열쇠 해시로 찾아야 한다 - IDOR 방지의 핵심이다.
        assertThat(sessionMapper.selectBySessionKeyHash("세션열쇠해시").sessionSeq())
                .isEqualTo(row.sessionSeq());
    }

    @Test
    void 이력을_담는다() {
        eventMapper.insertEvent(new ViewEventRow("ISSUE", null, "전자결재", "보고서.xlsx",
                1234L, "N", "홍길동 책임", 250, "OK", null, "192.0.2.92", "agent"), "system");

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM wo_view_event_log", Integer.class);
        assertThat(count).isEqualTo(1);
    }
}
