package weboffice.view;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 부르는 시스템 가리기.
 *
 * <p>주소 대역이 첫 관문이다. 사내망 대역만 부를 수 있고, 그 밖에서 온 것은
 * 이름도 못 얻는다(설계 3.3절).
 *
 * <p><b>열쇠는 적어 둔 시스템에만 묻는다.</b> 열쇠를 나눠 주고 갈아 끼우는 값이
 * 사내망 대역으로 조이는 것보다 크지 않다는 판단이다(2026-08-20 결정). 대신
 * 이력의 "어느 시스템이 보냈나" 가 대역 이름까지만 좁혀진다 - 같은 대역의 다른
 * 프로그램이 불러도 같은 이름으로 남는다. 더 좁혀야 할 시스템에는 열쇠를 적어라.
 *
 * <p>판정을 {@link #byAddress} 와 {@link #sameKey} 로 갈라 둔다. 부르는 쪽에
 * "주소가 막혔다(403)" 와 "열쇠가 틀렸다(401)" 를 나눠 돌려주려면 어느 쪽에서
 * 걸렸는지 알아야 한다 - 부르는 쪽이 모두 우리 시스템이라 진단이 되는 편이 낫다.
 */
public final class CallerGate {

    /**
     * IPv4 사상(mapped) 주소 접두사.
     *
     * <p>톰캣이 이중 스택으로 뜨면 {@code getRemoteAddr()} 이 사내 주소를
     * {@code ::ffff:192.0.2.92} 모양으로 준다. 벗기지 않으면 대역 안에서 온
     * 정당한 요청이 막힌다.
     */
    private static final String V4_MAPPED = "::ffff:";

    /** 숫자로 못 바꾼 주소. 예외 대신 이 값으로 다뤄 "안 맞음" 으로 끝낸다. */
    private static final long UNREADABLE = -1L;

    private CallerGate() {
    }

    /** @return 가려낸 시스템. 주소가 대역 밖이거나 열쇠가 틀리면 null */
    public static ViewCaller of(ViewProperties properties, String apiKey, String clientIp) {
        ViewCaller caller = byAddress(properties, clientIp);
        return caller != null && keyMatches(caller, apiKey) ? caller : null;
    }

    /**
     * @return 주소로 가려낸 시스템. 못 가리면 null
     *
     * <p>주소를 아예 안 적은 시스템은 여기서 안 걸린다. 그런 항목은 열쇠로만
     * 여는 옛 방식이라 {@link #byKey} 가 따로 본다.
     */
    public static ViewCaller byAddress(ViewProperties properties, String clientIp) {
        for (ViewCaller caller : properties.callers()) {
            if (!caller.addresses().isEmpty() && allows(caller, clientIp)) {
                return caller;
            }
        }
        return null;
    }

    /**
     * 그 시스템이 열쇠를 요구하는가, 요구한다면 맞는가.
     *
     * <p>설정에 열쇠를 안 적은 시스템은 주소만으로 통과한다.
     */
    public static boolean keyMatches(ViewCaller caller, String apiKey) {
        if (caller.key() == null || caller.key().isBlank()) {
            return true;
        }
        return apiKey != null && sameKey(apiKey, caller.key());
    }

    /**
     * 주소를 안 적고 <b>열쇠로만</b> 여는 시스템.
     *
     * <p>주소를 적어 둔 시스템은 여기서 안 걸린다 - 걸리게 두면 대역 밖에서도 열쇠만
     * 있으면 통과해 주소 관문이 무너진다.
     */
    public static ViewCaller byKeyOnly(ViewProperties properties, String apiKey) {
        ViewCaller caller = byKey(properties, apiKey);
        return caller != null && caller.addresses().isEmpty() ? caller : null;
    }

    /** @return 키로만 가려낸 시스템. 주소는 안 본다. 못 가리면 null */
    public static ViewCaller byKey(ViewProperties properties, String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return null;
        }
        for (ViewCaller caller : properties.callers()) {
            if (sameKey(apiKey, caller.key())) {
                return caller;
            }
        }
        return null;
    }

    /**
     * 키 비교.
     *
     * <p>{@code equals} 는 앞에서 어긋나는 순간 끝나 맞은 글자 수만큼 오래 걸린다.
     * 그 시간차를 재면 한 글자씩 맞혀 갈 수 있다 - 길이가 같으면 늘 끝까지 보는
     * {@link MessageDigest#isEqual} 로 잰다.
     */
    private static boolean sameKey(String apiKey, String configured) {
        if (configured == null) {
            return false;
        }
        return MessageDigest.isEqual(apiKey.getBytes(StandardCharsets.UTF_8),
                configured.getBytes(StandardCharsets.UTF_8));
    }

    /** 주소 목록이 비면 가리지 않는다 - 개발 환경에서 대역을 적기 어렵다. */
    public static boolean allows(ViewCaller caller, String clientIp) {
        if (caller.addresses().isEmpty()) {
            return true;
        }
        long asked = toLong(clientIp);
        if (asked == UNREADABLE) {
            return false;
        }
        for (String range : caller.addresses()) {
            if (inRange(range.trim(), asked)) {
                return true;
            }
        }
        return false;
    }

    /** IPv4 CIDR 또는 정확한 주소. 그 밖의 모양은 안 맞는 것으로 본다. */
    private static boolean inRange(String range, long asked) {
        int slash = range.indexOf('/');
        if (slash < 0) {
            long one = toLong(range);
            return one != UNREADABLE && one == asked;
        }
        long base = toLong(range.substring(0, slash));
        int bits = number(range.substring(slash + 1));
        // 설정에 오타가 나면 막는 쪽으로 넘어진다 - 못 읽는 대역이 문을 열면 안 된다.
        if (base == UNREADABLE || bits < 0 || bits > 32) {
            return false;
        }
        long mask = bits == 0 ? 0 : -1L << (32 - bits);
        return (base & mask) == (asked & mask);
    }

    /**
     * 점 넷짜리 IPv4 만 숫자로 바꾼다.
     *
     * <p>못 읽는 모양은 예외 대신 {@link #UNREADABLE} 이다. 여기서 예외가 나면
     * 주소 판정이 아니라 500 이 나가고 이력도 안 남는다 - 주소를 못 읽는 것은
     * "그 대역이 아니다" 로 끝날 일이다.
     */
    private static long toLong(String ip) {
        if (ip == null) {
            return UNREADABLE;
        }
        String plain = ip.trim();
        if (plain.regionMatches(true, 0, V4_MAPPED, 0, V4_MAPPED.length())) {
            plain = plain.substring(V4_MAPPED.length());
        }
        String[] pieces = plain.split("\\.");
        if (pieces.length != 4) {
            return UNREADABLE;
        }
        long value = 0;
        for (String piece : pieces) {
            int part = number(piece);
            if (part < 0 || part > 255) {
                return UNREADABLE;
            }
            value = (value << 8) + part;
        }
        return value;
    }

    /** @return 읽은 숫자. 숫자가 아니면 -1 */
    private static int number(String text) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
