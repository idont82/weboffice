package weboffice.oss;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 설정 파일에 비밀과 사내 주소가 박히는 것을 막는다.
 *
 * <p>왜 시험으로 두는가: 사람 눈으로 하는 확인은 한 번은 반드시 빠뜨리고,
 * 빠뜨린 것을 알게 되는 때는 이미 공개한 뒤다. 한 번 새어 나간 열쇠와 주소는
 * 커밋을 되돌려도 돌아오지 않는다. 실제로 이 저장소에는 운영 DB 계정과
 * 비밀번호가 평문으로 들어 있었다(2026-10-22 에 걷어냈다).
 *
 * <p>금지 <b>낱말 목록</b>을 두지 않는다. 그 목록 자체가 사내 이름과 비밀을
 * 적어 둔 파일이 되어, 저장소에서 지우려던 것을 저장소에 더하게 된다. 대신
 * <b>모양</b>을 본다.
 *
 * <p>여기 걸리면 시험을 고쳐 통과시키지 말고 설정을 고쳐라. 시험을 고치는
 * 것은 이 시험을 없애는 것과 같다.
 */
class PublicReleaseScanTest {

    /**
     * 비밀번호·열쇠를 값으로 적어 둔 자리.
     *
     * <p>{@code ${WO_DB_PASSWORD:}} 처럼 환경 변수로 받는 것은 통과다. 빈 값도
     * 통과다. 글자가 바로 적혀 있으면 걸린다.
     */
    private static final Pattern BAKED_SECRET = Pattern.compile(
            "(?im)^\\s*(password|secret|api-key|apikey|token|credential)\\s*:\\s*"
            + "(?!\\s*$)(?!\\$\\{)(?!['\"]?\\$\\{)\\S.*$");

    /**
     * 설정에 박힌 IPv4.
     *
     * <p>localhost(127.)와 RFC 5737 이 문서·예시 전용으로 떼어 둔 셋
     * (192.0.2 · 198.51.100 · 203.0.113)만 허용한다. 그 밖의 주소는 어딘가
     * 실재하는 망을 가리키고, 설정에 박혀 있으면 공개판을 받은 사람에게는
     * 쓸모가 없으면서 우리 망 구조만 알려 준다.
     */
    private static final Pattern BARE_IPV4 = Pattern.compile(
            "(?<![\\d.])(?!127\\.)(?!0\\.0\\.0\\.0)(?!192\\.0\\.2\\.)"
            + "(?!198\\.51\\.100\\.)(?!203\\.0\\.113\\.)"
            + "\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}(?![\\d.])");

    @org.junit.jupiter.api.Test
    void 설정에_비밀번호를_적어_두지_않는다() throws IOException {
        assertThat(hits(BAKED_SECRET))
                .as("환경 변수(${...})로 받아야 한다. 시험을 고쳐 통과시키지 말 것")
                .isEmpty();
    }

    @org.junit.jupiter.api.Test
    void 설정에_사내_주소를_적어_두지_않는다() throws IOException {
        assertThat(hits(BARE_IPV4))
                .as("localhost 와 RFC 5737 문서용 대역만 쓴다")
                .isEmpty();
    }

    /**
     * 어디에도 회사 이름이 남지 않는지 본다.
     *
     * <p>이 검사는 두 번 뚫렸다. 처음에는 <b>도메인만</b> 봤다 - 회사 이름에
     * world 를 붙인 꼴이라, 회사 이름 자체는 걸리지 않았다. 그리고 자바만
     * 훑었다. 그래서 제품 이름에 회사 이름이 붙은 채 화면 제목으로 11개 파일에
     * 남아 공개됐고, 브라우저 탭에 그대로 떴다. 띄워 보고서야 알았다.
     *
     * <p>이제 <b>회사 이름 토막</b>을 대소문자 가리지 않고 보고, 프런트와
     * {@code pom.xml} 까지 훑는다. 공개되는 글자는 자바에만 있지 않다.
     *
     * <p>찾을 낱말을 글로 적어 두면 이 파일 자신이 걸린다. 그래서 토막으로
     * 나눠 두고 이을 때 합친다 - 예외 목록을 두는 쪽은 그 목록 안에 무엇이든
     * 숨길 수 있어 검사의 뜻이 약해진다. 이 시험도 자기 자신을 검사한다.
     */
    @org.junit.jupiter.api.Test
    void 어디에도_회사_이름이_없다() throws IOException {
        String word = "k" + "cc";

        List<String> hits = new ArrayList<>();
        for (Path file : publishedTextFiles()) {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            if (text.toLowerCase(java.util.Locale.ROOT).contains(word)) {
                hits.add(file.toString());
            }
        }
        assertThat(hits)
                .as("제품 이름과 패키지에서 회사 이름을 뗐다. 화면 제목까지 본다")
                .isEmpty();
    }

