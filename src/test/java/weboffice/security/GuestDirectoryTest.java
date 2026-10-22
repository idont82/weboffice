package weboffice.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class GuestDirectoryTest {

    private final GuestDirectory directory = new GuestDirectory();

    @Test
    void 열쇠에서_이름을_지어_돌려준다() {
        DirectoryUser found = directory.findByKey("abcdefghijklmnopqrstuv");

        assertThat(found.userKey()).isEqualTo("abcdefghijklmnopqrstuv");
        assertThat(found.displayName()).isEqualTo("손님-abcd");
    }

    /**
     * 인증기와 같은 규칙으로 지어야 한다. 어긋나면 자기 이름이 자리 표시에
     * 다르게 보인다.
     */
    @Test
    void 인증기가_짓는_이름과_같다() {
        String key = "ABCD1234efgh5678ijkl90";

        assertThat(directory.findByKey(key).displayName())
                .isEqualTo(GuestAuthenticator.nameOf(key));
    }

    @Test
    void 로그인_아이디도_열쇠와_같게_본다() {
        // 손님에게는 아이디와 열쇠가 하나다. 따로 둘 근거가 없다.
        assertThat(directory.findByUserId("abcdefghijklmnopqrstuv").userKey())
                .isEqualTo("abcdefghijklmnopqrstuv");
    }

    @Test
    void 조직_항목은_비어_있다() {
        DirectoryUser found = directory.findByKey("abcdefghijklmnopqrstuv");

        assertThat(found.rank()).isNull();
        assertThat(found.deptCode()).isNull();
        assertThat(found.deptName()).isNull();
    }

    /**
     * 손님을 이름으로 찾을 길은 없다. 어디에도 적어 두지 않기 때문이다.
     * 빈 목록이 맞다 - 오류로 다루면 공유 창이 멈춘다.
     */
    @Test
    void 이름으로는_찾지_못한다() {
        assertThat(directory.searchByName("손님", 20)).isEmpty();
    }

    @Test
    void 여러_열쇠를_한_번에_짓는다() {
        List<DirectoryUser> found = directory.findByKeys(
                List.of("aaaabbbbccccddddeeeeff", "1111222233334444555566"));

        assertThat(found).extracting(DirectoryUser::displayName)
                .containsExactly("손님-aaaa", "손님-1111");
    }

    @Test
    void 열쇠가_없거나_모양이_어긋나면_null_이다() {
        assertThat(directory.findByKey(null)).isNull();
        assertThat(directory.findByKey("")).isNull();
        assertThat(directory.findByKey("짧다")).isNull();
    }

    @Test
    void 어긋난_열쇠는_목록에서도_걸러진다() {
        List<DirectoryUser> found = directory.findByKeys(
                List.of("aaaabbbbccccddddeeeeff", "짧다", ""));

        assertThat(found).extracting(DirectoryUser::displayName).containsExactly("손님-aaaa");
    }
}
