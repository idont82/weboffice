package weboffice.drawing;

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
import weboffice.security.LoginUser;
import weboffice.security.Authenticator;
import weboffice.sheet.SheetMapper;
import weboffice.sheet.SheetSummaryDto;
import weboffice.support.DbTestSupport;
import weboffice.workbook.MemberMapper;
import weboffice.workbook.WorkbookDto;
import weboffice.workbook.WorkbookMapper;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DrawingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ImageService imageService;

    @Autowired
    private SheetMapper sheetMapper;

    @Autowired
    private WorkbookMapper workbookMapper;

    @Autowired
    private MemberMapper memberMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private Authenticator authenticator;

    private Long workbookSeq;
    private Long sheetSeq;
    private Long imageSeq;

    @BeforeEach
    void 문서와_시트와_이미지를_만들고_로그인한다() {
        DbTestSupport.deleteAll(jdbcTemplate);
        loggedInAs("owner1");

        WorkbookDto workbook = new WorkbookDto(null, "그림 문서", "owner1", null, null);
        workbookMapper.insertWorkbook(workbook, "owner1");
        workbookSeq = workbook.workbookSeq();

        SheetSummaryDto sheet = new SheetSummaryDto(null, workbookSeq, "시트1", 0, 0, 0);
        sheetMapper.insertSheet(sheet, "owner1");
        sheetSeq = sheet.sheetSeq();

        imageSeq = imageService.store(workbookSeq, TestImages.png(), AuditUser.of("owner1"));
    }

    @Test
    void 그림을_저장하고_읽는다() throws Exception {
        mockMvc.perform(put("/api/sheets/{seq}/drawings", sheetSeq)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(델타(imageSeq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.savedCount").value(1));

        mockMvc.perform(get("/api/sheets/{seq}/drawings", sheetSeq))
                .andExpect(status().isOk())
                // 이 단정이 {} 를 잡는다. record 는 문제없지만 응답 타입이 가변
                // 클래스로 바뀌면 @JsonAutoDetect 없이 조용히 빈 객체가 나간다.
                .andExpect(jsonPath("$[0].drawingId").value("dw-1"))
                .andExpect(jsonPath("$[0].fromRowNo").value(1))
                .andExpect(jsonPath("$[0].toColumnNo").value(4))
                .andExpect(jsonPath("$[0].imageSeq").value(imageSeq));
    }

    @Test
    void 한쪽만_보내도_받는다() {
        // deletedIds 를 빼고 보내도 NPE 가 나면 안 된다.
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() ->
                mockMvc.perform(put("/api/sheets/{seq}/drawings", sheetSeq)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"upserted\":[]}"))
                        .andExpect(status().isOk()));
    }

    @Test
    void 읽기_전용_사용자는_저장하지_못한다() throws Exception {
        memberMapper.insertMember(workbookSeq, "reader1", "VIEWER", "owner1");
        loggedInAs("reader1");

        mockMvc.perform(put("/api/sheets/{seq}/drawings", sheetSeq)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(델타(imageSeq)))
                .andExpect(status().isForbidden());
    }

    @Test
    void 읽기_전용_사용자도_읽을_수는_있다() throws Exception {
        memberMapper.insertMember(workbookSeq, "reader1", "VIEWER", "owner1");
        loggedInAs("reader1");

        mockMvc.perform(get("/api/sheets/{seq}/drawings", sheetSeq))
                .andExpect(status().isOk());
    }

    @Test
    void 구성원이_아니면_읽지_못한다() throws Exception {
        loggedInAs("stranger");

        mockMvc.perform(get("/api/sheets/{seq}/drawings", sheetSeq))
                .andExpect(status().isForbidden());
    }

    private String 델타(Long imageSeq) {
        return """
                {"upserted":[{"drawingId":"dw-1","imageSeq":%d,
                  "fromRowNo":1,"fromRowOffset":0,
                  "fromColumnNo":2,"fromColumnOffset":0,
                  "toRowNo":6,"toRowOffset":0,
                  "toColumnNo":4,"toColumnOffset":0,
                  "anchorType":"TWO_CELL","zOrder":0,"drawingExtra":null}],
                 "deletedIds":[]}
                """.formatted(imageSeq);
    }

    private void loggedInAs(String userKey) {
        when(authenticator.authenticate(any(), any()))
                .thenReturn(new LoginUser(userKey, userKey, userKey, "사원", "D100", "전산팀", "ko"));
    }
}
