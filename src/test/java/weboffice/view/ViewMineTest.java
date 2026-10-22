package weboffice.view;

import java.io.ByteArrayOutputStream;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import weboffice.security.UserDirectory;
import weboffice.security.LoginUser;
import weboffice.security.Authenticator;
import weboffice.support.DbTestSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 우리 화면에서 파일을 잠깐 보는 길.
 *
 * <p>남의 시스템 길(`/api/view/documents`)은 주소로 통제한다. 우리 화면이 그것을
 * 그대로 부르면 <b>사용자 PC 주소</b>로 나가 대부분 막힌다 - 사내 직원 PC 대역을
 * 다 열면 그 통제가 무의미해진다. 그래서 로그인한 사람 전용 길을 따로 둔다
 * (2026-08-20 결정).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ViewMineTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private Authenticator authenticator;

    @MockitoBean
    private UserDirectory userDirectory;

    @BeforeEach
    void 비우고_로그인한다() {
        DbTestSupport.deleteAll(jdbcTemplate);
        loggedInAs("admin1", "홍길동", "책임");
    }

    private static byte[] 엑셀한장() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            workbook.createSheet("시트1").createRow(0).createCell(0).setCellValue("값");
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private org.springframework.test.web.servlet.ResultActions 보기발급(String fileName)
            throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", fileName,
                "application/octet-stream", 엑셀한장());
        return mockMvc.perform(multipart("/api/view/mine")
                .file(file)
                .param("fileName", fileName));
    }

    @Test
    void 로그인한_사람은_주소와_상관없이_발급받는다() throws Exception {
        보기발급("보고서.xlsx")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.viewUrl").exists())
                .andExpect(jsonPath("$.docType").value("SHEET"));
    }

    @Test
    void 이력에_웹오피스와_로그인한_사람_이름이_남는다() throws Exception {
        보기발급("보고서.xlsx").andExpect(status().isOk());

        String caller = jdbcTemplate.queryForObject(
                "SELECT CALLER_SYSTEM FROM wo_view_event_log WHERE EVENT_TYPE = 'ISSUE'",
                String.class);
        String viewer = jdbcTemplate.queryForObject(
                "SELECT VIEWER_NAME FROM wo_view_event_log WHERE EVENT_TYPE = 'ISSUE'",
                String.class);

        assertThat(caller).isEqualTo("웹오피스");
        assertThat(viewer).isEqualTo("홍길동 책임");
    }

    @Test
    void 사번은_이력에도_안_적는다() throws Exception {
        boolean 사번있음 = Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM wo_view_event_log WHERE VIEWER_NAME LIKE '%12345%'",
                Integer.class) > 0);
        보기발급("보고서.xlsx").andExpect(status().isOk());

        assertThat(사번있음).isFalse();
        Integer 사번줄 = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM wo_view_event_log WHERE VIEWER_NAME LIKE '%12345%'",
                Integer.class);
        assertThat(사번줄).isZero();
    }

    @Test
    void 못_여는_형식은_415() throws Exception {
        보기발급("사진.png").andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void 로그인하지_않으면_못_쓴다() throws Exception {
        // 이 길에는 주소 관문이 없다. 로그인마저 없으면 아무나 우리 서버로 파일을
        // 올려 변환시킬 수 있다.
        when(authenticator.authenticate(any(), any())).thenReturn(null);

        보기발급("보고서.xlsx").andExpect(status().isUnauthorized());
    }

    private void loggedInAs(String userId, String name, String rank) {
        when(authenticator.authenticate(any(), any()))
                .thenReturn(new LoginUser(userId, "12345", name, rank, "D1",
                        "전산팀", "ko"));
    }
}
