package weboffice.doc;

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
import weboffice.workbook.MemberMapper;
import weboffice.workbook.WorkbookDto;
import weboffice.workbook.WorkbookMapper;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 글 문서의 문단 저장.
 *
 * <p>문단 한 줄에 한 행이다. 통째로 담으면 둘이 동시에 고칠 때 나중 저장이 앞선
 * 것을 덮는다 - 시트에서 셀 단위로 한 것과 같은 얼개다.
 *
 * <p>셀과 같은 delta 방식이다. 바뀐 것과 지운 것만 보낸다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DocParagraphTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private WorkbookMapper workbookMapper;

    @Autowired
    private MemberMapper memberMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private Authenticator authenticator;

    private Long workbookSeq;

    @BeforeEach
    void 문서를_만들고_로그인한다() {
        DbTestSupport.deleteAll(jdbcTemplate);
        WorkbookDto workbook = new WorkbookDto(null, "보고서", "me", null, null);
        workbook.setDocType("DOC");
        workbookMapper.insertWorkbook(workbook, "me");
        workbookSeq = workbook.workbookSeq();
        memberMapper.insertMember(workbookSeq, "viewer1", "VIEWER", "me");
        loggedInAs("me");
    }

    @Test
    void 저장한_문단을_다시_읽는다() throws Exception {
        저장한다("{\"changed\":["
                + "{\"paragraphNo\":0,\"text\":\"첫째\",\"style\":null,\"runs\":null},"
                + "{\"paragraphNo\":1,\"text\":\"둘째\",\"style\":\"{}\",\"runs\":null}"
                + "],\"deleted\":[]}");

        mockMvc.perform(get("/api/docs/{seq}/paragraphs", workbookSeq))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].text").value("첫째"))
                .andExpect(jsonPath("$[1].style").value("{}"));
    }

    @Test
    void 같은_번호는_덮어쓴다() throws Exception {
        저장한다("{\"changed\":[{\"paragraphNo\":0,\"text\":\"처음\"}],\"deleted\":[]}");
        저장한다("{\"changed\":[{\"paragraphNo\":0,\"text\":\"고침\"}],\"deleted\":[]}");

        mockMvc.perform(get("/api/docs/{seq}/paragraphs", workbookSeq))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].text").value("고침"));
    }

    @Test
    void 지운_문단은_빠진다() throws Exception {
        // 글이 짧아지면 뒤쪽 문단이 사라진다. 남겨 두면 다음에 열 때 되살아난다.
        저장한다("{\"changed\":[{\"paragraphNo\":0,\"text\":\"남을 것\"},"
                + "{\"paragraphNo\":1,\"text\":\"지울 것\"}],\"deleted\":[]}");
        저장한다("{\"changed\":[],\"deleted\":[1]}");

        mockMvc.perform(get("/api/docs/{seq}/paragraphs", workbookSeq))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].text").value("남을 것"));
    }

    @Test
    void 번호_차례로_돌려준다() throws Exception {
        // 순서가 뒤집히면 문서가 뒤죽박죽이 된다.
        저장한다("{\"changed\":[{\"paragraphNo\":2,\"text\":\"셋\"},"
                + "{\"paragraphNo\":0,\"text\":\"하나\"},"
                + "{\"paragraphNo\":1,\"text\":\"둘\"}],\"deleted\":[]}");

        mockMvc.perform(get("/api/docs/{seq}/paragraphs", workbookSeq))
                .andExpect(jsonPath("$[0].text").value("하나"))
                .andExpect(jsonPath("$[1].text").value("둘"))
                .andExpect(jsonPath("$[2].text").value("셋"));
    }

    @Test
    void 열람_권한은_저장하지_못한다() throws Exception {
        loggedInAs("viewer1");

        mockMvc.perform(patch("/api/docs/{seq}/paragraphs", workbookSeq)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"changed\":[{\"paragraphNo\":0,\"text\":\"몰래\"}],\"deleted\":[]}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void 남의_문서는_읽지_못한다() throws Exception {
        loggedInAs("stranger");

        mockMvc.perform(get("/api/docs/{seq}/paragraphs", workbookSeq))
                .andExpect(status().isForbidden());
    }

    private void 저장한다(String body) throws Exception {
        mockMvc.perform(patch("/api/docs/{seq}/paragraphs", workbookSeq)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    private void loggedInAs(String userKey) {
        when(authenticator.authenticate(any(), any()))
                .thenReturn(new LoginUser(userKey, userKey, userKey, "사원", "D100", "전산팀", "ko"));
    }
}
