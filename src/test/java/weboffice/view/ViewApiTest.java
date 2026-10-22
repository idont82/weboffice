package weboffice.view;

import java.io.ByteArrayOutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import weboffice.security.UserDirectory;
import weboffice.security.Authenticator;
import weboffice.support.DbTestSupport;

import static com.jayway.jsonpath.JsonPath.read;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 발급 → 열람 → 스냅샷 한 줄기.
 *
 * <p>시험 설정(application-test.yml)에 부르는 시스템 둘을 둔다: 주소를 안 가리는
 * "시험시스템"(키 test-key)과 사내 대역만 받는 "대역시스템"(키 range-key).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ViewApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private Authenticator authenticator;

    @MockitoBean
    private UserDirectory userDirectory;

    @BeforeEach
    void 비운다() {
        DbTestSupport.deleteAll(jdbcTemplate);
    }

    private static byte[] 엑셀한장() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            workbook.createSheet("시트1").createRow(0).createCell(0).setCellValue("값");
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private String 발급받는다() throws Exception {
        return 발급받는다("홍길동 책임");
    }

    /** 열람자 이름을 안 주는 시스템도 있다. 그 갈래를 보려면 이름을 바꿔 넣어야 한다. */
    private String 발급받는다(String viewerName) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "보고서.xlsx",
                "application/octet-stream", 엑셀한장());

        String body = mockMvc.perform(multipart("/api/view/documents")
                        .file(file)
                        .param("fileName", "보고서.xlsx")
                        .param("viewerName", viewerName)
                        .param("systemName", "시험시스템")
                        .header("X-WO-API-KEY", "test-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.viewUrl").exists())
                .andReturn().getResponse().getContentAsString();

        String viewUrl = read(body, "$.viewUrl");
        return viewUrl.substring(viewUrl.lastIndexOf('/') + 1);
    }

    /** 오류 이력 한 줄. 두 줄 이상이면 시험이 무엇을 보고 있는지 흐려지므로 하나만 본다. */
    private Map<String, Object> 오류이력한줄() {
        return jdbcTemplate.queryForMap(
                "SELECT RESULT_CODE, MESSAGE, FILE_NAME, CLIENT_IP, CALLER_SYSTEM"
                        + "  FROM wo_view_event_log WHERE EVENT_TYPE = 'ERROR'");
    }

    @Test
    void 열쇠도_주소도_아니면_403() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "보고서.xlsx",
                "application/octet-stream", 엑셀한장());

        mockMvc.perform(multipart("/api/view/documents").file(file)
                        .param("fileName", "보고서.xlsx")
                        .param("viewerName", "홍길동 책임")
                        .param("systemName", "시험시스템"))
                .andExpect(status().isForbidden());
    }

    @Test
    void 틀린_열쇠로_부르면_오류_이력이_남는다() throws Exception {
        // 설계 3.3절: "둘 중 하나라도 어긋나면 401/403 이고, 그 사실도 이력에 남는다."
        // 감사가 이 기능의 뼈대다 - 남의 시스템이 틀린 열쇠로 두드린 것도 남아야 한다.
        MockMultipartFile file = new MockMultipartFile("file", "보고서.xlsx",
                "application/octet-stream", 엑셀한장());

        // 열쇠를 적어 둔 시스템(대역시스템)의 대역 안에서 틀린 열쇠로 부른다.
        mockMvc.perform(multipart("/api/view/documents").file(file)
                        .param("fileName", "보고서.xlsx")
                        .param("viewerName", "홍길동 책임")
                        .param("systemName", "시험시스템")
                        .header("X-WO-API-KEY", "훔친-열쇠")
                        .with(request -> {
                            request.setRemoteAddr("203.0.113.5");
                            return request;
                        }))
                .andExpect(status().isUnauthorized());

        Map<String, Object> row = 오류이력한줄();
        assertThat(row.get("RESULT_CODE")).isEqualTo("UNAUTHORIZED");
        assertThat(row.get("FILE_NAME")).isEqualTo("보고서.xlsx");
        assertThat(row.get("CLIENT_IP")).isEqualTo("203.0.113.5");
    }

    @Test
    void 열쇠는_맞아도_주소가_목록_밖이면_403() throws Exception {
        // 키가 틀린 것(401)과 주소가 막힌 것(403)을 갈라 준다 - 부르는 쪽이 모두
        // 우리 시스템이라 무엇을 고쳐야 하는지 알려 주는 편이 낫다(설계 8절).
        MockMultipartFile file = new MockMultipartFile("file", "보고서.xlsx",
                "application/octet-stream", 엑셀한장());

        mockMvc.perform(multipart("/api/view/documents").file(file)
                        .param("fileName", "보고서.xlsx")
                        .param("viewerName", "홍길동 책임")
                        .param("systemName", "시험시스템")
                        .header("X-WO-API-KEY", "range-key"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN_ADDRESS"));

        Map<String, Object> row = 오류이력한줄();
        assertThat(row.get("RESULT_CODE")).isEqualTo("FORBIDDEN_ADDRESS");
        // 키로는 누구인지 가려냈다 - 어느 시스템이 대역 밖에서 불렀는지 남아야 한다.
        assertThat(row.get("CALLER_SYSTEM")).isEqualTo("대역시스템");
    }

    @Test
    void 받기에_실패해도_오류_이력이_남는다() throws Exception {
        // 받기(DocumentFetcher)는 issue() 밖이라 지금까지 이력이 안 남았다.
        // 아무도 듣지 않는 포트로 보내 받기 단계에서 끊는다.
        mockMvc.perform(post("/api/view/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-WO-API-KEY", "test-key")
                        .content("{\"fileUrl\":\"http://127.0.0.1:1/none\","
                                + "\"fileName\":\"report.xlsx\",\"viewerName\":\"tester\","
                                + "\"systemName\":\"시험시스템\"}"))
                .andExpect(status().isBadGateway());

        Map<String, Object> row = 오류이력한줄();
        assertThat(row.get("RESULT_CODE")).isEqualTo("FETCH_FAILED");
        assertThat(row.get("FILE_NAME")).isEqualTo("report.xlsx");
        assertThat(row.get("CALLER_SYSTEM")).isEqualTo("시험시스템");
    }

    @Test
    void 아주_긴_파일_이름도_오류_이력을_담는다() throws Exception {
        // FILE_NAME 은 varchar(400), MESSAGE 는 varchar(1000) 이다. 자르지 않으면
        // 오류를 담다가 또 터진다 - 오류 처리기 안에서 터지는 자리라 값이 싸다.
        String 긴이름 = "가".repeat(1200) + ".txt";
        MockMultipartFile file = new MockMultipartFile("file", 긴이름,
                "application/octet-stream", new byte[] {1, 2, 3});

        mockMvc.perform(multipart("/api/view/documents").file(file)
                        .param("fileName", 긴이름)
                        .param("viewerName", "홍길동 책임")
                        .param("systemName", "시험시스템")
                        .header("X-WO-API-KEY", "test-key"))
                .andExpect(status().isUnsupportedMediaType());

        Map<String, Object> row = 오류이력한줄();
        assertThat((String) row.get("FILE_NAME")).hasSize(400);
        assertThat((String) row.get("MESSAGE")).hasSize(1000);
    }

    @Test
    void 발급받은_링크를_열면_스냅샷을_받는다() throws Exception {
        String token = 발급받는다();

        // 브라우저와 같은 길을 밟는다: 열 때 받은 세션 쿠키를 다음 요청에 실어 보낸다.
        jakarta.servlet.http.Cookie[] cookies = mockMvc.perform(get("/v/{token}", token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getCookies();

        byte[] snapshot = mockMvc.perform(get("/api/view/{token}/snapshot", token)
                        .cookie(cookies))
                .andExpect(status().isOk())
                // 브라우저 캐시나 프록시가 스냅샷을 붙잡고 있으면 안 된다 - 문서마다
                // 새로 받아야 하고, gzip 인 채로 그대로 내려간다는 것도 확인한다.
                .andExpect(header().string("Content-Encoding", "gzip"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(snapshot).isNotEmpty();
    }

    @Test
    void 스냅샷_머리에_무슨_문서인지_적는다() throws Exception {
        // 몸통은 변환 결과 JSON 그것뿐이다 - 무슨 문서인지·누가 보는지는 머리에 싣는다.
        String token = 발급받는다();
        jakarta.servlet.http.Cookie[] cookies = mockMvc.perform(get("/v/{token}", token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getCookies();

        MockHttpServletResponse response = mockMvc.perform(
                        get("/api/view/{token}/snapshot", token).cookie(cookies))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Wo-Doc-Type", "SHEET"))
                .andExpect(header().string("X-Wo-Encrypted", "N"))
                .andReturn().getResponse();

        // 머리에 한글을 그대로 넣으면 깨진다. UTF-8 URL 로 싸서 보내고 화면이 푼다.
        assertThat(URLDecoder.decode(response.getHeader("X-Wo-File-Name"),
                StandardCharsets.UTF_8)).isEqualTo("보고서.xlsx");
        String viewerName = response.getHeader("X-Wo-Viewer-Name");
        assertThat(URLDecoder.decode(viewerName, StandardCharsets.UTF_8))
                .isEqualTo("홍길동 책임");
        // 화면은 decodeURIComponent 로 푼다. 그것은 '+' 를 빈칸으로 돌리지 않는다 -
        // 폼 규칙(URLEncoder)대로 보내면 "홍길동+책임" 이 그대로 화면에 뜬다.
        assertThat(viewerName).doesNotContain("+");
    }

    @Test
    void 열람자_이름이_없으면_그_머리는_안_붙인다() throws Exception {
        // 이름을 안 알려 주는 시스템이 있다. 빈 머리를 붙이면 화면이 빈 이름으로
        // 워터마크를 찍는다 - 빈 이름과 없는 이름은 화면에서 같은 뜻이다.
        String token = 발급받는다("   ");
        jakarta.servlet.http.Cookie[] cookies = mockMvc.perform(get("/v/{token}", token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getCookies();

        MockHttpServletResponse response = mockMvc.perform(
                        get("/api/view/{token}/snapshot", token).cookie(cookies))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("X-Wo-Viewer-Name"))
                // 나머지 셋은 그대로 붙는다. 이름 하나가 없다고 문서 종류까지
                // 빠지면 화면이 무엇을 세울지 모른다.
                .andExpect(header().string("X-Wo-Doc-Type", "SHEET"))
                .andExpect(header().string("X-Wo-Encrypted", "N"))
                .andReturn().getResponse();

        assertThat(URLDecoder.decode(response.getHeader("X-Wo-File-Name"),
                StandardCharsets.UTF_8)).isEqualTo("보고서.xlsx");
    }

    @Test
    void 남의_세션_번호를_쿠키에_넣어도_안_열린다() throws Exception {
        // 문서 하나를 열어 둔다 - 그 세션의 SESSION_SEQ 는 1(이 시험에서 DB 를
        // 매번 비우므로 첫 번째 세션이다).
        String token = 발급받는다();
        mockMvc.perform(get("/v/{token}", token)).andExpect(status().isOk());

        // 공격자는 진짜 토큰이 없다 - 있는 것은 "세션 번호가 순번일 것" 이라는
        // 짐작뿐이다. 그래서 주소의 토큰 자리는 알지도 못하는 값을 넣고, 쿠키에만
        // 짐작한 순번을 넣어 스냅샷을 부른다. 고친 전이라면 쿠키 값을 그대로
        // SESSION_SEQ 로 여겨 이 문서를 내줬을 것이다.
        jakarta.servlet.http.Cookie guessed =
                new jakarta.servlet.http.Cookie(ViewPageController.COOKIE, "1");

        mockMvc.perform(get("/api/view/{token}/snapshot", "0".repeat(32)).cookie(guessed))
                .andExpect(status().isGone());
    }

    @Test
    void 쿠키를_실은_새로고침은_다시_열린다() throws Exception {
        // /v/{token} 은 forward 라 주소창에 토큰이 남는다. F5 는 같은 요청을 그대로
        // 다시 보낸다 - 설계 4절이 "새로고침은 계속 된다" 고 약속한 자리다.
        String token = 발급받는다();
        jakarta.servlet.http.Cookie[] cookies = mockMvc.perform(get("/v/{token}", token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getCookies();

        mockMvc.perform(get("/v/{token}", token).cookie(cookies))
                .andExpect(status().isOk());
    }

    @Test
    void 쿠키_없이_두_번째로_열면_막는다() throws Exception {
        // 링크가 새어 남이 열면 그 브라우저에는 쿠키가 없다 - 그때는 막는다.
        String token = 발급받는다();
        mockMvc.perform(get("/v/{token}", token)).andExpect(status().isOk());

        mockMvc.perform(get("/v/{token}", token))
                .andExpect(status().isGone());
    }

    @Test
    void 다른_문서의_쿠키로는_새로고침도_안_된다() throws Exception {
        // 쿠키는 "그 토큰의 세션" 을 가리켜야 한다. 아무 세션 쿠키나 있으면 통과하면
        // 제 문서 하나를 연 사람이 새어 나온 남의 링크까지 다시 열 수 있다.
        String 내문서 = 발급받는다();
        jakarta.servlet.http.Cookie[] 내쿠키 = mockMvc.perform(get("/v/{token}", 내문서))
                .andExpect(status().isOk())
                .andReturn().getResponse().getCookies();

        String 남의문서 = 발급받는다();
        mockMvc.perform(get("/v/{token}", 남의문서)).andExpect(status().isOk());

        mockMvc.perform(get("/v/{token}", 남의문서).cookie(내쿠키))
                .andExpect(status().isGone());
    }

    @Test
    void 없는_토큰은_막는다() throws Exception {
        mockMvc.perform(get("/v/{token}", "0".repeat(32)))
                .andExpect(status().isGone());
    }

    @Test
    void 열람_이력이_남는다() throws Exception {
        String token = 발급받는다();
        mockMvc.perform(get("/v/{token}", token));

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM wo_view_event_log WHERE EVENT_TYPE = 'OPEN'",
                Integer.class);
        assertThat(count).isEqualTo(1);
    }
}
