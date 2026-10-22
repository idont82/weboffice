package weboffice.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 운영 스키마를 <b>진짜 PostgreSQL</b> 에 올린다.
 *
 * <p>왜 필요한가: {@code db/schema-postgresql.sql} 은 {@code docker compose up}
 * 이 처음 뜰 때 딱 한 번 돌고, 그때까지 아무도 돌려 보지 않는다. 그 한 번이
 * 실패하면 받은 사람의 첫 경험이 "안 깔린다" 다.
 *
 * <p>{@link PostgresqlSchemaLoadTest} 는 H2 의 호환 모드로 문법만 본다 -
 * 부분 색인을 넘어가고 {@code jsonb}·{@code bytea} 의 실제 동작을 못 본다.
 * 여기서는 그 셋까지 본다.
 *
 * <p>Docker 가 아니다. 의존이 품은 PostgreSQL 바이너리로 {@code initdb} 와
 * {@code postgres} 를 일반 사용자 권한으로 임시 디렉터리에 띄운다.
 */
class RealPostgresqlSchemaTest {

    private static final Path SCHEMA = Path.of("db/schema-postgresql.sql");

    private static EmbeddedPostgres postgres;
    private static DataSource dataSource;

    @BeforeAll
    static void 진짜_postgresql_을_띄운다() throws Exception {
        postgres = EmbeddedPostgres.start();
        dataSource = postgres.getPostgresDatabase();
    }

    @AfterAll
    static void 내린다() throws Exception {
        if (postgres != null) {
            postgres.close();
        }
    }

    @Test
    void 운영_스키마가_진짜_postgresql_에서_통째로_돈다() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            List<String> ran = run(connection, Files.readString(SCHEMA, StandardCharsets.UTF_8));

