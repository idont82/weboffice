package weboffice.view;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ViewTokenTest {

    @Test
    void 토큰은_서른두_자다() {
        assertThat(ViewToken.make()).hasSize(32).matches("[0-9a-f]{32}");
    }

    @Test
    void 부를_때마다_다르다() {
        assertThat(ViewToken.make()).isNotEqualTo(ViewToken.make());
    }

    @Test
    void 해시는_같은_토큰에_같은_값이다() {
        String token = ViewToken.make();

        assertThat(ViewToken.hash(token)).isEqualTo(ViewToken.hash(token));
        assertThat(ViewToken.hash(token)).hasSize(64);
    }

    @Test
    void 해시에서_토큰이_보이지_않는다() {
        // 해시를 DB 에 두는 뜻이 이것이다. 관리자 화면에 새어도 열쇠가 되지 않는다.
        String token = ViewToken.make();

        assertThat(ViewToken.hash(token)).doesNotContain(token);
    }
}
