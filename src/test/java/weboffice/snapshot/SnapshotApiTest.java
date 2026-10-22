package weboffice.snapshot;

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

import weboffice.common.AuditUser;
import weboffice.security.DirectoryUser;
import weboffice.security.UserDirectory;
import weboffice.security.LoginUser;
import weboffice.security.Authenticator;
import weboffice.support.DbTestSupport;
import weboffice.workbook.DocType;
import weboffice.workbook.MemberMapper;
import weboffice.workbook.WorkbookService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 복구 지점 API 와 권한. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SnapshotApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private WorkbookService workbookService;

    @Autowired
    private SnapshotService snapshotService;

    @Autowired
    private MemberMapper memberMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private Authenticator authenticator;

    @MockitoBean
    private UserDirectory userDirectory;

    private Long workbookSeq;

    @BeforeEach
    void 문서를_하나_만든다() {
        DbTestSupport.deleteAll(jdbcTemplate);
        loggedInAs("me");
        when(userDirectory.findByKeys(any())).thenReturn(List.of(
                new DirectoryUser("me", "me-id", "나", "사원", "D1", "전산팀")));
        workbookSeq = workbookService
                .createBlank("권한 시험", DocType.SHEET, AuditUser.of("me")).workbookSeq();
    }

    @Test
    void 찍고_목록에서_본다() throws Exception {
        mockMvc.perform(post("/api/workbooks/{seq}/snapshots", workbookSeq)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"보내기 전\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.snapshotSeq").isNumber());

        mockMvc.perform(get("/api/workbooks/{seq}/snapshots", workbookSeq))
                .andExpect(jsonPath("$[0].label").value("보내기 전"))
                .andExpect(jsonPath("$[0].reason").value("MANUAL"))
                .andExpect(jsonPath("$[0].takenBy").value("나"));
    }

    @Test
    void 사번은_목록에_안_나온다() throws Exception {
        snapshotService.take(workbookSeq, "MANUAL", null, AuditUser.of("me"));

        String body = mockMvc.perform(get("/api/workbooks/{seq}/snapshots", workbookSeq))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("\"me\"");
    }

    @Test
    void 서버가_쓰는_이유는_사람이_못_부른다() throws Exception {
        // BEFORE_RESTORE 는 되돌리기가 스스로 남기는 것이다. 손으로 부르면 목록에서
        // 무엇이 사고 직전 모습인지 구별할 수 없게 된다.
        mockMvc.perform(post("/api/workbooks/{seq}/snapshots", workbookSeq)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"BEFORE_RESTORE\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/workbooks/{seq}/snapshots", workbookSeq))
                .andExpect(jsonPath("$[0].reason").value("MANUAL"));
    }

    @Test
    void AI_가_고치기_전_이유는_받는다() throws Exception {
        mockMvc.perform(post("/api/workbooks/{seq}/snapshots", workbookSeq)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"BEFORE_AI\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/workbooks/{seq}/snapshots", workbookSeq))
                .andExpect(jsonPath("$[0].reason").value("BEFORE_AI"));
    }

    @Test
    void 볼_수만_있는_사람은_못_찍는다() throws Exception {
        memberMapper.insertMember(workbookSeq, "guest", "VIEWER", "me");
        loggedInAs("guest");

        mockMvc.perform(post("/api/workbooks/{seq}/snapshots", workbookSeq)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void 볼_수만_있는_사람은_제자리_복원을_못_한다() throws Exception {
        Long point = snapshotService.take(workbookSeq, "MANUAL", null, AuditUser.of("me"));
        memberMapper.insertMember(workbookSeq, "guest", "VIEWER", "me");
        loggedInAs("guest");

        mockMvc.perform(post("/api/snapshots/{seq}/restore", point))
                .andExpect(status().isForbidden());
    }

    @Test
    void 볼_수만_있는_사람도_새_문서로는_복원한다() throws Exception {
        // 원본을 안 건드리고 자기 문서로 만드는 것이라 읽기 권한이면 된다.
        Long point = snapshotService.take(workbookSeq, "MANUAL", null, AuditUser.of("me"));
        memberMapper.insertMember(workbookSeq, "guest", "VIEWER", "me");
        loggedInAs("guest");

        mockMvc.perform(post("/api/snapshots/{seq}/copy", point))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workbookSeq").isNumber());
    }

    @Test
    void 남의_문서_복구_지점은_아예_못_본다() throws Exception {
        Long point = snapshotService.take(workbookSeq, "MANUAL", null, AuditUser.of("me"));
        loggedInAs("stranger");

        mockMvc.perform(get("/api/workbooks/{seq}/snapshots", workbookSeq))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/snapshots/{seq}/copy", point))
                .andExpect(status().isForbidden());
    }

    @Test
    void 버전_되돌리기는_직전_모습을_남긴다() throws Exception {
        // 버전 되돌리기는 셀과 문단만 되돌린다. 얼개가 안 돌아온 것을 나중에 알아도
        // 되돌아갈 자리가 있어야 한다.
        mockMvc.perform(post("/api/workbooks/{seq}/versions/{versionId}/restore",
                        workbookSeq, System.currentTimeMillis()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/workbooks/{seq}/snapshots", workbookSeq))
                .andExpect(jsonPath("$[0].reason").value("BEFORE_RESTORE"));
    }

    private void loggedInAs(String userKey) {
        when(authenticator.authenticate(any(), any()))
                .thenReturn(new LoginUser(userKey + "-id", userKey, userKey, "사원", "D1", "전산팀", "ko"));
    }
}
