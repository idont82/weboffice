package weboffice.oss;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 고지문.
 *
 * <p>배포물에 들어가는 오픈소스만 싣는다. 시험·빌드에서만 쓰는 것은 고지 대상이
 * 아니고, 상용·사내 것은 계약이 따로 있다.
 */
class OssNoticeTest {

    private static OssItem item(String name, String license, OssItem.Kind kind,
                                OssItem.Dist dist) {
        return new OssItem(List.of(), name, "1.0", license, "https://" + name,
                OssItem.Side.BACKEND, kind, dist, "쓰임");
    }

    @Test
    void 배포물에_드는_오픈소스를_싣는다() {
        String notice = OssNotice.textOf(List.of(
                item("Univer", "Apache-2.0", OssItem.Kind.OSS, OssItem.Dist.SHIPPED)));

        assertThat(notice).contains("Univer").contains("1.0")
                .contains("Apache-2.0").contains("https://Univer");
    }

    @Test
    void 개발_전용은_안_싣는다() {
        String notice = OssNotice.textOf(List.of(
                item("Vitest", "MIT", OssItem.Kind.OSS, OssItem.Dist.DEV_ONLY)));

        assertThat(notice).doesNotContain("Vitest");
    }

    @Test
    void 상용과_사내_것은_안_싣는다() {
        String notice = OssNotice.textOf(List.of(
                item("어떤 상용 제품", "상용", OssItem.Kind.COMMERCIAL, OssItem.Dist.SHIPPED),
                item("어떤 사내 제품", "사내", OssItem.Kind.INTERNAL, OssItem.Dist.SHIPPED)));

        assertThat(notice).doesNotContain("어떤 상용 제품").doesNotContain("어떤 사내 제품");
    }

    @Test
    void 머리말에_제품_이름이_있다() {
        String notice = OssNotice.textOf(List.of());

        assertThat(notice).contains("Web Office");
    }

    @Test
    void 버전을_안_적은_것도_싣는다() {
        // 상위 pom 이 버전을 정하는 것들이다. 빠뜨리면 고지가 모자라다.
        OssItem noVersion = new OssItem(List.of(), "Spring Boot", null, "Apache-2.0",
                "https://spring.io", OssItem.Side.BACKEND, OssItem.Kind.OSS,
                OssItem.Dist.SHIPPED, "쓰임");

        assertThat(OssNotice.textOf(List.of(noVersion))).contains("Spring Boot");
    }
}
