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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MemberControllerTest {

    private static final DirectoryUser HONG =
            new DirectoryUser("12345678", "hong", "홍길동", "과장", "D100", "전산팀");

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
        when(userDirectory.findByUserId("hong")).thenReturn(HONG);
        when(userDirectory.findByKeys(any())).thenReturn(List.of(HONG));
    }

    @Test
    void 아이디로_구성원을_넣고_이름으로_돌려받는다() throws Exception {
        // 넣을 때는 USER_ID, 저장은 사번, 볼 때는 이름이다(설계 3절).
        Long seq = 문서를_만든다("내 문서", "me");

        put구성원(seq, "hong", "EDITOR");

        String body = mockMvc.perform(get("/api/workbooks/{seq}/members", seq))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userId").value("hong"))
                .andExpect(jsonPath("$[0].displayName").value("홍길동"))
                .andExpect(jsonPath("$[0].rank").value("과장"))
                .andExpect(jsonPath("$[0].deptName").value("전산팀"))
                .andExpect(jsonPath("$[0].role").value("EDITOR"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("12345678");
        // DB 에는 사번으로 들어가야 감사·잠금과 아귀가 맞는다.
        assertThat(memberMapper.selectRole(seq, "12345678")).isEqualTo("EDITOR");
    }

    @Test
    void 같은_사람을_다시_넣으면_역할이_바뀐다() throws Exception {
        // 중복 오류가 아니라 역할 변경이어야 화면에서 다루기 쉽다(설계 9절).
        Long seq = 문서를_만든다("내 문서", "me");

        put구성원(seq, "hong", "EDITOR");
        put구성원(seq, "hong", "VIEWER");

        assertThat(memberMapper.selectRole(seq, "12345678")).isEqualTo("VIEWER");
    }

    @Test
    void 뺐다가_다시_넣으면_되살아난다() throws Exception {
        // 소프트 삭제를 되돌리지 않으면 조회에서 사라진 채로 남는다.
        Long seq = 문서를_만든다("내 문서", "me");
        put구성원(seq, "hong", "EDITOR");
        mockMvc.perform(delete("/api/workbooks/{seq}/members/{userId}", seq, "hong"))
                .andExpect(status().isOk());
        assertThat(memberMapper.selectRole(seq, "12345678")).isNull();

        put구성원(seq, "hong", "VIEWER");

        assertThat(memberMapper.selectRole(seq, "12345678")).isEqualTo("VIEWER");
    }

    @Test
    void 인명부에_없는_아이디는_거부한다() throws Exception {
        when(userDirectory.findByUserId("nobody")).thenReturn(null);
        Long seq = 문서를_만든다("내 문서", "me");

        mockMvc.perform(put("/api/workbooks/{seq}/members", seq)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"nobody\",\"role\":\"EDITOR\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 소유자가_아니면_구성원을_바꾸지_못한다() throws Exception {
        // 편집자가 남을 초대할 수 있으면 소유자가 통제를 잃는다(설계 9절).
        Long seq = 문서를_만든다("남의 문서", "other");
        memberMapper.insertMember(seq, "me", "EDITOR", "other");

        mockMvc.perform(put("/api/workbooks/{seq}/members", seq)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"hong\",\"role\":\"EDITOR\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void 알_수_없는_역할은_거부한다() throws Exception {
        // ROLE_CODE 에 아무 문자열이나 들어가면 나중에 권한 판정에서 터진다.
        Long seq = 문서를_만든다("내 문서", "me");

        mockMvc.perform(put("/api/workbooks/{seq}/members", seq)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"hong\",\"role\":\"SUPERUSER\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 소유자_권한은_부여하지_못한다() throws Exception {
        Long seq = 문서를_만든다("내 문서", "me");

        mockMvc.perform(put("/api/workbooks/{seq}/members", seq)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"hong\",\"role\":\"OWNER\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 소유자를_구성원으로_넣지_못한다() throws Exception {
        // 넣으면 목록에 두 번 나오고 역할이 둘이 된다.
        when(userDirectory.findByUserId("me-id"))
                .thenReturn(new DirectoryUser("me", "me-id", "나", "사원", "D1", "전산팀"));
        Long seq = 문서를_만든다("내 문서", "me");

        mockMvc.perform(put("/api/workbooks/{seq}/members", seq)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"me-id\",\"role\":\"VIEWER\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 구성원은_읽기_권한이면_볼_수_있다() throws Exception {
        Long seq = 문서를_만든다("남의 문서", "other");
        memberMapper.insertMember(seq, "me", "VIEWER", "other");

        mockMvc.perform(get("/api/workbooks/{seq}/members", seq))
                .andExpect(status().isOk());
    }

    private void put구성원(Long seq, String userId, String role) throws Exception {
        mockMvc.perform(put("/api/workbooks/{seq}/members", seq)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"%s\",\"role\":\"%s\"}".formatted(userId, role)))
                .andExpect(status().isOk());
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