            assertThat(ran).as("문장이 하나도 안 돌았으면 읽기부터 틀렸다").hasSizeGreaterThan(40);
            assertThat(tableCount(connection)).isEqualTo(23);
        }
    }

    /**
     * H2 가 못 받아 넘어갔던 부분 색인이 여기서는 실제로 생긴다.
     *
     * <p>{@code PostgresqlSchemaLoadTest} 가 검증하지 못한다고 적어 둔 자리다.
     */
    @Test
    void 부분_색인이_실제로_만들어진다() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            run(connection, Files.readString(SCHEMA, StandardCharsets.UTF_8));

            try (Statement statement = connection.createStatement();
                 ResultSet found = statement.executeQuery("""
                         SELECT indexdef FROM pg_indexes
                          WHERE lower(indexname) = 'ix_sheet_lock_line01'
                         """)) {
                assertThat(found.next()).as("색인이 있어야 한다").isTrue();
                assertThat(found.getString(1)).containsIgnoringCase("WHERE");
            }
        }
    }

    /**
     * jsonb 가 실제로 JSON 을 검사한다.
     *
     * <p>H2 쪽은 같은 컬럼이 CLOB 이라 아무 글자나 받는다 - 운영에서만 거절
     * 당하는 차이가 여기서 드러난다.
     */
    @Test
    void jsonb_컬럼이_깨진_json_을_거절한다() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            run(connection, Files.readString(SCHEMA, StandardCharsets.UTF_8));
            long workbookSeq = 문서를_만든다(connection);

            String insert = """
                    INSERT INTO wo_cell_style_master
                        (WORKBOOK_SEQ, STYLE_CODE, STYLE_VALUE)
                    VALUES (?, ?, ?::jsonb)
                    """;

            // 먼저 올바른 JSON 이 들어가는 것을 본다. 이것이 없으면 아래 단정이
            // "컬럼 이름이 틀렸다" 로도 통과한다 - 실제로 한 번 그렇게 통과했다.
            try (PreparedStatement good = connection.prepareStatement(insert)) {
                good.setLong(1, workbookSeq);
                good.setString(2, "0".repeat(32));
                good.setString(3, "{\"bold\":true}");
                assertThat(good.executeUpdate()).isEqualTo(1);
            }

            try (PreparedStatement bad = connection.prepareStatement(insert)) {
                bad.setLong(1, workbookSeq);
                bad.setString(2, "1".repeat(32));
                bad.setString(3, "{깨진 json");
                assertThatThrownBy(bad::executeUpdate)
                        .as("jsonb 는 JSON 이 아닌 것을 받지 않는다. H2 쪽은 CLOB 이라 받는다")
                        .isNotNull();
            }
        }
    }

    @Test
    void bytea_컬럼이_바이트를_그대로_돌려준다() throws Exception {
        byte[] png = {(byte) 0x89, 80, 78, 71, 13, 10, 26, 10};

        try (Connection connection = dataSource.getConnection()) {
            run(connection, Files.readString(SCHEMA, StandardCharsets.UTF_8));
            long workbookSeq = 문서를_만든다(connection);

            try (PreparedStatement insert = connection.prepareStatement("""
                    INSERT INTO wo_sheet_image_master
                        (WORKBOOK_SEQ, IMAGE_HASH, MIME_TYPE, IMAGE_SIZE, IMAGE_DATA)
                    VALUES (?, ?, 'image/png', ?, ?)
                    """)) {
                insert.setLong(1, workbookSeq);
                // char(64) 다. 넘치면 "너무 긴 자료" 로 막힌다 - 64자를 딱 맞춘다.
                insert.setString(2, "a".repeat(64));
                insert.setInt(3, png.length);
                insert.setBytes(4, png);
                insert.executeUpdate();
            }

            try (Statement statement = connection.createStatement();
                 ResultSet found = statement.executeQuery(
                         "SELECT IMAGE_DATA FROM wo_sheet_image_master")) {
                assertThat(found.next()).isTrue();
                assertThat(found.getBytes(1)).isEqualTo(png);
            }
        }
    }

    @Test
    void 이메일_고유_제약이_실제로_막는다() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            run(connection, Files.readString(SCHEMA, StandardCharsets.UTF_8));

            String insert = """
                    INSERT INTO wo_user_account
                        (USER_KEY, EMAIL, DISPLAY_NAME, ROLE_CODE)
                    VALUES (?, 'hong@example.com', '홍길동', 'USER')
                    """;
            try (PreparedStatement first = connection.prepareStatement(insert)) {
                first.setString(1, "k1");
                first.executeUpdate();
            }
            try (PreparedStatement second = connection.prepareStatement(insert)) {
                second.setString(1, "k2");
                assertThatThrownBy(second::executeUpdate)
                        .as("같은 이메일로 두 계정이 생기면 둘 다 \"내 계정\" 으로 보인다")
                        .isNotNull();
            }
        }
    }

    private static long 문서를_만든다(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet made = statement.executeQuery("""
                     INSERT INTO wo_workbook_master (WORKBOOK_NAME, OWNER_USER, DOC_TYPE)
                     VALUES ('시험문서', 'k1', 'SHEET')
                     RETURNING WORKBOOK_SEQ
                     """)) {
            made.next();
            return made.getLong(1);
        }
    }

    /** 세미콜론으로 끊어 한 문장씩 보낸다. 어느 문장이 틀렸는지가 이 시험의 값이다. */
    private static List<String> run(Connection connection, String script) throws Exception {
        List<String> ran = new ArrayList<>();
        try (Statement statement = connection.createStatement()) {
            for (String one : script.split(";")) {
                String sql = stripComments(one).trim();
                if (sql.isEmpty()) {
                    continue;
                }
                try {
                    statement.execute(sql);
                    ran.add(sql);
                } catch (Exception e) {
                    throw new AssertionError(
                            "%s 의 %d 번째 문장이 진짜 PostgreSQL 에서 실패했다:%n%s%n%n%s"
                                    .formatted(SCHEMA, ran.size() + 1, sql, e.getMessage()), e);
                }
            }
        }
        return ran;
    }

    private static String stripComments(String sql) {
        StringBuilder out = new StringBuilder();
        for (String line : sql.split("\\R")) {
            if (!line.trim().startsWith("--")) {
                out.append(line).append('\n');
            }
        }
        return out.toString();
    }

    private static int tableCount(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet found = statement.executeQuery("""
                     SELECT COUNT(*) FROM information_schema.tables
                      WHERE table_schema = 'public' AND table_name LIKE 'wo\\_%'
                     """)) {
            found.next();
            return found.getInt(1);
        }
    }
}
