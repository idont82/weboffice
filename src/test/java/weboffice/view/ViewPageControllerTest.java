package weboffice.view;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/** 사람에게 보이는 오류 화면. 스프링 없이 손으로 세워 안내 문구만 본다. */
class ViewPageControllerTest {

    @Test
    void 오류_안내_화면은_메시지를_이스케이프한다() {
        // 지금 오는 메시지는 전부 상수라 못 뚫지만, 같은 예외 종류를 남이 준 파일
        // 이름이 섞인 메시지와 함께 쓴다 - 조립하는 쪽에서 막아 둔다.
        ViewPageController controller = new ViewPageController(null, null);

        ResponseEntity<String> answer = controller.onError(new ViewException(
                ViewErrorCode.TOKEN_EXPIRED, "<script>alert(1)</script>.xlsx"));

        assertThat(answer.getBody()).doesNotContain("<script>");
        assertThat(answer.getBody()).contains("&lt;script&gt;");
    }
}