    /**
     * 공개되는 글자 파일 전부.
     *
     * <p>{@code node_modules} 와 내려받은 node 는 뺀다 - 우리가 쓴 글이 아니고,
     * 그 안에는 임의의 바이트가 들어 있어 무엇이든 걸린다.
     */
    private static List<Path> publishedTextFiles() throws IOException {
        List<Path> found = new ArrayList<>();
        Path pom = Path.of("pom.xml");
        if (Files.isRegularFile(pom)) {
            found.add(pom);
        }
        for (Path root : List.of(Path.of("src/main/java"), Path.of("src/main/resources"),
                Path.of("src/test/java"), Path.of("frontend/src"),
                Path.of("frontend/public"), Path.of("db"))) {
            if (Files.isDirectory(root)) {
                found.addAll(textFilesUnder(root));
            }
        }
        // frontend 바로 아래의 html 만. 그 아래 node_modules 로 내려가지 않는다.
        Path frontend = Path.of("frontend");
        if (Files.isDirectory(frontend)) {
            try (Stream<Path> listed = Files.list(frontend)) {
                listed.filter(Files::isRegularFile)
                        .filter(one -> one.getFileName().toString().endsWith(".html"))
                        .forEach(found::add);
            }
        }
        return found;
    }

    private static List<Path> textFilesUnder(Path root) throws IOException {
        try (Stream<Path> walked = Files.walk(root)) {
            return walked.filter(Files::isRegularFile)
                    .filter(one -> {
                        String path = one.toString().replace('\\', '/');
                        return !path.contains("/node_modules/") && !path.contains("/node/");
                    })
                    .filter(one -> {
                        String name = one.getFileName().toString();
                        return name.endsWith(".java") || name.endsWith(".xml")
                                || name.endsWith(".ts") || name.endsWith(".html")
                                || name.endsWith(".yml") || name.endsWith(".yaml")
                                || name.endsWith(".json") || name.endsWith(".sql")
                                || name.endsWith(".properties");
                    })
                    .toList();
        }
    }

    /**
     * 예시로 쓰는 사람 이름.
     *
     * <p>{@code 홍길동} 은 한국에서 쓰는 표준 예시 이름이고, {@code 나} 는
     * 시험이 "지금 로그인한 사람" 을 가리킬 때 쓰는 말이다. 이 둘만 허용한다.
     */
    private static final java.util.Set<String> EXAMPLE_NAMES =
            java.util.Set.of("홍길동", "나");

    /**
     * 글자값 안에 든 "이름 직급" 꼴.
     *
     * <p>여는 겹따옴표를 반드시 요구한다. 산문 주석에는 모듈 책임, 사이사이
     * 처럼 사람 이름이 아닌데 같은 모양이 되는 말이 있어서, 그것까지 걸면
     * 검사가 울다 지쳐 꺼지게 된다. 실제로 새는 자리는 <b>값으로 적어 둔
     * 이름</b>이다 - 시험 자료와 주석의 예시.
     *
     * <p>이 주석에서 그 두 낱말을 따옴표로 묶지 않은 것도 그래서다. 묶으면 이
     * 시험이 자기 주석에 걸린다. 한 번 걸려 보고 고쳤다.
     */
    private static final Pattern NAMED_PERSON = Pattern.compile(
            "[\"']([가-힣]{2,4}) *(책임|수석|부장|과장|대리|차장|사원|팀장|이사|상무|전무)");

    /** 퍼센트 인코딩 토막. 머리에 실어 보내는 한글이 이 꼴로 적혀 있다. */
    private static final Pattern PERCENT_RUN = Pattern.compile("(?:%[0-9A-Fa-f]{2})+");

