package weboffice.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class InviteTokenTest {

    @Test
    void 만들면_url_에_실을_수_있는_글자만_쓴다() {
        String made = InviteToken.make();

        assertThat(made).matches("[A-Za-z0-9_-]{43}");
    }

    @Test
    void 매번_다른_값이_나온다() {
        assertThat(InviteToken.make()).isNotEqualTo(InviteToken.make());
    }

    /**
     * 표에는 해시만 적는다. 표가 새어도 남의 계정을 가져갈 수 없어야 한다 -
     * 평문 토큰은 발급한 그 순간 화면과 메일로만 나간다.
     */
    @Test
    void 해시는_원본을_되돌릴_수_없는_모양이다() {
        String made = InviteToken.make();

        String hashed = InviteToken.hash(made);

        assertThat(hashed).matches("[0-9a-f]{64}");
        assertThat(hashed).doesNotContain(made);
    }

    @Test
    void 같은_토큰은_같은_해시가_된다() {
        String made = InviteToken.make();

        assertThat(InviteToken.hash(made)).isEqualTo(InviteToken.hash(made));
    }

    @Test
    void 다른_토큰은_다른_해시가_된다() {
        assertThat(InviteToken.hash(InviteToken.make()))
                .isNotEqualTo(InviteToken.hash(InviteToken.make()));
    }
}
