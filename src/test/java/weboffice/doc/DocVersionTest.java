package weboffice.doc;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import weboffice.security.DirectoryUser;
import weboffice.security.UserDirectory;
import weboffice.security.LoginUser;
import weboffice.security.Authenticator;
import weboffice.support.DbTestSupport;
import weboffice.workbook.WorkbookDto;
import weboffice.workbook.WorkbookMapper;

import static com.jayway.jsonpath.JsonPath.read;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 글 문서의 버전 기록과 되돌리기.
 *
 * <p>글 문서에는 이력이 아예 없었다 - 문단 표가 현재 모습만 들고 있었다. 시트와
 * 같은 얼개로 문단 이력을 남기고, 버전 기록은 시트와 한 화면을 쓴다(2026-08-20).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DocVersionTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private WorkbookMapper workbookMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private Authenticator authenticator;

    @MockitoBean
    private UserDirectory userDirectory;

    private Long workbookSeq;

    @BeforeEach
    void 글_문서를_만들고_로그인한다() {
        DbTestSupport.deleteAll(jdbcTemplate);
        loggedInAs("me");
        when(userDirectory.findByKeys(any())).thenReturn(List.of(
                new DirectoryUser("me", "me-id", "나", "책임", "D1", "전산팀")));

        WorkbookDto workbook = new WorkbookDto(null, "글 문서", "me", null, null);
        workbookMapper.insertWorkbook(workbook, "me");
        workbookSeq = workbook.workbookSeq();
    }

    @Test
    void 문단을_고치면_버전이_생긴다() throws Exception {
        문단을_담는다("[{\"paragraphNo\":0,\"text\":\"처음 글\"}]");

        mockMvc.perform(get("/api/workbooks/{seq}/versions", workbookSeq))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].changeCount").value(1))
                .andExpect(jsonPath("$[0].editors[0]").value("나 책임"));
    }

    @Test
    void 같은_글을_다시_담으면_버전이_늘지_않는다() throws Exception {
        // 문서를 열기만 해도 저장이 도는 자리가 있다. 값이 그대로면 기록도 없어야 한다.
        문단을_담는다("[{\"paragraphNo\":0,\"text\":\"처음 글\"}]");
        문단을_담는다("[{\"paragraphNo\":0,\"text\":\"처음 글\"}]");

        mockMvc.perform(get("/api/workbooks/{seq}/versions", workbookSeq))
                .andExpect(jsonPath("$[0].changeCount").value(1));
    }

    @Test
    void 되돌리면_그때_글로_돌아온다() throws Exception {
        문단을_담는다("[{\"paragraphNo\":0,\"text\":\"처음 글\"}]");
        long version = 지금_버전();
        문단을_담는다("[{\"paragraphNo\":0,\"text\":\"고친 글\"},"
                + "{\"paragraphNo\":1,\"text\":\"덧붙인 글\"}]");

        mockMvc.perform(post("/api/workbooks/{seq}/versions/{version}/restore",
                        workbookSeq, version))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.restoredCount").value(2));

        mockMvc.perform(get("/api/docs/{seq}/paragraphs", workbookSeq))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].text").value("처음 글"));
    }

    @Test
    void 앞에_줄을_넣어도_넣은_줄만_바뀐_것으로_센다() throws Exception {
        // 문단 번호로 견주면 아래 문단이 통째로 밀려 "131군데 바뀜" 이 된다
        // (2026-08-20 지적). 내용으로 견뎌야 넣은 줄만 나온다.
        문단을_담는다("[{\"paragraphNo\":0,\"text\":\"첫째\"},"
                + "{\"paragraphNo\":1,\"text\":\"둘째\"},"
                + "{\"paragraphNo\":2,\"text\":\"셋째\"}]");
        // 앞선 편집을 스무 날 아니라 스무 분 전으로 밀어 다른 판으로 가른다.
        // (편집 덩어리는 10분을 넘겨 쉬면 갈린다.)
        jdbcTemplate.update("UPDATE wo_doc_change_log "
                + "SET INPUT_DATETIME = ? WHERE WORKBOOK_SEQ = ?",
                java.sql.Timestamp.valueOf(java.time.LocalDateTime.now().minusMinutes(20)),
                workbookSeq);
        // 맨 앞에 한 줄을 넣으면 아래 문단 번호가 하나씩 밀린다.
        문단을_담는다("[{\"paragraphNo\":0,\"text\":\"새 줄\"},"
                + "{\"paragraphNo\":1,\"text\":\"첫째\"},"
                + "{\"paragraphNo\":2,\"text\":\"둘째\"},"
                + "{\"paragraphNo\":3,\"text\":\"셋째\"}]");

        mockMvc.perform(get("/api/workbooks/{seq}/versions/{version}/changes",
                        workbookSeq, 지금_버전()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].after").value("새 줄"));
    }

    @Test
    void 빈_줄이_늘고_준_것은_세지_않는다() throws Exception {
        // 엔터만 쳐도 빈 문단이 생긴다. 그것을 "바뀐 자리" 로 세면 정작 무슨 글을
        // 썼는지가 묻힌다(2026-08-20 실측: 5건 중 3건이 빈 줄이었다).
        문단을_담는다("[{\"paragraphNo\":0,\"text\":\"첫째\"}]");
        jdbcTemplate.update("UPDATE wo_doc_change_log "
                + "SET INPUT_DATETIME = ? WHERE WORKBOOK_SEQ = ?",
                java.sql.Timestamp.valueOf(java.time.LocalDateTime.now().minusMinutes(20)),
                workbookSeq);
        문단을_담는다("[{\"paragraphNo\":0,\"text\":\"첫째\"},"
                + "{\"paragraphNo\":1,\"text\":\"\"},"
                + "{\"paragraphNo\":2,\"text\":\"둘째\"}]");

        mockMvc.perform(get("/api/workbooks/{seq}/versions/{version}/changes",
                        workbookSeq, 지금_버전()))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].after").value("둘째"));
    }

    private long 지금_버전() throws Exception {
        // 되돌릴 자리를 시각으로 가른다. 같은 밀리초에 다음 편집이 들어오면 갈리지
        // 않으므로 검사에서도 한 박자 쉰다.
        Thread.sleep(20);
        String body = mockMvc.perform(get("/api/workbooks/{seq}/versions", workbookSeq))
                .andReturn().getResponse().getContentAsString();
        Number id = read(body, "$[0].versionId");
        Thread.sleep(20);
        return id.longValue();
    }

    private void 문단을_담는다(String paragraphsJson) throws Exception {
        mockMvc.perform(patch("/api/docs/{seq}/paragraphs", workbookSeq)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"changed\":" + paragraphsJson + "}"))
                .andExpect(status().isOk());
    }

    private void loggedInAs(String userKey) {
        when(authenticator.authenticate(any(), any()))
                .thenReturn(new LoginUser(userKey, userKey, userKey, "사원", "D100", "전산팀", "ko"));
    }
}
