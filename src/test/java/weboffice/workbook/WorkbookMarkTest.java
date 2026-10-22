package weboffice.workbook;

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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 별표(즐겨찾기)와 색 딱지. 개인 것이라 남에게 보이지 않아야 한다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WorkbookMarkTest {

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

    @MockitoBean
    private UserDirectory userDirectory;

    @BeforeEach
    void 데이터를_비우고_로그인한다() {
        DbTestSupport.deleteAll(jdbcTemplate);
        loggedInAs("me");
        when(userDirectory.findByKeys(any())).thenReturn(List.of(
                new DirectoryUser("me", "me-id", "나", "사원", "D1", "전산팀"),
                new DirectoryUser("other", "other-id", "홍길동", "과장", "D2", "영업팀")));
    }

    @Test
    void 별표와_색을_달면_목록에_실린다() throws Exception {
        Long mine = 문서를_만든다("내 문서", "me");

        표시한다(mine, true, "RED");

        mockMvc.perform(get("/api/workbooks"))
                .andExpect(jsonPath("$.mine[0].starred").value(true))
                .andExpect(jsonPath("$.mine[0].color").value("RED"));
    }

    @Test
    void 표시를_떼면_목록에서도_빠진다() throws Exception {
        Long mine = 문서를_만든다("내 문서", "me");
        표시한다(mine, true, "RED");

        표시한다(mine, false, null);

        mockMvc.perform(get("/api/workbooks"))
                .andExpect(jsonPath("$.mine[0].starred").value(false))
                .andExpect(jsonPath("$.mine[0].color").doesNotExist());
    }

    @Test
    void 별표만_떼고_색은_남길_수_있다() throws Exception {
        // 두 값이 한 행이라 한쪽을 고칠 때 다른 쪽이 지워지기 쉽다.
        Long mine = 문서를_만든다("내 문서", "me");
        표시한다(mine, true, "BLUE");

        표시한다(mine, false, "BLUE");

        mockMvc.perform(get("/api/workbooks"))
                .andExpect(jsonPath("$.mine[0].starred").value(false))
                .andExpect(jsonPath("$.mine[0].color").value("BLUE"));
    }

    @Test
    void 내_표시는_남의_목록에_보이지_않는다() throws Exception {
        // 색이 공용이면 남이 칠한 색이 내 목록을 어지럽힌다.
        Long shared = 문서를_만든다("남의 문서", "other");
        memberMapper.insertMember(shared, "me", "EDITOR", "other");
        표시한다(shared, true, "GREEN");

        loggedInAs("other");

        mockMvc.perform(get("/api/workbooks"))
                .andExpect(jsonPath("$.mine[0].starred").value(false))
                .andExpect(jsonPath("$.mine[0].color").doesNotExist());
    }

    @Test
    void 공유받은_남의_문서에도_내_표시를_단다() throws Exception {
        Long shared = 문서를_만든다("남의 문서", "other");
        memberMapper.insertMember(shared, "me", "EDITOR", "other");

        표시한다(shared, true, null);

        mockMvc.perform(get("/api/workbooks"))
                .andExpect(jsonPath("$.shared[0].starred").value(true));
    }

    @Test
    void 볼_수_없는_문서에는_표시하지_못한다() throws Exception {
        Long theirs = 문서를_만든다("남의 문서", "other");

        mockMvc.perform(put("/api/workbooks/{seq}/mark", theirs)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"starred\":true,\"color\":null}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void 모르는_색은_안_칠한_것으로_받는다() throws Exception {
        // 화면이 보내는 값은 우리가 준 목록에서 온다. 오류로 막을 자리가 아니다.
        Long mine = 문서를_만든다("내 문서", "me");

        표시한다(mine, true, "RAINBOW");

        mockMvc.perform(get("/api/workbooks"))
                .andExpect(jsonPath("$.mine[0].color").doesNotExist());
    }

    @Test
    void 찾기_결과에도_표시가_실린다() throws Exception {
        Long mine = 문서를_만든다("찾을 문서", "me");
        표시한다(mine, true, "YELLOW");

        mockMvc.perform(get("/api/workbooks/search").param("q", "찾을"))
                .andExpect(jsonPath("$.mine[0].starred").value(true))
                .andExpect(jsonPath("$.mine[0].color").value("YELLOW"));
    }

    @Test
    void 색_이름은_일곱_개가_기본_이름으로_온다() throws Exception {
        mockMvc.perform(get("/api/workbook-colors"))
                .andExpect(jsonPath("$.length()").value(7))
                .andExpect(jsonPath("$[0].colorCode").value("RED"))
                .andExpect(jsonPath("$[0].labelName").value("빨강"));
    }

    @Test
    void 색에_내_이름을_붙인다() throws Exception {
        mockMvc.perform(put("/api/workbook-colors/{color}", "RED")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"급함\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/workbook-colors"))
                .andExpect(jsonPath("$[0].labelName").value("급함"));
    }

    @Test
    void 이름을_비우면_기본_이름으로_돌아간다() throws Exception {
        이름을_붙인다("RED", "급함");

        이름을_붙인다("RED", "  ");

        mockMvc.perform(get("/api/workbook-colors"))
                .andExpect(jsonPath("$[0].labelName").value("빨강"));
    }

    @Test
    void 색_이름도_사람마다_다르다() throws Exception {
        이름을_붙인다("RED", "급함");

        loggedInAs("other");

        mockMvc.perform(get("/api/workbook-colors"))
                .andExpect(jsonPath("$[0].labelName").value("빨강"));
    }

    private void 표시한다(Long workbookSeq, boolean starred, String color) throws Exception {
        String body = "{\"starred\":" + starred + ",\"color\":"
                + (color == null ? "null" : "\"" + color + "\"") + "}";
        mockMvc.perform(put("/api/workbooks/{seq}/mark", workbookSeq)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNoContent());
    }

    private void 이름을_붙인다(String color, String name) throws Exception {
        mockMvc.perform(put("/api/workbook-colors/{color}", color)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isNoContent());
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
