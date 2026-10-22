package weboffice.realtime;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.converter.SimpleMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import weboffice.security.LoginUser;
import weboffice.security.Authenticator;
import weboffice.sheet.SheetMapper;
import weboffice.sheet.SheetSummaryDto;
import weboffice.support.DbTestSupport;
import weboffice.workbook.WorkbookDto;
import weboffice.workbook.WorkbookMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class StompIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private WorkbookMapper workbookMapper;

    @Autowired
    private SheetMapper sheetMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private Authenticator authenticator;

    private final RestTemplate rest = new RestTemplate();
    private WebSocketStompClient stompClient;

    private Long sheetSeq;
    private Long otherSheetSeq;
    private String sessionCookie;

    @BeforeEach
    void 문서와_시트를_만들고_로그인한다() {
        DbTestSupport.deleteAll(jdbcTemplate);
        WorkbookDto workbook = new WorkbookDto(null, "실시간 문서", "20502534", null, null);
        workbookMapper.insertWorkbook(workbook, "20502534");
        sheetSeq = newSheet(workbook.workbookSeq(), "시트1", 0);
        otherSheetSeq = newSheet(workbook.workbookSeq(), "시트2", 1);

        when(authenticator.authenticate(any(), any()))
                .thenReturn(new LoginUser("owner1", "20502534", "소유자", "사원", "D100", "전산팀", "ko"));

        // 세션 쿠키를 얻는다. WebSocket 핸드셰이크가 이 쿠키로 인증된다.
        // 보호된 엔드포인트를 불러야 한다 — 열린 경로는 인터셉터가 그냥 통과시켜
        // 세션에 사용자가 담기지 않는다.
        ResponseEntity<String> response = rest.getForEntity(
                "http://localhost:" + port + "/api/sheets/" + sheetSeq
                        + "/cells?fromRowNo=0&fromColumnNo=0&toRowNo=0&toColumnNo=0",
                String.class);
        sessionCookie = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);

        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        // JSON 전문을 그대로 받는다. 타입 매핑을 거치지 않아도 전달 여부와
        // 토픽 분리는 확인된다.
        stompClient.setMessageConverter(new SimpleMessageConverter());
    }

    @AfterEach
    void 정리한다() {
        if (stompClient != null) {
            stompClient.stop();
        }
    }

    @Test
    void 구독자는_자리_표시_이벤트를_받는다() throws Exception {
        StompSession session = connect();
        BlockingQueue<String> received = subscribe(session, sheetSeq);

        acquireLock(sheetSeq);

        String payload = received.poll(5, TimeUnit.SECONDS);
        assertThat(payload).isNotNull();
        assertThat(payload).contains("PRESENCE_SET");
        // 사번이 실려 나가면 여기서 잡힌다. 아이디와 사번을 다르게 둔 이유다.
        assertThat(payload).doesNotContain("20502534");
    }

    @Test
    void 다른_시트_구독자는_그_이벤트를_받지_않는다() throws Exception {
        StompSession session = connect();
        BlockingQueue<String> otherTopic = subscribe(session, otherSheetSeq);

        acquireLock(sheetSeq);

        assertThat(otherTopic.poll(2, TimeUnit.SECONDS))
                .as("토픽 분리가 깨지면 각자 다른 문서를 연 사용자들이 서로의 이벤트를 전부 받는다")
                .isNull();
    }

    @Test
    void 세션_없는_연결은_거부된다() {
        WebSocketStompClient anonymous = new WebSocketStompClient(new StandardWebSocketClient());
        try {
            assertThatThrownBy(() -> anonymous
                    .connectAsync("ws://localhost:" + port + "/ws",
                            new WebSocketHttpHeaders(), new StompSessionHandlerAdapter() {
                            })
                    .get(5, TimeUnit.SECONDS))
                    .as("막지 않으면 아무나 모든 문서의 셀 변경을 실시간으로 받아간다")
                    .isInstanceOf(ExecutionException.class);
        } finally {
            anonymous.stop();
        }
    }

    private StompSession connect() throws Exception {
        WebSocketHttpHeaders handshake = new WebSocketHttpHeaders();
        handshake.add(HttpHeaders.COOKIE, sessionCookie);
        return stompClient.connectAsync("ws://localhost:" + port + "/ws", handshake,
                new StompSessionHandlerAdapter() {
                }).get(5, TimeUnit.SECONDS);
    }

    private BlockingQueue<String> subscribe(StompSession session, Long topicSheetSeq) {
        BlockingQueue<String> received = new LinkedBlockingQueue<>();
        session.subscribe("/topic/sheets/" + topicSheetSeq, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return byte[].class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                received.add(new String((byte[]) payload, StandardCharsets.UTF_8));
            }
        });
        return received;
    }

    private void acquireLock(Long targetSheetSeq) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add(HttpHeaders.COOKIE, sessionCookie);
        String body = """
                {"sheetSeq":%d,"fromRowNo":0,"fromColumnNo":0,"toRowNo":9,"toColumnNo":9}
                """.formatted(targetSheetSeq);
        rest.postForEntity("http://localhost:" + port + "/api/locks",
                new HttpEntity<>(body, headers), String.class);
    }

    private Long newSheet(Long workbookSeq, String name, int sheetNo) {
        SheetSummaryDto sheet = new SheetSummaryDto(null, workbookSeq, name, sheetNo, 0, 0);
        sheetMapper.insertSheet(sheet, "owner1");
        return sheet.sheetSeq();
    }
}
