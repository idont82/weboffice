package weboffice.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
class UserAccountServiceTest {

    @Autowired
    private UserAccountService accounts;

    @Autowired
    private UserAccountMapper mapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private Authenticator authenticator;

    @BeforeEach
    void 사용자_표를_비운다() {
        jdbcTemplate.update("DELETE FROM wo_user_invite");
        jdbcTemplate.update("DELETE FROM wo_user_account");
    }

    @Test
    void 등록하면_초대가_나오고_아직_로그인은_못_한다() {
        UserAccountService.Invited invited =
                accounts.register("Hong@Example.COM", "홍길동", "USER", "admin1");

        assertThat(invited.email()).as("이메일은 소문자로 맞춘다").isEqualTo("hong@example.com");
        assertThat(invited.token()).isNotBlank();

        UserAccountRow found = mapper.selectByEmail("hong@example.com");
        assertThat(found.displayName()).isEqualTo("홍길동");
        assertThat(found.canSignIn()).as("비밀번호를 정하기 전에는 못 들어온다").isFalse();
    }

    @Test
    void 이름을_안_주면_이메일_앞부분을_쓴다() {
        accounts.register("hong@example.com", "  ", "USER", "admin1");

        assertThat(mapper.selectByEmail("hong@example.com").displayName()).isEqualTo("hong");
    }

    /** 관리자가 같은 주소를 두 번 적는 가장 흔한 까닭은 "링크를 잃었다" 다. */
    @Test
    void 같은_이메일을_다시_등록하면_계정은_하나고_초대만_새로_난다() {
        UserAccountService.Invited first =
                accounts.register("hong@example.com", "홍길동", "USER", "admin1");
        UserAccountService.Invited second =
                accounts.register("hong@example.com", "홍길동", "USER", "admin1");

        assertThat(second.userKey()).isEqualTo(first.userKey());
        assertThat(second.token()).isNotEqualTo(first.token());
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM wo_user_account", Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void 새_초대를_내면_앞선_초대는_죽는다() {
        UserAccountService.Invited first =
                accounts.register("hong@example.com", "홍길동", "USER", "admin1");
        accounts.invite(first.userKey(), "admin1");

        assertThat(accounts.findInvited(first.token()))
                .as("샌 것 같아 다시 내는 경우가 있다. 앞선 것이 살아 있으면 뜻이 없다")
                .isNull();
    }

    /**
     * 앞선 초대가 시각 비교에 매여 살아남지 않는지 본다.
     *
     * <p>처음에는 유효기간을 지금으로 당겨 죽였는데, EXPIRES_AT 이
     * {@code timestamp(3)} 이라 나노초가 올림되면 나중에 비교하는 now 보다 커져
     * 초대가 살아남았다 - 같은 밀리초 안에 두 번 부르면 통과하고 아니면 실패하는
     * 깜빡임이었다(2026-10-22 실측). 연달아 여러 번 불러 그것을 잡는다.
     */
    @Test
    void 연달아_여러_번_초대해도_앞선_것들은_모두_죽는다() {
        UserAccountService.Invited first =
                accounts.register("hong@example.com", "홍길동", "USER", "admin1");

        List<String> dead = new ArrayList<>();
        dead.add(first.token());
        for (int at = 0; at < 5; at++) {
            dead.add(accounts.invite(first.userKey(), "admin1").token());
        }
        String alive = dead.remove(dead.size() - 1);

        for (String token : dead) {
            assertThat(accounts.findInvited(token)).as("죽어야 한다: %s", token).isNull();
        }
        assertThat(accounts.findInvited(alive)).as("마지막 것은 살아 있어야 한다").isNotNull();
    }

    @Test
    void 초대로_비밀번호를_정하면_그_뒤로_로그인한다() {
        UserAccountService.Invited invited =
                accounts.register("hong@example.com", "홍길동", "USER", "admin1");

        accounts.acceptInvite(invited.token(), "충분히긴비밀번호");

        assertThat(accounts.signIn("hong@example.com", "충분히긴비밀번호")).isNotNull();
        assertThat(accounts.signIn("hong@example.com", "틀린비밀번호다")).isNull();
    }

