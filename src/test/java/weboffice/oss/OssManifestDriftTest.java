package weboffice.oss;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 오픈소스 목록이 실제 의존성과 어긋나지 않는지 본다.
 *
 * <p>이 시험이 이 기능의 값이다. 목록을 손으로 적는 대신 플러그인으로 긁어 모으면
 * 추이 의존까지 수백 개가 나오고 라이선스 문자열이 뒤섞여 사람이 못 읽는다. 그래서
 * 손으로 적되, <b>적지 않으면 빌드가 깨지게</b> 한다 - 라이선스를 모르는 라이브러리가
 * 조용히 들어오는 것이 여기서 가장 위험한 일이다.
 *
 * <p>좌표가 비어 있는 항목({@code coords: []})은 선언에 없는 것이다 - 자바 런타임,
 * node, npm 처럼 설정이나 도구로 박혀 있는 것들이라 어긋남을 셀 수 없다.
 */
class OssManifestDriftTest {

    /** {@code ${poi.version}} 같은 자리. */
    private static final Pattern PROPERTY = Pattern.compile("\\$\\{([^}]+)}");

    private static List<OssItem> items;
    private static Map<String, String> javaDeclared;
    private static Map<String, String> frontDeclared;

    @BeforeAll
    static void 선언을_읽는다() throws Exception {
        items = OssCatalog.load();
        javaDeclared = new LinkedHashMap<>();
        javaDeclared.putAll(readPom("dependencies", "dependency"));
        javaDeclared.putAll(readPom("plugins", "plugin"));
        frontDeclared = readPackageJson();
    }

    @Test
    void 선언한_자바_의존성이_모두_목록에_있다() {
        assertThat(coords()).containsAll(javaDeclared.keySet());
    }

    @Test
    void 선언한_프런트_패키지가_모두_목록에_있다() {
        assertThat(coords()).containsAll(frontDeclared.keySet());
    }

    @Test
    void 목록에만_있는_좌표는_없다() {
        // 지운 라이브러리가 목록에 남으면 "쓰고 있다" 고 잘못 알린다.
        Set<String> declared = new LinkedHashSet<>(javaDeclared.keySet());
        declared.addAll(frontDeclared.keySet());

        assertThat(coords()).isSubsetOf(declared);
    }

    @Test
    void 버전을_적었으면_선언과_같다() {
        for (OssItem item : items) {
            if (item.version() == null) {
                // 상위(parent)가 정하거나 좌표마다 버전이 다른 것들이다.
                continue;
            }
            for (String coord : item.coords()) {
                assertThat(declaredVersion(coord))
                        .as("%s 의 선언 버전", coord)
                        .isEqualTo(item.version());
            }
        }
    }

    @Test
    void 버전을_안_적은_것은_선언에도_버전이_없다() {
        for (OssItem item : items) {
            if (item.version() != null || item.coords().size() != 1) {
                continue;
            }
            String coord = item.coords().get(0);
            assertThat(declaredVersion(coord))
                    .as("%s 는 선언에 버전이 있는데 목록에는 없다", coord)
                    .isNull();
        }
    }

    @Test
    void 좌표가_겹치지_않는다() {
        // 같은 좌표가 두 항목에 들어가면 화면에 두 번 나오고 고지문도 두 번 적힌다.
        List<String> all = items.stream().flatMap(one -> one.coords().stream()).toList();

        assertThat(all).doesNotHaveDuplicates();
    }

    @Test
    void 모든_항목이_이름과_라이선스와_쓰임을_갖는다() {
        for (OssItem item : items) {
            assertThat(item.name()).isNotBlank();
            assertThat(item.license()).isNotBlank();
            assertThat(item.what()).isNotBlank();
            assertThat(item.side()).isNotNull();
            assertThat(item.kind()).isNotNull();
            assertThat(item.dist()).isNotNull();
        }
    }

    @Test
    void 오픈소스는_출처_주소를_갖는다() {
        // 라이선스 전문을 확인할 자리가 없으면 고지문을 만들 수 없다.
        for (OssItem item : items) {
            if (item.kind() != OssItem.Kind.OSS) {
                continue;
            }
            assertThat(item.url()).as("%s 의 주소", item.name()).isNotBlank();
        }
    }

    private Set<String> coords() {
        Set<String> found = new LinkedHashSet<>();
        for (OssItem item : items) {
            found.addAll(item.coords());
        }
        return found;
    }

    private String declaredVersion(String coord) {
        return javaDeclared.containsKey(coord)
                ? javaDeclared.get(coord)
                : frontDeclared.get(coord);
    }

