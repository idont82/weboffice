package weboffice.workbook;

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

import weboffice.security.LoginUser;
import weboffice.security.Authenticator;
import weboffice.support.DbTestSupport;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 문서 종류.
 *
 * <p>이 시스템은 오피스이고 지금까지 만들어진 것이 시트일 뿐이다. 글 문서를 더하려면
 * 화면이 무엇을 열어야 하는지 알아야 한다 - 시트 격자냐 글 편집기냐.
 *
 * <p>종류를 문서 자체에 담는다. 파일 이름이나 시트 개수 따위로 짐작하면 빈 문서에서
 * 갈래가 없다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DocTypeTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private Authenticator authenticator;

    @BeforeEach
    void 비우고_로그인한다() {
        DbTestSupport.deleteAll(jdbcTemplate);
        when(authenticator.authenticate(any(), any()))
                .thenReturn(new LoginUser("me", "me", "나", "사원", "D100", "전산팀", "ko"));
    }

    @Test
    void 종류를_주지_않으면_시트다() throws Exception {
        // 지금까지 만든 문서가 전부 시트다. 옛 문서도 그렇게 읽혀야 한다.
        mockMvc.perform(post("/api/workbooks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.docType").value("SHEET"));
    }

    @Test
    void 글_문서를_만든다() throws Exception {
        mockMvc.perform(post("/api/workbooks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"docType\":\"DOC\",\"workbookName\":\"보고서\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.docType").value("DOC"))
                .andExpect(jsonPath("$.workbookName").value("보고서"));
    }

    @Test
    void 모르는_종류는_거절한다() throws Exception {
        // 담아 두면 화면이 무엇을 열지 몰라 빈 화면이 뜬다.
        mockMvc.perform(post("/api/workbooks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"docType\":\"HWP\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 목록과_한_건_조회에도_종류가_담긴다() throws Exception {
        // 화면이 목록에서 아이콘을 가르고, 열 때 무엇을 띄울지 정한다.
        // 제목을 준다 - 제목도 내용도 없는 문서는 목록에서 감춘다.
        String body = mockMvc.perform(post("/api/workbooks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"workbookName\":\"보고서\",\"docType\":\"DOC\"}"))
                .andReturn().getResponse().getContentAsString();
        long seq = com.jayway.jsonpath.JsonPath.parse(body).read("$.workbookSeq", Long.class);

        mockMvc.perform(get("/api/workbooks"))
                .andExpect(jsonPath("$.mine[0].docType").value("DOC"));

        mockMvc.perform(get("/api/workbooks/{seq}", seq))
                .andExpect(jsonPath("$.docType").value("DOC"));
    }

    @Test
    void 글_문서에는_시트를_붙이지_않는다() throws Exception {
        // 빈 시트가 딸려 오면 목록·내보내기가 그것을 세어 이상해진다.
        String body = mockMvc.perform(post("/api/workbooks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"docType\":\"DOC\"}"))
                .andReturn().getResponse().getContentAsString();
        long seq = com.jayway.jsonpath.JsonPath.parse(body).read("$.workbookSeq", Long.class);

        mockMvc.perform(get("/api/workbooks/{seq}/sheets", seq))
                .andExpect(jsonPath("$.length()").value(0));
    }
}
