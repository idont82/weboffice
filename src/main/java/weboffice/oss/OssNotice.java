package weboffice.oss;

import java.util.List;

/**
 * 고지문.
 *
 * <p>Apache-2.0·MIT 같은 라이선스는 "이 소프트웨어를 썼다" 를 알리게 한다. 배포할
 * 때 붙일 글을 손으로 다시 적지 않게 목록에서 만든다.
 *
 * <p>싣는 것은 <b>배포물에 들어가는 오픈소스</b>만이다. 시험·빌드 도구는 고객에게
 * 가지 않아 고지 대상이 아니고, 상용·사내 것은 계약이 따로 있다.
 */
public final class OssNotice {

    private OssNotice() {
    }

    public static String textOf(List<OssItem> items) {
        StringBuilder text = new StringBuilder();
        text.append("Web Office\n");
        text.append("AGPL-3.0-or-later. 소스: https://github.com/idont82/weboffice\n");
        text.append("\n");
        text.append("이 제품은 아래 오픈소스 소프트웨어를 포함합니다.\n");
        text.append("각 라이선스 전문은 적힌 주소에서 볼 수 있습니다.\n");

        for (OssItem item : items) {
            if (!item.needsNotice()) {
                continue;
            }
            text.append("\n");
            text.append("- ").append(item.name());
            if (item.version() != null) {
                text.append(' ').append(item.version());
            }
            text.append('\n');
            text.append("  라이선스: ").append(item.license()).append('\n');
            if (item.url() != null && !item.url().isBlank()) {
                text.append("  ").append(item.url()).append('\n');
            }
        }
        return text.toString();
    }
}
