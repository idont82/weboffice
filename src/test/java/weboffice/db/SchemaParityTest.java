package weboffice.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * 운영 스키마와 시험 스키마가 어긋나지 않는지 본다.
 *
 * <p>왜 필요한가: 시험은 전부 H2({@code src/test/resources/db/schema-h2.sql})에서
 * 돈다. 운영 스키마({@code db/schema-postgresql.sql})는 <b>어떤 시험에서도 돌지
 * 않는다</b> - 거기 표나 컬럼이 빠져 있으면 {@code docker compose up} 을 하고
 * 나서야 드러난다. 두 파일을 손으로 같이 고치는 규칙은 한 번은 반드시 어긋난다.
 *
 * <p>타입은 보지 않는다. 벤더마다 이름이 다른 것이 정상이다(bytea/VARBINARY,
 * jsonb/CLOB). 표 이름과 컬럼 이름·순서만 본다 - MyBatis 가
 * {@code map-underscore-to-camel-case} 로 컬럼 이름에 기대고 있어 그것이
 * 어긋나면 조용히 null 이 된다.
 */
class SchemaParityTest {

    private static final Path POSTGRESQL = Path.of("db/schema-postgresql.sql");
    private static final Path H2 = Path.of("src/test/resources/db/schema-h2.sql");

    private static final Pattern TABLE =
            Pattern.compile("CREATE TABLE\\s+(\\w+)\\s*\\(", Pattern.CASE_INSENSITIVE);

    /**
     * 읽어 내는 일 자체가 되는지 먼저 본다.
     *
     * <p>이것이 없으면 파서가 빈 결과를 내도 아래 비교가 "빈 것끼리 같다" 로
     * 통과한다 - 스키마 모양이 바뀌어 한 줄도 못 읽게 되는 날 조용히 지나간다.
     */
    @Test
    void 두_파일에서_표를_읽어_낸다() throws IOException {
        assertThat(tablesOf(POSTGRESQL)).as("운영 스키마").hasSize(23);
        assertThat(tablesOf(H2)).as("시험 스키마").hasSize(23);
        // tablesOf 가 표 이름을 대문자로 맞춰 담는다. 두 스키마가 대소문자만
        // 다르게 적혀 있어도 같은 표로 보기 위한 것이다.
        assertThat(tablesOf(POSTGRESQL).get("WO_USER_ACCOUNT"))
                .as("컬럼까지 읽혀야 한다")
                .contains("USER_KEY", "EMAIL", "PASSWORD_HASH");
    }

    @Test
    void 두_스키마가_같은_표를_만든다() throws IOException {
        assertThat(tablesOf(POSTGRESQL).keySet())
                .as("한쪽에만 있는 표가 있으면 운영이나 시험 한쪽이 깨진다")
                .containsExactlyInAnyOrderElementsOf(tablesOf(H2).keySet());
    }

    @Test
    void 표마다_같은_컬럼을_같은_순서로_만든다() throws IOException {
        Map<String, List<String>> postgresql = tablesOf(POSTGRESQL);
        Map<String, List<String>> h2 = tablesOf(H2);

        for (Map.Entry<String, List<String>> each : postgresql.entrySet()) {
            List<String> there = h2.get(each.getKey());
            if (there == null) {
                continue;   // 앞 시험이 잡는다. 여기서 또 터뜨리면 원인이 흐려진다.
            }
            assertThat(each.getValue())
                    .as("%s 의 컬럼", each.getKey())
                    .isEqualTo(there);
        }
    }

    /**
     * {@code CREATE TABLE} 마다 표 이름과 컬럼 이름을 뽑는다.
     *
     * <p>SQL 을 제대로 파싱하지 않는다. 우리 스키마는 한 줄에 컬럼 하나씩 적는
     * 모양이 지켜지고 있고, 그 모양만 읽으면 된다 - 파서를 들이면 유지보수할
     * 것이 하나 더 는다.
     */
    private static Map<String, List<String>> tablesOf(Path path) throws IOException {
        String text = Files.readString(path, StandardCharsets.UTF_8);
        Map<String, List<String>> found = new LinkedHashMap<>();

        Matcher matcher = TABLE.matcher(text);
        while (matcher.find()) {
            int from = matcher.end();
            int to = closingParen(text, from);
            found.put(matcher.group(1).toUpperCase(), columnsOf(text.substring(from, to)));
        }
        return found;
    }

    /** 여는 괄호 다음부터 짝이 맞는 닫는 괄호까지. 컬럼 안의 괄호(타입 길이)를 센다. */
    private static int closingParen(String text, int from) {
        int depth = 1;
        for (int at = from; at < text.length(); at++) {
            char one = text.charAt(at);
            if (one == '(') {
                depth++;
            } else if (one == ')') {
                depth--;
                if (depth == 0) {
                    return at;
                }
            }
        }
        throw new IllegalStateException("닫는 괄호가 없다: " + from);
    }

    private static List<String> columnsOf(String body) {
        List<String> names = new ArrayList<>();
        for (String line : body.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("--")
                    || trimmed.toUpperCase().startsWith("CONSTRAINT")
                    || trimmed.toUpperCase().startsWith("PRIMARY KEY")
                    || trimmed.toUpperCase().startsWith("FOREIGN KEY")
                    || trimmed.toUpperCase().startsWith("REFERENCES")
                    || trimmed.toUpperCase().startsWith("CHECK")) {
                continue;
            }
            String name = trimmed.split("[\\s,(]", 2)[0];
            if (!name.isEmpty()) {
                names.add(name.toUpperCase());
            }
        }
        return names;
    }
}
