package weboffice.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserContextTest {

    private static final LoginUser USER =
            new LoginUser("hong", "12345", "홍길동", "과장", "D100", "전산팀", "ko");

    @Test
    void 세션에_담고_다시_꺼낸다() {
        MockHttpSession session = new MockHttpSession();

        UserContext.set(session, USER);

        assertThat(UserContext.find(session)).isEqualTo(USER);
    }

    @Test
    void 담은_적_없으면_null_이다() {
        assertThat(UserContext.find(new MockHttpSession())).isNull();
    }

    @Test
    void 감사_사번은_세션의_사번이다() {
        MockHttpSession session = new MockHttpSession();
        UserContext.set(session, USER);
        UserContext.bindToRequest(USER);
        try {
            assertThat(UserContext.currentAuditUser().userKey()).isEqualTo("12345");
        } finally {
            UserContext.clearRequest();
        }
    }

    @Test
    void 요청에_바인딩되지_않았으면_예외다() {
        UserContext.clearRequest();

        assertThatThrownBy(UserContext::current)
                .isInstanceOf(UnauthenticatedException.class);
    }

    @Test
    void 사번이_없는_사용자는_감사_사번을_만들_수_없다() {
        UserContext.bindToRequest(new LoginUser("hong", null, "홍길동", "과장", "D100", "전산팀", "ko"));
        try {
            assertThatThrownBy(UserContext::currentAuditUser)
                    .as("사번 없이 감사 컬럼을 찍으면 누가 바꿨는지 알 수 없는 행이 생긴다")
                    .isInstanceOf(IllegalArgumentException.class);
        } finally {
            UserContext.clearRequest();
        }
    }
}
