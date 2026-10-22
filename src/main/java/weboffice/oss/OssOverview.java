package weboffice.oss;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 목록 위에 얹는 요약.
 *
 * <p>라이선스 점검에서 먼저 묻는 것이 숫자다 - "오픈소스가 몇 개인가, 그중 배포물에
 * 드는 것이 몇 개인가, 어떤 라이선스가 섞여 있는가". 표를 끝까지 세어 가며 답하지
 * 않게 미리 센다.
 *
 * @param open 오픈소스 수
 * @param closed 오픈소스가 아닌 것(상용·사내) 수
 * @param shipped 배포물에 들어가는 것
 * @param devOnly 빌드·시험에서만 쓰는 것
 */
public record OssOverview(int open, int closed, int shipped, int devOnly,
                          List<LicenseCount> licenses) {

    /** 라이선스 한 갈래와 그 수. */
    public record LicenseCount(String license, int count) { }

    public static OssOverview of(List<OssItem> items) {
        int open = 0;
        int closed = 0;
        int shipped = 0;
        int devOnly = 0;
        Map<String, Integer> byLicense = new LinkedHashMap<>();

        for (OssItem item : items) {
            if (item.kind() == OssItem.Kind.OSS) {
                open += 1;
            } else {
                closed += 1;
            }
            if (item.dist() == OssItem.Dist.SHIPPED) {
                shipped += 1;
            } else {
                devOnly += 1;
            }
            byLicense.merge(item.license(), 1, Integer::sum);
        }

        // 많은 것부터, 같으면 이름 차례로. 차례가 흔들리면 새로 고칠 때마다 줄이 뒤바뀐다.
        List<LicenseCount> counts = byLicense.entrySet().stream()
                .map(entry -> new LicenseCount(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingInt(LicenseCount::count).reversed()
                        .thenComparing(LicenseCount::license))
                .toList();

        return new OssOverview(open, closed, shipped, devOnly, counts);
    }
}
