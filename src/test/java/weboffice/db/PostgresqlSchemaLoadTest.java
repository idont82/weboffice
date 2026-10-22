package weboffice.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * 운영 스키마를 실제로 실행해 본다.
 *
 * <p>왜 필요한가: {@code db/schema-postgresql.sql} 은 {@code docker compose up}
 * 이 처음 뜰 때 딱 한 번 돌고, 그때까지 아무도 돌려 보지 않는다. 쉼표 하나가
 * 빠져 있어도 거기서야 드러나고, 그때는 받은 사람의 첫 경험이 "안 깔린다" 다.
 * {@link SchemaParityTest} 는 H2 스키마와 견주기만 하므로 둘이 똑같이 틀리면
 * 통과한다.
 *
 * <p><b>이것은 PostgreSQL 이 아니다.</b> H2 의 PostgreSQL 호환 모드다. 문장이
 * 문법에 맞는지, 표와 컬럼과 제약이 선언한 대로 생기는지까지는 본다. 타입의
 * 실제 동작(jsonb 연산자, bytea 처리)은 보지 못한다 - 그것은 진짜 PostgreSQL
 * 로 한 번 돌려 봐야 한다.
 *
 * <p>Spring 을 띄우지 않고 연결을 직접 연다. 시험용 H2 는 이미 다른 스키마를
 * 들고 있어 거기 또 올리면 표가 겹친다.
 */
class PostgresqlSchemaLoadTest {

    private static final Path SCHEMA = Path.of("db/schema-postgresql.sql");

    @Test
    void 운영_스키마가_문법_오류_없이_돌아간다() throws Exception {
        // 시험마다 다른 DB 를 쓴다. 이름이 같으면 앞 시험이 남긴 표를 본다.
        String url = "jdbc:h2:mem:pgschema-" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH";

        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            run(connection, Files.readString(SCHEMA, StandardCharsets.UTF_8));

            assertThat(tableCount(connection))
                    .as("표 스물셋이 선언한 대로 생겨야 한다")
                    .isEqualTo(23);
        }
    }

    /**
     * H2 가 못 받아 넘어간 문장이 늘지 않았는지 본다.
     *
     * <p>넘어간 문장은 <b>어디서도 검증되지 않는다</b>. 그 수를 못 박아 두면
     * 새로 늘 때 이 시험이 알려 준다 - 모르는 채로 늘어나는 것이 문제다.
     */
    @Test
    void 검증하지_못한_문장은_부분_색인_하나뿐이다() throws Exception {
        String url = "jdbc:h2:mem:pgskip-" + UUID.randomUUID() + ";MODE=PostgreSQL";

        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            List<String> skipped =
                    run(connection, Files.readString(SCHEMA, StandardCharsets.UTF_8));

            assertThat(skipped)
                    .as("PostgreSQL 전용 문장이 늘었다. 진짜 PostgreSQL 로 확인해야 한다")
                    .hasSize(1);
            assertThat(skipped.get(0)).contains("ix_sheet_lock_line01");
        }
    }

    @Test
    void 사용자_표의_이메일_고유_제약이_실제로_막는다() throws Exception {
        String url = "jdbc:h2:mem:pgunique-" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE";

        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            run(connection, Files.readString(SCHEMA, StandardCharsets.UTF_8));

            String insert = """
                    INSERT INTO wo_user_account
                        (USER_KEY, EMAIL, DISPLAY_NAME, ROLE_CODE)
                    VALUES (?, 'hong@example.com', '홍길동', 'USER')
                    """;
            try (var first = connection.prepareStatement(insert)) {
                first.setString(1, "k1");
                first.executeUpdate();
            }

            // 같은 이메일로 두 계정이 생기면 둘 다 "내 계정" 으로 보인다.
            try (var second = connection.prepareStatement(insert)) {
                second.setString(1, "k2");
                assertThat(catchThrowable(second::executeUpdate))
                        .as("이메일 고유 제약이 막아야 한다")
                        .isNotNull();
            }
        }
    }

    private static Throwable catchThrowable(ThrowingRunnable given) {
        try {
            given.run();
            return null;
        } catch (Exception e) {
            return e;
        }
    }

    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    /**
     * PostgreSQL 에만 있어 H2 가 못 받는 문장.
     *
     * <p>지금은 부분 색인({@code CREATE INDEX ... WHERE ...}) 하나다. 올바른
     * PostgreSQL 이고 H2 에 없는 기능이다 - 틀린 것이 아니므로 넘어간다.
     * 넘어간 만큼은 진짜 PostgreSQL 로 한 번 돌려 봐야 한다.
     */
    private static boolean isPostgresqlOnly(String sql) {
        String upper = sql.toUpperCase();
        return upper.startsWith("CREATE INDEX") && upper.contains(" WHERE ");
    }

    /**
     * 세미콜론으로 끊어 한 문장씩 보낸다. 넘어간 문장을 돌려준다.
     *
     * <p>H2 는 여러 문장을 한 번에 받지만, 그러면 어느 문장이 틀렸는지 알려
     * 주지 않는다 - 이 시험의 값은 "어디가 틀렸는지" 에 있다.
     */
    private static List<String> run(Connection connection, String script) throws Exception {
        List<String> skipped = new ArrayList<>();
        try (Statement statement = connection.createStatement()) {
            int no = 0;
            for (String one : script.split(";")) {
                String sql = stripComments(one).trim();
                if (sql.isEmpty()) {
                    continue;
                }
                no++;
                try {
                    statement.execute(sql);
                } catch (Exception e) {
                    if (isPostgresqlOnly(sql)) {
                        skipped.add(sql);
                        continue;
                    }
                    throw new AssertionError(
                            "%s 의 %d 번째 문장이 실패했다:%n%s%n%n%s"
                                    .formatted(SCHEMA, no, sql, e.getMessage()), e);
                }
            }
        }
        return skipped;
    }

    /** 한 줄 주석을 떼어 낸다. 주석만 남은 토막을 빈 것으로 보기 위한 것이다. */
    private static String stripComments(String sql) {
        StringBuilder out = new StringBuilder();
        for (String line : sql.split("\\R")) {
            String trimmed = line.trim();
            if (!trimmed.startsWith("--")) {
                out.append(line).append('\n');
            }
        }
        return out.toString();
    }

    private static int tableCount(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet found = statement.executeQuery("""
                     SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES
                      WHERE UPPER(TABLE_SCHEMA) = 'PUBLIC'
                        AND LOWER(TABLE_NAME) LIKE 'wo\\_%'
                     """)) {
            found.next();
            return found.getInt(1);
        }
    }
}
