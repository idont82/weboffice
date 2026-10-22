package weboffice.doc;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 글 문서 두 판을 견주는 규칙.
 *
 * <p>문단 번호로 견주면 위에 한 줄만 넣어도 아래 문단이 통째로 "바뀐 것" 이 된다
 * (2026-08-20 지적: 두 줄 넣었는데 131군데가 바뀌었다고 나왔다). 번호가 아니라
 * 글 내용을 견줘 <b>정말 늘고 준 줄</b>만 찾는다.
 */
class DocDiffTest {

    @Test
    void 앞에_넣은_줄만_찾는다() {
        List<DocDiff.Change> changes = DocDiff.between(
                List.of("첫째", "둘째", "셋째"),
                List.of("새 줄", "첫째", "둘째", "셋째"));

        assertThat(changes).hasSize(1);
        assertThat(changes.get(0).before()).isNull();
        assertThat(changes.get(0).after()).isEqualTo("새 줄");
    }

    @Test
    void 지운_줄을_찾는다() {
        List<DocDiff.Change> changes = DocDiff.between(
                List.of("첫째", "둘째", "셋째"),
                List.of("첫째", "셋째"));

        assertThat(changes).hasSize(1);
        assertThat(changes.get(0).before()).isEqualTo("둘째");
        assertThat(changes.get(0).after()).isNull();
    }

    @Test
    void 고친_줄은_한_줄로_묶는다() {
        // 지우고 넣은 것으로 보이면 같은 자리가 두 줄로 나와 읽기 나쁘다.
        List<DocDiff.Change> changes = DocDiff.between(
                List.of("첫째", "둘째", "셋째"),
                List.of("첫째", "둘째를 고침", "셋째"));

        assertThat(changes).hasSize(1);
        assertThat(changes.get(0).before()).isEqualTo("둘째");
        assertThat(changes.get(0).after()).isEqualTo("둘째를 고침");
    }

    @Test
    void 그대로면_아무것도_없다() {
        assertThat(DocDiff.between(List.of("첫째", "둘째"), List.of("첫째", "둘째"))).isEmpty();
    }

    @Test
    void 자리_번호는_새_판_기준이다() {
        // 되찾을 자리를 사람이 짚을 수 있어야 한다. 새 판에 없는 줄(지운 줄)은
        // 옛 판의 자리로 적는다.
        List<DocDiff.Change> changes = DocDiff.between(
                List.of("첫째", "둘째", "셋째"),
                List.of("첫째", "새 줄", "둘째", "셋째"));

        assertThat(changes.get(0).paragraphNo()).isEqualTo(1);
    }

    @Test
    void 앞뒤가_통째로_바뀌어도_센다() {
        List<DocDiff.Change> changes = DocDiff.between(List.of("가", "나"), List.of("다", "라"));

        assertThat(changes).hasSize(2);
    }
}
