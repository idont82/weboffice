package weboffice.security;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import jakarta.servlet.http.HttpSession;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 세션에 담기는 것은 전부 직렬화돼야 한다.
 *
 * <p>{@code server.servlet.session.persistent=true} 로 두면 톰캣이 재기동할 때
 * 세션을 파일로 쓰고 다시 읽는다. 담긴 것 중 하나라도 {@code Serializable} 이
 * 아니면 <b>그 세션이 통째로 버려진다</b> - 로그인이 풀린 채로 조용히 돌아간다.
 * 그것을 여기서 잡는다.
 */
class SessionSerializationTest {

    @Test
    void 로그인_사용자는_직렬화된다() throws Exception {
        LoginUser user = new LoginUser("admin1", "20502534", "홍길동", "책임",
                "D100", "전산팀", "ko");

        LoginUser restored = roundTrip(user);

        assertThat(restored).isEqualTo(user);
    }

    @Test
    void 공개_링크_허가도_직렬화된다() throws Exception {
        // UserContext.grantShare 가 HashMap 을 새로 만들어 넣는다. 그 형태 그대로 본다.
        // 값은 들어올 때 쓴 토큰이다 - 재발급을 알아채려면 그것까지 살아남아야 한다.
        HashMap<Long, String> granted = new HashMap<>(Map.of(1L, "tok-1", 2L, "tok-2"));

        assertThat(roundTrip(granted)).containsEntry(1L, "tok-1").containsEntry(2L, "tok-2");
    }

    @Test
    void 세션에_담기는_것이_전부_직렬화된다() throws Exception {
        // 실제로 담는 경로를 그대로 태운다. 새 속성이 늘면 여기서 걸린다.
        HttpSession session = new MockHttpSession();
        UserContext.set(session, new LoginUser("admin1", "20502534", "홍길동", "책임",
                "D1", "전산팀", "ko"));
        UserContext.grantShare(session, 7L, "tok-7");

        for (String name : java.util.Collections.list(session.getAttributeNames())) {
            Object value = session.getAttribute(name);
            assertThat(value)
                    .as("세션 속성 %s 가 직렬화되지 않으면 재기동 때 세션이 통째로 버려진다", name)
                    .isInstanceOf(Serializable.class);
            roundTrip((Serializable) value);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends Serializable> T roundTrip(T value) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(value);
        }
        try (ObjectInputStream in =
                     new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return (T) in.readObject();
        }
    }
}
