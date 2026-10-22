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
import weboffice.security.Authenticator;
import weboffice.support.DbTestSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 주소만으로 부르는 시스템을 가린다.
 *
 * <p>열쇠를 나눠 주고 관리하는 값보다 사내망 대역으로 조이는 쪽이 낫다는 판단이다
 * (2026-08-20 결정). 열쇠를 적어 둔 시스템은 여전히 열쇠도 함께 본다.
 *
 * <p>시험 설정(application-test.yml)에 주소만 적은 시스템 하나가 있어야 한다:
 * 이름 "사내서버", 주소 192.0.2.0/24 · 198.51.100.0/24.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CallerAddressTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private Authenticator authenticator;

    @MockitoBean
    private UserDirectory userDirectory;

    @BeforeEach
    void 비운다() {
        DbTestSupport.deleteAll(jdbcTemplate);
    }

    private static byte[] 엑셀한장() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            workbook.createSheet("시트1").createRow(0).createCell(0).setCellValue("값");
            workbook.write(out);
            return out.toByteArray();
        }
    }

    /** 그 주소에서 부른 것처럼 꾸민다. MockMvc 는 기본으로 127.0.0.1 이다. */
    private org.springframework.test.web.servlet.ResultActions 발급(String clientIp)
            throws Exception {
        return 발급(clientIp, "전자결재");
    }

    private org.springframework.test.web.servlet.ResultActions 발급(String clientIp,
                                                                 String systemName)
            throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "보고서.xlsx",
                "application/octet-stream", 엑셀한장());
        var 요청 = multipart("/api/view/documents")
                .file(file)
                .param("fileName", "보고서.xlsx")
                .param("viewerName", "홍길동 책임");
        if (systemName != null) {
            요청 = 요청.param("systemName", systemName);
        }
        return mockMvc.perform(요청.with(request -> {
            request.setRemoteAddr(clientIp);
            return request;
        }));
    }

    @Test
    void 사내_서버_대역은_열쇠_없이_통과한다() throws Exception {
        발급("192.0.2.25")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.viewUrl").exists());
    }

    @Test
    void 개발자_PC_대역도_통과한다() throws Exception {
        발급("198.51.100.140")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.viewUrl").exists());
    }

    @Test
    void 그_밖의_주소는_403() throws Exception {
        발급("8.8.8.8").andExpect(status().isForbidden());
    }

    @Test
    void 사내_다른_대역도_막는다() throws Exception {
        // 사내라고 다 여는 것이 아니다. 적어 둔 대역만이다.
        발급("192.0.3.5").andExpect(status().isForbidden());
    }

    @Test
    void 막힌_주소도_이력에_남는다() throws Exception {
        발급("8.8.8.8");

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM wo_view_event_log"
                        + " WHERE EVENT_TYPE = 'ERROR' AND RESULT_CODE = 'FORBIDDEN_ADDRESS'",
                Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void 이력에_부른_시스템_이름이_적힌다() throws Exception {
        발급("192.0.2.25", "전자결재").andExpect(status().isOk());

        String caller = jdbcTemplate.queryForObject(
                "SELECT CALLER_SYSTEM FROM wo_view_event_log WHERE EVENT_TYPE = 'ISSUE'",
                String.class);
        assertThat(caller).isEqualTo("전자결재");
    }

    @Test
    void 시스템명이_없으면_400() throws Exception {
        // 주소만으로는 대역 이름까지만 좁혀진다. 어느 시스템이 불렀는지는 그쪽이
        // 밝혀야 이력이 쓸모 있다(2026-08-20 결정).
        발급("192.0.2.25", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_SYSTEM_NAME"));
    }

    @Test
    void 시스템명이_공백뿐이어도_400() throws Exception {
        발급("192.0.2.25", "   ").andExpect(status().isBadRequest());
    }

    @Test
    void 시스템명이_없으면_그것도_이력에_남는다() throws Exception {
        발급("192.0.2.25", null);

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM wo_view_event_log"
                        + " WHERE EVENT_TYPE = 'ERROR' AND RESULT_CODE = 'MISSING_SYSTEM_NAME'",
                Integer.class);
        assertThat(count).isEqualTo(1);
    }
}
