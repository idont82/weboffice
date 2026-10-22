package weboffice.view;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * 보기 토큰.
 *
 * <p>DB 에는 해시만 둔다. 평문으로 두면 관리자 화면·백업·로그에 남는 것이 그대로
 * 열쇠가 된다(설계 4절).
 */
public final class ViewToken {

    /** 16바이트 = 32자 hex. Random 을 쓰면 예측 가능하다. */
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 16;

    private ViewToken() {
    }

    public static String make() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    public static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 이 없는 JVM 은 없다", e);
        }
    }
}
