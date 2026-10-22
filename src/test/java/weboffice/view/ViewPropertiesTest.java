package weboffice.view;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/** 설정이 없으면 뷰어가 아무나 부를 수 있게 열리거나 아예 안 뜬다. 기본값을 못 박는다. */
@SpringBootTest
@ActiveProfiles("test")
class ViewPropertiesTest {

    @Autowired
    private ViewProperties properties;

    @Test
    void 기본값이_설계와_같다() {
        assertThat(properties.tokenMinutes()).isEqualTo(5);
        assertThat(properties.sessionHours()).isEqualTo(8);
        assertThat(properties.cacheMinutes()).isEqualTo(60);
        assertThat(properties.maxFileMb()).isEqualTo(50);
    }

    @Test
    void 관리자_목록이_비면_아무도_못_들어간다() {
        // 목록이 비었을 때 "모두 허용" 으로 읽으면 감사 화면이 통째로 열린다.
        // application-test.yml 은 시험시스템 발급·열람 흐름(ViewApiTest)을 확인하려고
        // 관리자 계정을 하나 등록해 뒀다 - 그래서 여기서는 주입받은 빈이 아니라
        // 새 인스턴스로 "설정을 안 주면" 이라는 조건 자체를 확인한다.
        ViewProperties empty = new ViewProperties();
        assertThat(empty.isAdmin("admin1")).isFalse();
        assertThat(empty.isAdmin(null)).isFalse();
    }

    @Test
    void 계정으로_관리자를_가린다() {
        ViewProperties given = new ViewProperties();
        given.setAdmins(List.of("admin1", "admin2"));

        assertThat(given.isAdmin("admin1")).isTrue();
        assertThat(given.isAdmin("ADMIN2")).isTrue();   // 대소문자는 가리지 않는다
        assertThat(given.isAdmin("stranger")).isFalse();
    }
}
