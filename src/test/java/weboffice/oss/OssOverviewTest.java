package weboffice.oss;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 목록 위에 얹는 요약. */
class OssOverviewTest {

    private static OssItem item(String name, String license, OssItem.Kind kind,
                                OssItem.Dist dist) {
        return new OssItem(List.of(), name, null, license, "https://x", OssItem.Side.BACKEND,
                kind, dist, "쓰임");
    }

    @Test
    void 배포물에_드는_것과_개발_전용을_따로_센다() {
        OssOverview overview = OssOverview.of(List.of(
                item("가", "Apache-2.0", OssItem.Kind.OSS, OssItem.Dist.SHIPPED),
                item("나", "MIT", OssItem.Kind.OSS, OssItem.Dist.DEV_ONLY)));

        assertThat(overview.shipped()).isEqualTo(1);
        assertThat(overview.devOnly()).isEqualTo(1);
    }

    @Test
    void 오픈소스가_아닌_것을_따로_센다() {
        // 라이선스 점검에서 먼저 묻는 것이 이 숫자다.
        OssOverview overview = OssOverview.of(List.of(
                item("가", "Apache-2.0", OssItem.Kind.OSS, OssItem.Dist.SHIPPED),
                item("나", "상용", OssItem.Kind.COMMERCIAL, OssItem.Dist.SHIPPED),
                item("다", "사내", OssItem.Kind.INTERNAL, OssItem.Dist.SHIPPED)));

        assertThat(overview.open()).isEqualTo(1);
        assertThat(overview.closed()).isEqualTo(2);
    }

    @Test
    void 라이선스별로_많은_것부터_센다() {
        OssOverview overview = OssOverview.of(List.of(
                item("가", "MIT", OssItem.Kind.OSS, OssItem.Dist.SHIPPED),
                item("나", "Apache-2.0", OssItem.Kind.OSS, OssItem.Dist.SHIPPED),
                item("다", "Apache-2.0", OssItem.Kind.OSS, OssItem.Dist.SHIPPED)));

        assertThat(overview.licenses()).extracting(OssOverview.LicenseCount::license)
                .containsExactly("Apache-2.0", "MIT");
        assertThat(overview.licenses().get(0).count()).isEqualTo(2);
    }

    @Test
    void 같은_수면_이름_차례로_센다() {
        // 차례가 흔들리면 화면이 새로 고칠 때마다 줄이 뒤바뀐다.
        OssOverview overview = OssOverview.of(List.of(
                item("가", "MIT", OssItem.Kind.OSS, OssItem.Dist.SHIPPED),
                item("나", "Apache-2.0", OssItem.Kind.OSS, OssItem.Dist.SHIPPED)));

        assertThat(overview.licenses()).extracting(OssOverview.LicenseCount::license)
                .containsExactly("Apache-2.0", "MIT");
    }

    @Test
    void 아무것도_없으면_영이다() {
        OssOverview overview = OssOverview.of(List.of());

        assertThat(overview.shipped()).isZero();
        assertThat(overview.licenses()).isEmpty();
    }
}
