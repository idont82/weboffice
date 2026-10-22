package weboffice.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * 초대 토큰. 등록된 사람이 처음 비밀번호를 정하는 1회용 열쇠다.
 *
 * <p>표에는 <b>해시만</b> 적는다. 그 표가 새어도 남의 계정을 가져갈 수 없어야
 * 한다 - 평문 토큰은 발급한 그 순간 관리자 화면과 메일로만 나간다.
 *
 * <p>SHA-256 을 쓴다. 비밀번호 해시가 아니라 <b>무작위 32바이트</b>의 해시이므로
 * 느린 해시(BCrypt)가 필요 없다 - 사전 공격의 대상이 될 사전이 없다.
 */
public final class InviteToken {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    private InviteToken() {
    }

    /** 32바이트를 패딩 없는 base64url 로 적은 43글자. URL 에 그대로 실린다. */
    public static String make() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return ENCODER.encodeToString(bytes);
    }

    /** 표에 적을 모양. 64자리 16진수다. */
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
