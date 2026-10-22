package weboffice.common;

/**
 * 감사 컬럼(INPUT_USER/MOD_USER/DEL_USER)에 찍을 사번.
 *
 * <p>사번을 명시적 파라미터로 넘기기 위한 타입이다. ThreadLocal 이나 인터셉터로
 * 숨기면 매퍼 XML 만 보고 어느 사번이 찍히는지 알 수 없고, 테스트에서 다른
 * 사용자를 흉내내기도 어려워진다.
 */
public record AuditUser(String userKey) {

    public static AuditUser of(String userKey) {
        if (userKey == null || userKey.isBlank()) {
            throw new IllegalArgumentException("감사 컬럼에 찍을 사번이 없다");
        }
        return new AuditUser(userKey);
    }
}
