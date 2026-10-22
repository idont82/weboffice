package weboffice.workbook;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;

import weboffice.security.LoginUser;
import weboffice.security.Authenticator;
import weboffice.security.UserContext;
import weboffice.support.DbTestSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 공개 링크.
 *
 * <p>핵심은 두 가지다. 재발급하면 옛 링크가 그 순간 죽고, 해제하면 이미 들어와
 * 있던 세션도 막힌다. 세션에 남긴 허가만 믿으면 둘 다 새는데, 그것을 여기서 잡는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ShareLinkTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private WorkbookMapper workbookMapper;

    @Autowired
    private MemberMapper memberMapper;

    @Autowired
    private WorkbookPermissionService permissionService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private Authenticator authenticator;

    private MockHttpSession session;

    @BeforeEach
    void 데이터를_비우고_로그인한다() {
        DbTestSupport.deleteAll(jdbcTemplate);
        session = new MockHttpSession();
        loggedInAs("me");
    }

    @Test
    void 링크를_발급하면_주소를_준다() throws Exception {
        Long seq = 문서를_만든다("공개 문서", "me");

        mockMvc.perform(post("/api/workbooks/{seq}/share-link", seq))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value(matchesPattern("/s/[0-9a-f]{32}")));
    }

    @Test
    void 링크로_들어오면_문서로_보낸다() throws Exception {
        Long seq = 문서를_만든다("공개 문서", "other");
        String url = 발급한다(seq, "other");

        loggedInAs("me");
        mockMvc.perform(get(url).session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/?workbookSeq=" + seq));
    }

    @Test
    void 링크로_들어온_사람은_편집할_수_있다() throws Exception {
        // 공개는 편집까지다(설계 2절).
        Long seq = 문서를_만든다("공개 문서", "other");
        String url = 발급한다(seq, "other");

        loggedInAs("me");
        mockMvc.perform(get(url).session(session));

        assertThat(권한(seq)).isEqualTo(WorkbookRole.EDITOR);
    }

    @Test
    void 링크_없이는_들어가지_못한다() throws Exception {
        // 순번 주소만으로는 공개 문서라도 열리지 않아야 한다(설계 2절).
        Long seq = 문서를_만든다("공개 문서", "other");
        발급한다(seq, "other");

        loggedInAs("me");
        mockMvc.perform(get("/api/workbooks/{seq}/sheets", seq).session(session))
                .andExpect(status().isForbidden());
    }

    @Test
    void 재발급하면_옛_링크가_즉시_막힌다() throws Exception {
        Long seq = 문서를_만든다("공개 문서", "other");
        String oldUrl = 발급한다(seq, "other");
        String newUrl = 발급한다(seq, "other");
        assertThat(newUrl).isNotEqualTo(oldUrl);

        loggedInAs("me");
        mockMvc.perform(get(oldUrl).session(session)).andExpect(status().isNotFound());
    }

    @Test
    void 재발급하면_옛_링크로_들어온_사람도_막힌다() throws Exception {
        // 재발급의 뜻은 "이 링크가 샜다" 다. 옛 주소만 죽이고 그 주소로 이미 들어온
        // 사람을 그냥 두면 샌 링크를 회수하지 못한 것과 같다(설계 6절).
        //
        // 세션에 문서 순번만 남기면 이것을 못 잡는다. 판정이 "허가가 있고 토큰이
        // 있는가" 가 되어, 재발급으로 토큰이 바뀌어도 여전히 참이다.
        Long seq = 문서를_만든다("공개 문서", "other");
        String oldUrl = 발급한다(seq, "other");

        loggedInAs("me");
        mockMvc.perform(get(oldUrl).session(session));
        assertThat(권한(seq)).isEqualTo(WorkbookRole.EDITOR);

        loggedInAs("other");
        발급한다(seq, "other");

        loggedInAs("me");
        mockMvc.perform(get("/api/workbooks/{seq}/sheets", seq).session(session))
                .andExpect(status().isForbidden());
    }

    @Test
    void 해제하면_이미_들어온_사람도_막힌다() throws Exception {
        // 세션 기록만 믿으면 해제해도 기존 세션이 계속 들어온다(설계 6절).
        Long seq = 문서를_만든다("공개 문서", "other");
        String url = 발급한다(seq, "other");
        loggedInAs("me");
        mockMvc.perform(get(url).session(session));
        assertThat(권한(seq)).isEqualTo(WorkbookRole.EDITOR);

        loggedInAs("other");
        mockMvc.perform(delete("/api/workbooks/{seq}/share-link", seq))
                .andExpect(status().isOk());

        loggedInAs("me");
        mockMvc.perform(get("/api/workbooks/{seq}/sheets", seq).session(session))
                .andExpect(status().isForbidden());
    }

    @Test
    void 열람자가_링크로도_들어오면_편집할_수_있다() throws Exception {
        // 링크가 주는 권한이 더 넓다. 반대로 하면 초대받는 쪽이 손해를 본다(설계 7절).
        Long seq = 문서를_만든다("공개 문서", "other");
        memberMapper.insertMember(seq, "me", "VIEWER", "other");
        String url = 발급한다(seq, "other");

        loggedInAs("me");
        mockMvc.perform(get(url).session(session));

        assertThat(권한(seq)).isEqualTo(WorkbookRole.EDITOR);
    }

    @Test
    void 소유자가_아니면_링크를_발급하지_못한다() throws Exception {
        Long seq = 문서를_만든다("남의 문서", "other");
        memberMapper.insertMember(seq, "me", "EDITOR", "other");

        mockMvc.perform(post("/api/workbooks/{seq}/share-link", seq))
                .andExpect(status().isForbidden());
    }

    @Test
    void 없는_토큰은_404() throws Exception {
        mockMvc.perform(get("/s/{token}", "0".repeat(32)))
                .andExpect(status().isNotFound());
    }

    /** 인터셉터가 하는 일과 같다. 세션에 남은 허가를 스레드에 실어 판정한다. */
    private WorkbookRole 권한(Long seq) {
        UserContext.bindGrants(UserContext.findGrants(session));
        try {
            return permissionService.requireReadWorkbook(seq, "me");
        } finally {
            UserContext.clearRequest();
        }
    }

    private String 발급한다(Long seq, String ownerUser) throws Exception {
        loggedInAs(ownerUser);
        String body = mockMvc.perform(post("/api/workbooks/{seq}/share-link", seq))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.url");
    }

    private Long 문서를_만든다(String name, String ownerUser) {
        WorkbookDto workbook = new WorkbookDto(null, name, ownerUser, null, null);
        workbookMapper.insertWorkbook(workbook, ownerUser);
        return workbook.workbookSeq();
    }

    private void loggedInAs(String userKey) {
        when(authenticator.authenticate(any(), any()))
                .thenReturn(new LoginUser(userKey + "-id", userKey, userKey, "사원", "D1", "전산팀", "ko"));
    }
}
