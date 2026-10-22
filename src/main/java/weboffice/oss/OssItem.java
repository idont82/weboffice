package weboffice.oss;

import java.util.List;

/**
 * 쓰고 있는 바깥 라이브러리 한 줄.
 *
 * @param coords 선언에 적힌 좌표. 자바는 {@code groupId:artifactId}, 프런트는
 *               {@code npm:이름} 이다. 한 항목이 여러 좌표를 가질 수 있다 - Univer 처럼
 *               한 묶음으로 쓰는 패키지를 스물여섯 줄로 늘어놓으면 사람이 못 읽는다.
 *               비어 있으면 선언에 없는 것이다(자바 런타임, node, npm 같은 것)
 * @param version 선언에 적은 버전. {@code null} 이면 상위(parent)가 정하거나 좌표마다
 *                버전이 다르다는 뜻이다
 * @param license SPDX 식별자를 쓴다. 두 갈래 중 고르는 것은 {@code A OR B} 로 적는다
 * @param url 라이선스 전문을 확인할 자리. 고지문이 이것을 싣는다
 * @param dist 배포물에 들어가는가. 시험·빌드만 쓰는 것은 고지 대상이 아니다
 * @param what 무엇에 쓰는가. 관리자에게는 라이선스보다 이것이 먼저다
 */
public record OssItem(List<String> coords, String name, String version, String license,
                      String url, Side side, Kind kind, Dist dist, String what) {

    /** 어느 쪽에서 쓰는가. */
    public enum Side {
        BACKEND, FRONTEND, BUILD
    }

    /**
     * 무슨 물건인가.
     *
     * <p>오픈소스가 아닌 것을 같은 표에 섞어 두는 이유: "우리가 쓰는 바깥 것" 을 한
     * 자리에서 봐야 빠진 것을 안다. 섞되 갈래로 가른다.
     */
    public enum Kind {
        /** 오픈소스. */
        OSS,
        /** 돈 내고 쓰는 것. */
        COMMERCIAL,
        /** 사내에서 만든 것. */
        INTERNAL
    }

    /** 배포물에 들어가는가. */
    public enum Dist {
        /** 운영에 함께 나간다. 고지 대상이다. */
        SHIPPED,
        /** 빌드·시험에서만 쓴다. */
        DEV_ONLY
    }

    public OssItem {
        coords = coords == null ? List.of() : List.copyOf(coords);
    }

    /** 고지문에 실어야 하는가. */
    public boolean needsNotice() {
        return kind == Kind.OSS && dist == Dist.SHIPPED;
    }
}