    @Test
    void 쓴_초대는_다시_못_쓴다() {
        UserAccountService.Invited invited =
                accounts.register("hong@example.com", "홍길동", "USER", "admin1");
        accounts.acceptInvite(invited.token(), "충분히긴비밀번호");

        assertThatThrownBy(() -> accounts.acceptInvite(invited.token(), "다른비밀번호다"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("이미 사용");
    }

    @Test
    void 짧은_비밀번호는_받지_않는다() {
        UserAccountService.Invited invited =
                accounts.register("hong@example.com", "홍길동", "USER", "admin1");

        assertThatThrownBy(() -> accounts.acceptInvite(invited.token(), "짧다"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("8자");

        assertThat(accounts.findInvited(invited.token()))
                .as("실패했으면 초대는 살아 있어야 한다").isNotNull();
    }

    /**
     * String.length() 로 세면 이모지 한 자가 둘로 센다. 눈에 보이는 글자로
     * 세야 "여덟 자" 가 사람이 아는 여덟 자다.
     */
    @Test
    void 글자_수는_눈에_보이는_대로_센다() {
        UserAccountService.Invited invited =
                accounts.register("hong@example.com", "홍길동", "USER", "admin1");

        // 이모지 일곱 자. length() 로는 14 라 통과해 버린다.
        assertThatThrownBy(() -> accounts.acceptInvite(invited.token(), "🙂🙃😀😁😂😃😄"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("8자");
    }

    @Test
    void 표에는_비밀번호_평문이_없다() {
        UserAccountService.Invited invited =
                accounts.register("hong@example.com", "홍길동", "USER", "admin1");
        accounts.acceptInvite(invited.token(), "충분히긴비밀번호");

        String hash = jdbcTemplate.queryForObject(
                "SELECT PASSWORD_HASH FROM wo_user_account WHERE EMAIL = 'hong@example.com'",
                String.class);
        assertThat(hash).doesNotContain("충분히긴비밀번호").startsWith("$2");
    }

    /** 표가 새어도 남의 계정을 가져갈 수 없어야 한다. */
    @Test
    void 초대_표에는_토큰_평문이_없다() {
        UserAccountService.Invited invited =
                accounts.register("hong@example.com", "홍길동", "USER", "admin1");

        String stored = jdbcTemplate.queryForObject(
                "SELECT TOKEN_HASH FROM wo_user_invite", String.class);
        assertThat(stored).isNotEqualTo(invited.token())
                .isEqualTo(InviteToken.hash(invited.token()));
    }

    @Test
    void 막힌_사람은_로그인하지_못한다() {
        UserAccountService.Invited invited =
                accounts.register("hong@example.com", "홍길동", "USER", "admin1");
        accounts.acceptInvite(invited.token(), "충분히긴비밀번호");

        accounts.setDisabled(invited.userKey(), true, "admin1");

        assertThat(accounts.signIn("hong@example.com", "충분히긴비밀번호")).isNull();
    }

    @Test
    void 막았다_풀면_다시_로그인한다() {
        UserAccountService.Invited invited =
                accounts.register("hong@example.com", "홍길동", "USER", "admin1");
        accounts.acceptInvite(invited.token(), "충분히긴비밀번호");
        accounts.setDisabled(invited.userKey(), true, "admin1");

        accounts.setDisabled(invited.userKey(), false, "admin1");

        assertThat(accounts.signIn("hong@example.com", "충분히긴비밀번호")).isNotNull();
    }

    @Test
    void 없는_이메일로는_로그인하지_못한다() {
        assertThat(accounts.signIn("nobody@example.com", "아무비밀번호나")).isNull();
    }

    @Test
    void 이메일_모양이_아니면_등록하지_않는다() {
        for (String bad : new String[] {"", "  ", "hong", "hong@", "@example.com", "a@b"}) {
            assertThatThrownBy(() -> accounts.register(bad, "홍길동", "USER", "admin1"))
                    .as("받으면 안 된다: %s", bad)
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void 역할은_ADMIN_과_USER_둘뿐이다() {
        accounts.register("a@example.com", "가", "ADMIN", "admin1");
        accounts.register("b@example.com", "나", "뭐든지", "admin1");

        assertThat(mapper.selectByEmail("a@example.com").isAdmin()).isTrue();
        assertThat(mapper.selectByEmail("b@example.com").roleCode()).isEqualTo("USER");
    }

    @Test
    void 첫_관리자는_한_번만_만든다() {
        UserAccountService.Invited first = accounts.ensureAdmin("admin@example.com");

        assertThat(first).isNotNull();
        assertThat(mapper.selectByEmail("admin@example.com").isAdmin()).isTrue();
        assertThat(accounts.ensureAdmin("admin@example.com"))
                .as("다시 뜰 때마다 초대가 새로 나면 앞서 받은 링크가 매번 죽는다")
                .isNull();
    }

    @Test
    void 이름이나_이메일로_찾는다() {
        UserAccountService.Invited invited =
                accounts.register("hong@example.com", "홍길동", "USER", "admin1");
        accounts.acceptInvite(invited.token(), "충분히긴비밀번호");

        assertThat(mapper.searchByName("홍길", 20)).hasSize(1);
        assertThat(mapper.searchByName("HONG@", 20)).hasSize(1);
        assertThat(mapper.searchByName("없는사람", 20)).isEmpty();
    }

    @Test
    void 막힌_사람은_이름으로_찾히지_않는다() {
        UserAccountService.Invited invited =
                accounts.register("hong@example.com", "홍길동", "USER", "admin1");
        accounts.setDisabled(invited.userKey(), true, "admin1");

        assertThat(mapper.searchByName("홍길", 20)).isEmpty();
        assertThat(mapper.selectByKey(invited.userKey()))
                .as("그가 만든 문서의 소유자 이름은 그대로 보여야 한다").isNotNull();
    }
}