    /**
     * pom 에서 좌표와 버전을 읽는다.
     *
     * <p>{@code ${poi.version}} 처럼 속성으로 적은 버전은 풀어 준다. 버전을 안 적은
     * 것(parent 가 정하는 것)은 {@code null} 로 둔다.
     */
    private static Map<String, String> readPom(String listTag, String itemTag) throws Exception {
        Element project = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(Path.of("pom.xml").toFile())
                .getDocumentElement();
        Map<String, String> properties = readProperties(project);

        Map<String, String> found = new LinkedHashMap<>();
        for (Element list : childrenNamed(project, listTag)) {
            // <dependencies> 는 <dependencyManagement> 안에도 있을 수 있다. 여기서는
            // project 바로 밑과 <build> 밑만 본다.
            for (Element one : childrenNamed(list, itemTag)) {
                String group = text(one, "groupId");
                String artifact = text(one, "artifactId");
                found.put(group + ":" + artifact, resolve(text(one, "version"), properties));
            }
        }
        for (Element build : childrenNamed(project, "build")) {
            for (Element list : childrenNamed(build, listTag)) {
                for (Element one : childrenNamed(list, itemTag)) {
                    found.put(text(one, "groupId") + ":" + text(one, "artifactId"),
                            resolve(text(one, "version"), properties));
                }
            }
        }
        return found;
    }

    private static Map<String, String> readProperties(Element project) {
        Map<String, String> properties = new LinkedHashMap<>();
        for (Element block : childrenNamed(project, "properties")) {
            NodeList children = block.getChildNodes();
            for (int i = 0; i < children.getLength(); i += 1) {
                Node node = children.item(i);
                if (node.getNodeType() == Node.ELEMENT_NODE) {
                    properties.put(node.getNodeName(), node.getTextContent().trim());
                }
            }
        }
        return properties;
    }

    private static String resolve(String version, Map<String, String> properties) {
        if (version == null) {
            return null;
        }
        Matcher matcher = PROPERTY.matcher(version);
        return matcher.matches() ? properties.get(matcher.group(1)) : version;
    }

    /** npm 좌표는 {@code npm:} 을 붙여 자바 좌표와 섞이지 않게 한다. */
    private static Map<String, String> readPackageJson() throws IOException {
        JsonNode root = JsonMapper.builder().build()
                .readTree(Files.readString(Path.of("frontend/package.json")));
        Map<String, String> found = new LinkedHashMap<>();
        for (String block : List.of("dependencies", "devDependencies")) {
            JsonNode node = root.get(block);
            if (node == null) {
                continue;
            }
            for (Map.Entry<String, JsonNode> entry : node.properties()) {
                found.put("npm:" + entry.getKey(), bareVersion(entry.getValue().asString()));
            }
        }
        return found;
    }

    /** {@code ^0.25.1} 에서 {@code 0.25.1} 만 꺼낸다. */
    private static String bareVersion(String range) {
        return range.replaceAll("^[\\^~>=<\\s]+", "");
    }

    private static List<Element> childrenNamed(Element parent, String name) {
        List<Element> found = new ArrayList<>();
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i += 1) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE && node.getNodeName().equals(name)) {
                found.add((Element) node);
            }
        }
        return found;
    }

    private static String text(Element parent, String name) {
        List<Element> found = childrenNamed(parent, name);
        return found.isEmpty() ? null : found.get(0).getTextContent().trim();
    }

    /** 좌표를 두 번 세지 않는지 스스로 본다 - 이 시험이 쓰는 도구부터 맞아야 한다. */
    @Test
    void pom_을_읽으면_좌표가_나온다() {
        assertThat(javaDeclared).containsKey("org.apache.poi:poi-ooxml");
        assertThat(javaDeclared.get("org.apache.poi:poi-ooxml")).isEqualTo("5.5.1");
        assertThat(javaDeclared).containsKey("org.springframework.boot:spring-boot-starter-web");
        assertThat(javaDeclared.get("org.springframework.boot:spring-boot-starter-web")).isNull();
        assertThat(javaDeclared).containsKey("com.github.eirslett:frontend-maven-plugin");
        assertThat(new HashSet<>(javaDeclared.keySet())).hasSize(javaDeclared.size());
    }

    @Test
    void package_json_을_읽으면_좌표가_나온다() {
        assertThat(frontDeclared).containsEntry("npm:@univerjs/core", "1.0.4");
        assertThat(frontDeclared).containsEntry("npm:vite", "8.2.0");
    }
}
