package weboffice.lock;

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

import weboffice.realtime.RealtimeHub;
import weboffice.security.LoginUser;
import weboffice.security.Authenticator;
import weboffice.sheet.CellRange;
import weboffice.sheet.SheetMapper;
import weboffice.sheet.SheetSummaryDto;
import weboffice.support.DbTestSupport;
import weboffice.workbook.MemberMapper;
import weboffice.workbook.WorkbookDto;
import weboffice.workbook.WorkbookMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 편집 위치 공유(presence).
 *
 * <p>잠금은 막는 것이 아니라 알리는 것이다. 겹쳐도 둘 다 잡히고, 나가는 자료에
 * 사번이 없어야 한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PresenceTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LockService lockService;

    @Autowired
    private LockMapper lockMapper;

    @Autowired
    private WorkbookMapper workbookMapper;

    @Autowired
    private MemberMapper memberMapper;

    @Autowired
    private SheetMapper sheetMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private Authenticator authenticator;

    /** 브로드캐스트를 가로채 무엇이 나갔는지 본다. 사번이 실리면 여기서 잡힌다. */
    @MockitoBean
    private RealtimeHub realtimeHub;

    private Long sheetSeq;

    @BeforeEach
    void 문서를_만들고_로그인한다() {
        DbTestSupport.deleteAll(jdbcTemplate);
        WorkbookDto workbook = new WorkbookDto(null, "문서", "20502534", null, null);
        workbookMapper.insertWorkbook(workbook, "20502534");
        memberMapper.insertMember(workbook.workbookSeq(), "21312213", "EDITOR", "20502534");

        SheetSummaryDto sheet =
                new SheetSummaryDto(null, workbook.workbookSeq(), "시트1", 0, 100, 20);
        sheetMapper.insertSheet(sheet, "20502534");
        sheetSeq = sheet.sheetSeq();

        loggedInAs("20502534", "admin1", "홍길동");
    }

    @Test
    void 겹치는_범위도_둘_다_잡는다() {
        // 잠금이 아니라 표시다. 남이 보고 있다고 내가 못 쓰면 그것은 협업이 아니다.
        lockService.acquire(sheetSeq, new CellRange(0, 0, 4, 4), "20502534");

        lockService.acquire(sheetSeq, new CellRange(2, 2, 6, 6), "21312213");

        assertThat(lockMapper.selectActiveLocks(sheetSeq)).hasSize(2);
    }

    @Test
    void 같은_사람의_두_창은_자리가_둘이다() {
        // 자리는 사람이 아니라 세션 단위다. 사번으로 하나만 남기면 두 창을 띄운
        // 사람이 자기 다른 창의 자리를 지워 아무것도 안 보인다(2026-08-06 실측).
        lockService.acquire(sheetSeq, new CellRange(0, 0, 0, 0), "20502534");

        lockService.acquire(sheetSeq, new CellRange(5, 5, 5, 5), "20502534");

        assertThat(lockMapper.selectActiveLocks(sheetSeq)).hasSize(2);
    }

    @Test
    void 같은_탭이_다시_잡으면_앞_자리를_지운다() {
        // 새로고침한 탭의 옛 자리가 TTL 동안 남으면 남들 화면에 유령이 보인다.
        lockService.acquire(sheetSeq, new CellRange(0, 0, 0, 0), "20502534", "tab-1");

        lockService.acquire(sheetSeq, new CellRange(5, 5, 5, 5), "20502534", "tab-1");

        assertThat(lockMapper.selectActiveLocks(sheetSeq)).hasSize(1);
    }

    @Test
    void 다른_탭의_자리는_남긴다() {
        // 같은 사람이라도 창이 둘이면 자리도 둘이다.
        lockService.acquire(sheetSeq, new CellRange(0, 0, 0, 0), "20502534", "tab-1");

        lockService.acquire(sheetSeq, new CellRange(5, 5, 5, 5), "20502534", "tab-2");

        assertThat(lockMapper.selectActiveLocks(sheetSeq)).hasSize(2);
    }

    @Test
    void 탭마다_다른_세션_키를_그대로_돌려준다() throws Exception {
        // 색을 사람이 아니라 탭 단위로 나누기 위한 것이다. 같은 사람이 두 창을
        // 띄우면 색도 둘이어야 한다.
        String body = mockMvc.perform(post("/api/locks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sheetSeq":%d,"fromRowNo":1,"fromColumnNo":1,
                                 "toRowNo":1,"toColumnNo":1,"sessionKey":"tab-abc"}"""
                                .formatted(sheetSeq)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("tab-abc");
        // 요청이 끝나면 인터셉터가 ThreadLocal 을 비운다. 서비스를 직접 부르려면 다시 채운다.
        loggedInAs("20502534", "admin1", "홍길동");
        // 첫 화면을 그릴 때도 같은 키가 나와야 색이 안 바뀐다.
        assertThat(lockService.listFor(sheetSeq))
                .extracting(PresenceDto::sessionKey).containsExactly("tab-abc");
    }

    @Test
    void 세션_키가_없어도_자리는_잡힌다() {
        // 옛 화면이나 스크립트가 보낸 요청이다. 색만 사람 단위로 물러선다.
        PresenceDto seat = lockService.acquire(sheetSeq, new CellRange(0, 0, 0, 0), "20502534");

        assertThat(seat.sessionKey()).isNull();
    }

    @Test
    void 페이지를_떠날_때_보낸_해제도_받는다() throws Exception {
        // navigator.sendBeacon 은 POST 만 보낸다. DELETE 만 열어 두면 창을 닫은
        // 사람의 자리가 만료까지 남아 "작성 중" 으로 보인다(2026-08-06 지적).
        PresenceDto seat = lockService.acquire(sheetSeq, new CellRange(1, 1, 1, 1), "20502534");

        mockMvc.perform(post("/api/locks/{lockSeq}/release", seat.lockSeq()))
                .andExpect(status().isNoContent());

        assertThat(lockMapper.selectActiveLocks(sheetSeq)).isEmpty();
    }

    @Test
    void 응답에_사번이_없다() throws Exception {
        String body = mockMvc.perform(post("/api/locks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sheetSeq":%d,"fromRowNo":1,"fromColumnNo":1,
                                 "toRowNo":2,"toColumnNo":2}""".formatted(sheetSeq)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("20502534");
        assertThat(body).contains("admin1");
    }

    @Test
    void 브로드캐스트에_사번이_없다() {
        lockService.acquire(sheetSeq, new CellRange(1, 1, 2, 2), "20502534");

        org.mockito.ArgumentCaptor<PresenceDto> captor =
                org.mockito.ArgumentCaptor.forClass(PresenceDto.class);
        org.mockito.Mockito.verify(realtimeHub).presenceSet(captor.capture());

        PresenceDto sent = captor.getValue();
        assertThat(sent.userId()).isEqualTo("admin1");
        assertThat(sent.displayName()).isEqualTo("홍길동");
        // record 전체를 문자열로 만들어 사번이 어느 필드로도 안 새는지 본다.
        assertThat(sent.toString()).doesNotContain("20502534");
    }

    @Test
    void 이름을_모르면_아이디만_보낸다() {
        // 인명부가 없는 환경이다. presence 가 안 뜨는 것보다 이름 없이라도 뜨는 게 낫다.
        loggedInAs("21312213", "kse0381", null);

        lockService.acquire(sheetSeq, new CellRange(3, 3, 3, 3), "21312213");

        org.mockito.ArgumentCaptor<PresenceDto> captor =
                org.mockito.ArgumentCaptor.forClass(PresenceDto.class);
        org.mockito.Mockito.verify(realtimeHub).presenceSet(captor.capture());
        assertThat(captor.getValue().userId()).isEqualTo("kse0381");
        assertThat(captor.getValue().displayName()).isNull();
    }

    private void loggedInAs(String userKey, String userId, String displayName) {
        LoginUser user = new LoginUser(userId, userKey, displayName, "책임", "D1", "전산팀", "ko");
        when(authenticator.authenticate(any(), any())).thenReturn(user);
        weboffice.security.UserContext.bindToRequest(user);
    }
}
