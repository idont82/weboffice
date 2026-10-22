package weboffice.view;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CallerGateTest {

    private static ViewProperties settings(List<String> addresses) {
        ViewCaller caller = new ViewCaller();
        caller.setName("전자결재");
        caller.setKey("key-1234");
        caller.setAddresses(addresses);

        ViewProperties properties = new ViewProperties();
        properties.setCallers(List.of(caller));
        return properties;
    }

    @Test
    void 키가_맞고_주소가_대역_안이면_통과한다() {
        ViewCaller found = CallerGate.of(settings(List.of("192.0.2.0/24")), "key-1234",
                "192.0.2.92");

        assertThat(found).isNotNull();
        assertThat(found.name()).isEqualTo("전자결재");
    }

    @Test
    void 키가_틀리면_못_가린다() {
        assertThat(CallerGate.of(settings(List.of()), "wrong", "192.0.2.92")).isNull();
    }

    @Test
    void 키가_없으면_못_가린다() {
        assertThat(CallerGate.of(settings(List.of()), null, "192.0.2.92")).isNull();
    }

    @Test
    void 주소_대역_밖이면_막는다() {
        // 키가 새어도 사내 밖에서는 못 쓰게 한다.
        assertThat(CallerGate.of(settings(List.of("192.0.2.0/24")), "key-1234", "8.8.8.8"))
                .isNull();
    }

    @Test
    void 주소를_안_적은_시스템은_주소로_못_가린다() {
        // 2026-08-20 부터 주소가 첫 관문이다. 주소를 안 적은 항목은 열쇠로만 연다.
        assertThat(CallerGate.byAddress(settings(List.of()), "8.8.8.8")).isNull();
        assertThat(CallerGate.byKey(settings(List.of()), "key-1234")).isNotNull();
    }

    @Test
    void 주소만_적은_시스템은_열쇠_없이_통과한다() {
        // 열쇠를 안 적은 시스템이다. 사내 대역에서 오면 그대로 통과한다.
        ViewCaller 열쇠없음 = new ViewCaller();
        열쇠없음.setName("사내서버");
        열쇠없음.setAddresses(List.of("192.0.2.0/24"));
        ViewProperties properties = new ViewProperties();
        properties.setCallers(List.of(열쇠없음));

        assertThat(CallerGate.of(properties, null, "192.0.2.25")).isNotNull();
        assertThat(CallerGate.of(properties, null, "8.8.8.8")).isNull();
        assertThat(CallerGate.keyMatches(열쇠없음, null)).isTrue();
    }

    @Test
    void 열쇠를_적은_시스템은_열쇠도_맞아야_한다() {
        ViewProperties properties = settings(List.of("192.0.2.0/24"));

        assertThat(CallerGate.of(properties, "key-1234", "192.0.2.25")).isNotNull();
        assertThat(CallerGate.of(properties, null, "192.0.2.25")).isNull();
        assertThat(CallerGate.of(properties, "wrong", "192.0.2.25")).isNull();
    }

    @Test
    void 주소를_안_적은_시스템은_열쇠로만_연다() {
        // 주소가 첫 관문이 된 뒤에도 옛 방식(열쇠만 적은 항목)은 그대로 산다.
        ViewProperties properties = settings(List.of());

        assertThat(CallerGate.byAddress(properties, "8.8.8.8")).isNull();
        assertThat(CallerGate.byKeyOnly(properties, "key-1234")).isNotNull();
    }

    @Test
    void 주소를_적은_시스템은_열쇠만으로는_못_연다() {
        // 걸리게 두면 대역 밖에서도 열쇠만 있으면 통과해 주소 관문이 무너진다.
        ViewProperties properties = settings(List.of("192.0.2.0/24"));

        assertThat(CallerGate.byKeyOnly(properties, "key-1234")).isNull();
    }

    @Test
    void 주소가_밖이어도_키로는_가려낸다() {
        // 키가 틀린 것(401)과 주소가 막힌 것(403)을 나눠 돌려주려면 두 판정이
        // 갈라져 있어야 한다. 이력에 "어느 시스템이 대역 밖에서 불렀나" 도 남는다.
        ViewCaller found = CallerGate.byKey(settings(List.of("192.0.2.0/24")), "key-1234");

        assertThat(found).isNotNull();
        assertThat(CallerGate.allows(found, "8.8.8.8")).isFalse();
    }

    @Test
    void IPv6_사상_주소도_대역_안이면_통과한다() {
        // 톰캣이 이중 스택이면 getRemoteAddr() 가 ::ffff:192.0.2.92 를 준다.
        // 접두사를 못 벗기면 대역 안에서 온 정당한 요청이 막힌다.
        assertThat(CallerGate.of(settings(List.of("192.0.2.0/24")), "key-1234",
                "::ffff:192.0.2.92")).isNotNull();
        assertThat(CallerGate.of(settings(List.of("192.0.2.92")), "key-1234",
                "::ffff:192.0.2.92")).isNotNull();
    }

    @Test
    void 못_읽는_주소는_조용히_막는다() {
        // 숫자로 못 바꾸는 주소에서 예외를 던지면 이력도 안 남는 500 이 된다.
        // "안 맞음" 으로 다뤄 403 까지 가게 한다.
        assertThat(CallerGate.of(settings(List.of("192.0.2.0/24")), "key-1234", "fe80::1"))
                .isNull();
        assertThat(CallerGate.of(settings(List.of("192.0.2.0/24")), "key-1234", "192.0.2"))
                .isNull();
        assertThat(CallerGate.of(settings(List.of("192.0.2.0/24")), "key-1234", "그냥글자"))
                .isNull();
        assertThat(CallerGate.of(settings(List.of("192.0.2.0/24")), "key-1234", "999.1.1.1"))
                .isNull();
        // 대역 쪽이 망가진 경우도 마찬가지다 - 설정 오타가 문을 열어 주면 안 된다.
        assertThat(CallerGate.of(settings(List.of("사내대역/16")), "key-1234", "192.0.2.92"))
                .isNull();
    }
}
