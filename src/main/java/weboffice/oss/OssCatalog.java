package weboffice.oss;

import java.io.IOException;
import java.io.InputStream;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Component;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * 오픈소스 목록.
 *
 * <p>손으로 적은 {@code oss-manifest.json} 을 읽는다. 플러그인으로 긁어 모으지 않는
 * 까닭: 추이 의존까지 더하면 수백 개가 되고 라이선스 문자열이 제각각이라 사람이 못
 * 읽는다. 관리자가 보고 싶은 것은 "무엇을 왜 쓰는가" 라 사람이 쓴 한 줄이 필요하다.
 *
 * <p>손으로 적은 것이 실제와 어긋날 위험은 {@code OssManifestDriftTest} 가 막는다 -
 * 선언에 있고 목록에 없으면 빌드가 깨진다.
 *
 * <p>읽기는 뜰 때 한 번이다. 파일이 깨졌으면 그 자리에서 뜨지 않는다 - 관리자가
 * 화면을 눌렀을 때야 알게 되면 "빈 목록" 과 구별이 안 된다.
 */
@Component
public class OssCatalog {

    private static final String RESOURCE = "/oss-manifest.json";

    private final List<OssItem> items;

    public OssCatalog() {
        this.items = load();
    }

    /** 갈래, 배포 여부, 이름 차례로 세운 목록. */
    public List<OssItem> items() {
        return items;
    }

    /** 배포물에 들어가는 오픈소스만. 고지문이 보는 것이다. */
    public List<OssItem> forNotice() {
        return items.stream().filter(OssItem::needsNotice).toList();
    }

    static List<OssItem> load() {
        List<OssItem> read = readResource();
        for (OssItem item : read) {
            if (isBlank(item.name()) || isBlank(item.license()) || isBlank(item.what())
                    || item.side() == null || item.kind() == null || item.dist() == null) {
                throw new IllegalStateException(
                        "오픈소스 목록이 덜 적혔습니다: " + item.name() + " (" + RESOURCE + ")");
            }
        }
        return read.stream()
                .sorted(Comparator.comparing((OssItem one) -> one.side().ordinal())
                        .thenComparing(one -> one.dist().ordinal())
                        .thenComparing(one -> one.kind().ordinal())
                        .thenComparing(OssItem::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private static List<OssItem> readResource() {
        try (InputStream stream = OssCatalog.class.getResourceAsStream(RESOURCE)) {
            if (stream == null) {
                throw new IllegalStateException("오픈소스 목록이 없습니다: " + RESOURCE);
            }
            return JsonMapper.builder().build()
                    .readValue(stream, new TypeReference<List<OssItem>>() { });
        } catch (IOException broken) {
            throw new IllegalStateException("오픈소스 목록을 읽지 못했습니다: " + RESOURCE, broken);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
