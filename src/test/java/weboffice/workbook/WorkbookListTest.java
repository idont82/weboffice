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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WorkbookListTest {

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
    void 내_문서와_공유받은_문서를_나눠_준다() throws Exception {
        Long mine = 문서를_만든다("내 문서", "me");
        Long shared = 문서를_만든다("남의 문서", "other");
        memberMapper.insertMember(shared, "me", "EDITOR", "other");

        mockMvc.perform(get("/api/workbooks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mine.length()").value(1))
                .andExpect(jsonPath("$.mine[0].workbookSeq").value(mine))
                .andExpect(jsonPath("$.mine[0].workbookName").value("내 문서"))
                .andExpect(jsonPath("$.shared.length()").value(1))
                .andExpect(jsonPath("$.shared[0].workbookSeq").value(shared))
                .andExpect(jsonPath("$.shared[0].ownerName").value("홍길동"))
                .andExpect(jsonPath("$.shared[0].role").value("EDITOR"));
    }

    @Test
    void 사번은_목록에_나오지_않는다() throws Exception {
        Long shared = 문서를_만든다("남의 문서", "other");
        memberMapper.insertMember(shared, "me", "EDITOR", "other");

        String body = mockMvc.perform(get("/api/workbooks"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // 소유자 사번이 새기 쉬운 자리다. 이름만 나가야 한다.
        assertThat(body).doesNotContain("\"other\"");
    }

    @Test
    void 공유_인원과_공개_여부를_알려준다() throws Exception {
        Long mine = 문서를_만든다("내 문서", "me");
        memberMapper.insertMember(mine, "a", "EDITOR", "me");
        memberMapper.insertMember(mine, "b", "VIEWER", "me");
        workbookMapper.updateShareToken(mine, "t".repeat(32), "me");

        mockMvc.perform(get("/api/workbooks"))
                .andExpect(jsonPath("$.mine[0].memberCount").value(2))
                .andExpect(jsonPath("$.mine[0].shared").value(true));
    }

    @Test
    void 공개_토큰_자체는_목록에_실리지_않는다() throws Exception {
        // 목록은 여럿에게 보이는 화면이다. 토큰이 실리면 공개 링크가 새어 나간다.
        Long mine = 문서를_만든다("내 문서", "me");
        workbookMapper.updateShareToken(mine, "z".repeat(32), "me");

        String body = mockMvc.perform(get("/api/workbooks"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("z".repeat(32));
    }

    @Test
    void 남의_문서는_보이지_않는다() throws Exception {
        문서를_만든다("남의 문서", "other");

        mockMvc.perform(get("/api/workbooks"))
                .andExpect(jsonPath("$.mine.length()").value(0))
                .andExpect(jsonPath("$.shared.length()").value(0));
    }

    @Test
    void 소유자가_구성원으로도_있으면_한_번만_나온다() throws Exception {
        // 두 목록에 모두 나오면 화면에서 같은 문서가 두 줄이 된다.
        Long mine = 문서를_만든다("내 문서", "me");
        memberMapper.insertMember(mine, "me", "EDITOR", "me");

        mockMvc.perform(get("/api/workbooks"))
                .andExpect(jsonPath("$.mine.length()").value(1))
                .andExpect(jsonPath("$.shared.length()").value(0));
    }

    @Test
    void 빈_문서를_만들면_시트가_하나_딸려_온다() throws Exception {
        // 시트 없는 문서를 만들면 화면이 열 것이 없어 빈 그리드만 뜬다.
        String body = mockMvc.perform(post("/api/workbooks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"workbookName\":\"새 문서\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workbookName").value("새 문서"))
                .andExpect(jsonPath("$.sheets.length()").value(1))
                .andReturn().getResponse().getContentAsString();

        Number seq = com.jayway.jsonpath.JsonPath.read(body, "$.workbookSeq");
        // 만든 사람이 소유자여야 자기 문서에 들어갈 수 있다.
        mockMvc.perform(get("/api/workbooks/{seq}", seq))
                .andExpect(jsonPath("$.role").value("OWNER"));
    }

    @Test
    void 이름_없이_문서를_만들면_기본_이름을_붙인다() throws Exception {
        // 제목이 비면 목록에서 어느 문서인지 알 수 없다.
        mockMvc.perform(post("/api/workbooks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"workbookName\":\"  \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workbookName").value("제목 없는 문서"));
    }

    @Test
    void 만든_문서는_목록_맨_앞에_온다() throws Exception {
        문서를_만든다("옛 문서", "me");

        mockMvc.perform(post("/api/workbooks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"workbookName\":\"새 문서\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/workbooks"))
                .andExpect(jsonPath("$.mine[0].workbookName").value("새 문서"));
    }

    @Test
    void 문서_한_건은_제목과_내_역할을_준다() throws Exception {
        // 화면이 workbookSeq 만 들고 들어와도 제목을 띄울 수 있어야 한다.
        Long shared = 문서를_만든다("남의 문서", "other");
        memberMapper.insertMember(shared, "me", "VIEWER", "other");

        mockMvc.perform(get("/api/workbooks/{seq}", shared))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workbookName").value("남의 문서"))
                .andExpect(jsonPath("$.ownerName").value("홍길동"))
                .andExpect(jsonPath("$.role").value("VIEWER"));
    }

    @Test
    void 권한이_없으면_문서_한_건도_주지_않는다() throws Exception {
        Long other = 문서를_만든다("남의 문서", "other");

        mockMvc.perform(get("/api/workbooks/{seq}", other))
                .andExpect(status().isForbidden());
    }

    @Test
    void 문서_한_건에도_사번과_토큰은_없다() throws Exception {
        Long mine = 문서를_만든다("내 문서", "me");
        workbookMapper.updateShareToken(mine, "z".repeat(32), "me");

        String body = mockMvc.perform(get("/api/workbooks/{seq}", mine))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("z".repeat(32));
        assertThat(body).doesNotContain("\"me\"");
    }

    @Test
    void 목록에_수정_일시가_실린다() throws Exception {
        // 언제 손댄 문서인지 보이지 않으면 이름만으로 골라야 한다.
        문서를_만든다("내 문서", "me");

        mockMvc.perform(get("/api/workbooks"))
                .andExpect(jsonPath("$.mine[0].modifiedAt").isNotEmpty());
    }

    @Test
    void 소유자는_문서를_지운다() throws Exception {
        Long mine = 문서를_만든다("버릴 문서", "me");

        mockMvc.perform(delete("/api/workbooks/{seq}", mine))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/workbooks"))
                .andExpect(jsonPath("$.mine.length()").value(0));
    }

    @Test
    void 지운_문서는_열리지_않는다() throws Exception {
        // 목록에서만 빠지고 주소로는 열리면 지운 것이 아니다.
        Long mine = 문서를_만든다("버릴 문서", "me");
        mockMvc.perform(delete("/api/workbooks/{seq}", mine))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/workbooks/{seq}", mine))
                .andExpect(status().isForbidden());
    }

    @Test
    void 편집자는_문서를_지우지_못한다() throws Exception {
        // 지우기는 되돌릴 수 없다. 초대받은 사람이 남의 문서를 없애면 안 된다.
        Long shared = 문서를_만든다("남의 문서", "other");
        memberMapper.insertMember(shared, "me", "EDITOR", "other");

        mockMvc.perform(delete("/api/workbooks/{seq}", shared))
                .andExpect(status().isForbidden());
    }

    @Test
    void 지우면_공개_링크도_함께_끊긴다() throws Exception {
        // 문서를 지웠는데 링크로 계속 열리면 지운 것이 아니다.
        Long mine = 문서를_만든다("버릴 문서", "me");
        workbookMapper.updateShareToken(mine, "t".repeat(32), "me");

        mockMvc.perform(delete("/api/workbooks/{seq}", mine))
                .andExpect(status().isNoContent());

        assertThat(workbookMapper.selectWorkbookByShareToken("t".repeat(32))).isNull();
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
