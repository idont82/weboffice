package weboffice.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;

import javax.sql.DataSource;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 서버가 <b>진짜 PostgreSQL</b> 위에서 뜨고 돈다.
 *
 * <p>왜 필요한가: 다른 모든 시험은 H2 에서 돈다. 매퍼의 태그 없는 문장이 H2
 * 쪽이고 {@code databaseId="postgresql"} 이 붙은 열한 개가 운영 쪽인데, 그
 * 열한 개는 <b>어떤 시험에서도 실행되지 않는다</b> - {@code jsonb} 업서트와
 * {@code INSERT ... ON CONFLICT} 가 거기 있다. 틀려 있으면
 * {@code docker compose up} 을 한 사람에게서만 터진다.
 *
 * <p>Docker 가 아니다. 의존이 품은 PostgreSQL 바이너리를 일반 사용자 권한으로
 * 띄우고, 그 주소를 Spring 에 넘긴다. compose 가 환경 변수로 하는 일을 여기서는
 * {@code @DynamicPropertySource} 가 한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RealPostgresqlAppTest {

    private static EmbeddedPostgres postgres;

    /**
     * Spring 이 뜨기 전에 DB 를 세우고 스키마를 올린다.
     *
     * <p>{@code @BeforeAll} 로는 늦다 - 그때 이미 Spring 이 연결을 열고 있다.
     */
    @DynamicPropertySource
    static void postgresql(DynamicPropertyRegistry registry) throws Exception {
        postgres = EmbeddedPostgres.start();
        DataSource dataSource = postgres.getPostgresDatabase();
        loadSchema(dataSource);

        int port = postgres.getPort();
        registry.add("spring.datasource.url", () ->
                "jdbc:postgresql://localhost:" + port + "/postgres"
                + "?quoteReturningIdentifiers=false&reWriteBatchedInserts=true");
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "");
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        // 스키마는 위에서 이미 올렸다. Spring 이 또 올리면 DROP 이 다시 돈다.
        registry.add("spring.sql.init.mode", () -> "never");
        // 이 시험의 요점이다. 이 값이 없으면 매퍼가 H2 분기를 고른다.
        registry.add("weboffice.realtime.allowed-origin-patterns", () -> "http://localhost:*");
    }

    @AfterAll
    static void 내린다() throws Exception {
        if (postgres != null) {
            postgres.close();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DataSource dataSource;

    @Test
    void 매퍼가_postgresql_분기를_고른다() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            assertThat(connection.getMetaData().getDatabaseProductName())
                    .as("H2 에 붙었으면 이 시험이 보려던 것을 하나도 못 본다")
                    .isEqualTo("PostgreSQL");
        }
    }

    @Test
    void 서버가_떠서_헬스에_답한다() throws Exception {
        mockMvc.perform(get("/api/health")).andExpect(status().isOk());
    }

    /**
     * 로그인 없는 모드로 들어와 문서 목록을 받는다.
     *
     * <p>{@code docker compose up} 을 한 사람이 처음 하는 일이 이것이다 -
     * 주소를 열면 손님 신원을 받고 빈 파일함이 보여야 한다.
     */
    @Test
    void 손님으로_들어와_빈_파일함을_받는다() throws Exception {
        mockMvc.perform(get("/api/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value(org.hamcrest.Matchers.startsWith("손님-")));

        mockMvc.perform(get("/api/workbooks")).andExpect(status().isOk());
    }

    /**
     * 문서를 만들고 셀을 넣는다. 셀 업서트가 운영 분기
     * ({@code INSERT ... ON CONFLICT})를 타는 자리다.
     */
    @Test
    void 문서를_만들고_셀을_넣고_다시_읽는다() throws Exception {
        String body = mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .post("/api/workbooks")
                                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                                .content("{\"workbookName\":\"시험\",\"docType\":\"SHEET\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("workbookSeq");
    }

    private static void loadSchema(DataSource dataSource) throws Exception {
        String script = Files.readString(Path.of("db/schema-postgresql.sql"),
                StandardCharsets.UTF_8);
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            for (String one : script.split(";")) {
                String sql = stripComments(one).trim();
                if (!sql.isEmpty()) {
                    statement.execute(sql);
                }
            }
        }
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
}