    /**
     * 코드에 실재하는 사람의 이름이 남지 않는지 본다.
     *
     * <p>왜 따로 두는가: 위의 회사 이름 검사는 <b>회사</b>만 봤다. 그래서
     * 만든 사람의 실명이 주석과 시험 자료 47곳에 예시로 박힌 채 공개됐고,
     * 걷어낸 뒤에 이 검사를 더했다. 회사 이름을 지우는 것과 사람 이름을
     * 지우는 것은 다른 일이다.
     *
     * <p>찾을 이름을 적어 두지 않는다 - 그러면 이 파일이 그 사람의 이름을
     * 적어 둔 파일이 된다. 대신 <b>허용할 예시 이름</b>만 적고 나머지를 전부
     * 막는다. 새 이름이 들어오면 그것이 누구든 걸린다.
     */
    @org.junit.jupiter.api.Test
    void 코드에_실명을_남기지_않는다() throws IOException {
        List<String> hits = new ArrayList<>();
        for (Path file : exampleBearingFiles()) {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            collectNames(file, text, hits);
            collectNames(file, percentDecoded(text), hits);
        }
        assertThat(hits)
                .as("예시 이름은 홍길동 을 쓴다. 실재하는 사람의 이름을 두지 말 것")
                .isEmpty();
    }

    private static void collectNames(Path file, String text, List<String> hits) {
        Matcher matcher = NAMED_PERSON.matcher(text);
        while (matcher.find()) {
            if (!EXAMPLE_NAMES.contains(matcher.group(1))) {
                hits.add(file + ": " + matcher.group().substring(1));
            }
        }
    }

    /**
     * 퍼센트 인코딩을 푼 글.
     *
     * <p>왜 필요한가: 뷰어는 열람자 이름을 HTTP 머리에 싣고, 머리에 한글을
     * 그대로 넣으면 깨지니 URL 로 싸서 보낸다. 그래서 시험 자료에 이름이
     * {@code %EA%B9%80...} 꼴로 적혀 있었고, 글자만 보는 검사는 그것을 놓쳤다.
     * 실제로 이 구멍으로 실명 하나가 공개 저장소까지 갔다.
     *
     * <p>푼 뒤 여는 따옴표가 사라지는 자리도 있어, 토막 앞에 따옴표를 끼워
     * 잇는다 - 인코딩된 값은 통째로 글자값 안에 들어 있기 때문이다.
     */
    private static String percentDecoded(String text) {
        Matcher matcher = PERCENT_RUN.matcher(text);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            try {
                out.append('"')
                        .append(java.net.URLDecoder.decode(matcher.group(),
                                StandardCharsets.UTF_8))
                        .append('\n');
            } catch (IllegalArgumentException ignored) {
                // 인코딩이 아닌 퍼센트. 볼 것이 없다.
            }
        }
        return out.toString();
    }

    /** 사람 이름이 예시로 들어갈 수 있는 자리 전부. 자바·시험·프런트. */
    private static List<Path> exampleBearingFiles() throws IOException {
        List<Path> found = new ArrayList<>();
        for (Path root : List.of(Path.of("src/main/java"), Path.of("src/test/java"),
                Path.of("frontend/src"), Path.of("frontend/public"))) {
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (Stream<Path> walked = Files.walk(root)) {
                walked.filter(Files::isRegularFile)
                        .filter(one -> {
                            String name = one.getFileName().toString();
                            return name.endsWith(".java") || name.endsWith(".ts")
                                    || name.endsWith(".html");
                        })
                        .forEach(found::add);
            }
        }
        return found;
    }

    /**
     * 훑을 파일이 실제로 있는지 본다. 없으면 위 둘이 빈 채로 통과한다.
     *
     * <p>지금 셋이다 - application.yml 과 프로필 둘(dev, postgresql).
     */
    @org.junit.jupiter.api.Test
    void 훑을_설정_파일을_찾는다() throws IOException {
        assertThat(configFiles()).hasSizeGreaterThanOrEqualTo(3);
    }

    private static List<String> hits(Pattern pattern) throws IOException {
        List<String> found = new ArrayList<>();
        for (Path file : configFiles()) {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            Matcher matcher = pattern.matcher(text);
            while (matcher.find()) {
                found.add(file + ": " + matcher.group().trim());
            }
        }
        return found;
    }

    /**
     * 설정 파일만 본다.
     *
     * <p>자바·시험 코드는 보지 않는다. 시험은 일부러 주소와 비밀번호 같은 값을
     * 적어 두고 그 판정을 확인하는 자리이고, 그것을 금지하면 그 시험을 쓸 수
     * 없다. 실제로 샐 수 있는 자리는 <b>돌아가는 서버가 읽는 설정</b>이다.
     */
    private static List<Path> configFiles() throws IOException {
        Path root = Path.of("src/main/resources");
        try (Stream<Path> walked = Files.walk(root)) {
            return walked.filter(Files::isRegularFile)
                    .filter(one -> {
                        String name = one.getFileName().toString();
                        return name.endsWith(".yml") || name.endsWith(".yaml")
                                || name.endsWith(".properties");
                    })
                    .toList();
        }
    }
}
