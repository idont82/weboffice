package weboffice.doc;

import java.util.ArrayList;
import java.util.List;

/**
 * 글 문서 두 판을 견준다.
 *
 * <p>문단 번호로 견주면 위에 한 줄만 넣어도 아래 문단이 통째로 "바뀐 것" 이 된다 -
 * 번호가 하나씩 밀리기 때문이다(2026-08-20 지적: 두 줄 넣었는데 131군데). 그래서
 * 번호가 아니라 <b>글 내용</b>을 견줘 정말 늘고 준 줄만 찾는다.
 *
 * <p>가장 긴 공통 부분(LCS)을 잡고 그 바깥만 바뀐 것으로 본다. 문서 한 편이 수백
 * 줄이라 셈이 커지지 않는다(줄 수의 곱).
 */
public final class DocDiff {

    /**
     * 바뀐 줄 하나.
     *
     * @param paragraphNo 새 판에서의 자리. 지운 줄은 옛 판에서의 자리다.
     */
    public record Change(int paragraphNo, String before, String after) {
    }

    /** 견줄 줄 수의 위 끝. 이보다 긴 문서는 앞부분만 견준다 - 셈이 줄 수의 곱이다. */
    public static final int MOST_LINES = 3000;

    private DocDiff() {
    }

    public static List<Change> between(List<String> before, List<String> after) {
        List<String> left = cut(before);
        List<String> right = cut(after);
        int[][] common = commonLengths(left, right);

        List<Change> changes = new ArrayList<>();
        int at = 0;
        int to = 0;
        while (at < left.size() || to < right.size()) {
            if (at < left.size() && to < right.size()
                    && java.util.Objects.equals(left.get(at), right.get(to))) {
                at++;
                to++;
                continue;
            }
            // 어긋난 구간을 통째로 모은다. 지운 줄과 넣은 줄을 짝지어야 "고친 줄" 이
            // 두 줄로 갈라지지 않는다.
            int spot = to;
            List<String> gone = new ArrayList<>();
            List<String> added = new ArrayList<>();
            while (at < left.size() || to < right.size()) {
                if (at < left.size() && to < right.size()
                        && java.util.Objects.equals(left.get(at), right.get(to))) {
                    break;
                }
                if (to < right.size()
                        && (at == left.size() || common[at][to + 1] >= common[at + 1][to])) {
                    added.add(right.get(to));
                    to++;
                } else {
                    gone.add(left.get(at));
                    at++;
                }
            }
            for (int step = 0; step < Math.max(gone.size(), added.size()); step++) {
                changes.add(new Change(spot + step,
                        step < gone.size() ? gone.get(step) : null,
                        step < added.size() ? added.get(step) : null));
            }
        }
        return changes;
    }

    /** 뒤에서부터 잰 공통 길이 표. LCS 를 되짚는 데 쓴다. */
    private static int[][] commonLengths(List<String> left, List<String> right) {
        int[][] common = new int[left.size() + 1][right.size() + 1];
        for (int at = left.size() - 1; at >= 0; at--) {
            for (int to = right.size() - 1; to >= 0; to--) {
                common[at][to] = java.util.Objects.equals(left.get(at), right.get(to))
                        ? common[at + 1][to + 1] + 1
                        : Math.max(common[at + 1][to], common[at][to + 1]);
            }
        }
        return common;
    }

    private static List<String> cut(List<String> lines) {
        return lines.size() <= MOST_LINES ? lines : lines.subList(0, MOST_LINES);
    }
}
