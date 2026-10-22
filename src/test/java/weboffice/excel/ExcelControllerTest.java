package weboffice.excel;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import weboffice.drm.DrmCodec;
import weboffice.security.LoginUser;
import weboffice.security.Authenticator;
import weboffice.support.DbTestSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ExcelControllerTest {

    private static final String XLSX_MIME =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private Authenticator authenticator;

    /**
     * 끼워진 것을 그대로 쓴다. 기본 구현은 아무것도 하지 않지만, 자기 문서보안을
     * 끼운 사람에게도 이 시험이 뜻을 가지려면 내보내기와 같은 codec 을 타야 한다.
     */
    @Autowired
    private DrmCodec drmCodec;

    @BeforeEach
    void 데이터를_비우고_로그인한다() {
        DbTestSupport.deleteAll(jdbcTemplate);
        loggedInAs("owner1");
    }

    @Test
    void 업로드하면_문서_번호와_시트_목록을_돌려준다() throws Exception {
        mockMvc.perform(multipart("/api/workbooks/import").file(xlsxFile("영업.xlsx")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workbookSeq").isNumber())
                .andExpect(jsonPath("$.sheets.length()").value(1))
                .andExpect(jsonPath("$.sheets[0].sheetName").value("시트1"));
    }

    @Test
    void 매크로_문서는_거부한다() throws Exception {
        mockMvc.perform(multipart("/api/workbooks/import").file(xlsxFile("영업.xlsm")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("매크로 문서(.xlsm)는 올릴 수 없습니다"));
    }

    @Test
    void 올린_문서를_다시_내려받는다() throws Exception {
        Long workbookSeq = uploadAndGetSeq();

        byte[] downloaded = mockMvc.perform(
                        get("/api/workbooks/{workbookSeq}/export", workbookSeq))
                .andExpect(status().isOk())
                .andExpect(status().is2xxSuccessful())
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(downloaded).isNotEmpty();
        byte[] plain = drmCodec.decrypt(downloaded, "영업.xlsx");
        assertThat(new String(plain, 0, 2))
                .as("xlsx 는 zip 이므로 PK 로 시작한다")
                .isEqualTo("PK");
    }

    /**
     * 기본 설정은 문서를 잠그지 않는다. 받는 사람이 이것을 모르고 "어차피
     * 잠겨 나간다" 고 믿는 것이 가장 나쁘다.
     */
    @Test
    void 내려받는_파일은_평문이다() throws Exception {
        Long workbookSeq = uploadAndGetSeq();

        byte[] downloaded = mockMvc.perform(
                        get("/api/workbooks/{workbookSeq}/export", workbookSeq))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(new String(downloaded, 0, 2))
                .as("xlsx 는 zip 이므로 머리가 PK 다")
                .isEqualTo("PK");
    }

    @Test
    void 올린_파일의_확장자는_제목에_넣지_않는다() throws Exception {
        // "영업.xlsx" 를 올리면 제목이 "영업.xlsx" 가 돼 다시 내려받을 때
        // "영업.xlsx.xlsx" 가 됐다(2026-08-10 지적). 제목은 사람이 읽는 이름이다.
        Long workbookSeq = uploadAndGetSeq();

        String disposition = mockMvc.perform(
                        get("/api/workbooks/{workbookSeq}/export", workbookSeq))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION);

        assertThat(rfc5987NameOf(disposition)).isEqualTo("영업.xlsx");
    }

    @Test
    void 내려받는_파일에_문서_제목을_붙인다() throws Exception {
        // export.xlsx 로 고정돼 있어 여러 문서를 받으면 export (1).xlsx 처럼 쌓였다
        // - 어느 파일이 어느 문서인지 알 수 없다(2026-08-07 지적).
        Long workbookSeq = uploadAndGetSeq();
        mockMvc.perform(patch("/api/workbooks/{seq}", workbookSeq)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"workbookName\":\"7월 실적\"}"))
                .andExpect(status().isOk());

        String disposition = mockMvc.perform(
                        get("/api/workbooks/{workbookSeq}/export", workbookSeq))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION);

        // 한글은 헤더에 그대로 못 싣는다. RFC 5987 이름과 ASCII 대체 이름이 함께 간다.
        assertThat(disposition).contains("attachment");
        assertThat(disposition).contains("filename*=UTF-8''");
        assertThat(disposition).contains("%EC%8B%A4%EC%A0%81");
        assertThat(rfc5987NameOf(disposition))
                .as("확장자가 없으면 엑셀이 안 연다").endsWith(".xlsx");
    }

    @Test
    void 제목에_파일명으로_못_쓰는_글자가_있으면_바꾼다() throws Exception {
        // 윈도우는 \ / : * ? " < > | 를 파일명에 못 쓴다. 그대로 두면 저장이 실패한다.
        Long workbookSeq = uploadAndGetSeq();
        mockMvc.perform(patch("/api/workbooks/{seq}", workbookSeq)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"workbookName\":\"a/b:c*d?e\"}"))
                .andExpect(status().isOk());

        String disposition = mockMvc.perform(
                        get("/api/workbooks/{workbookSeq}/export", workbookSeq))
                .andReturn().getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION);

        // 헤더 문법에도 * 와 = 가 있으므로 파일명 부분만 본다.
        assertThat(rfc5987NameOf(disposition)).isEqualTo("a_b_c_d_e.xlsx");
    }

    @Test
    void 제목_끝의_점과_공백은_떼고_붙인다() throws Exception {
        // "테스트문서 입니다." 를 그대로 쓰면 "테스트문서 입니다..xlsx" 가 된다.
        // 윈도우는 이름 끝의 점과 공백을 허용하지 않기도 한다(2026-08-07 실측).
        Long workbookSeq = uploadAndGetSeq();
        mockMvc.perform(patch("/api/workbooks/{seq}", workbookSeq)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"workbookName\":\"보고서. \"}"))
                .andExpect(status().isOk());

        String disposition = mockMvc.perform(
                        get("/api/workbooks/{workbookSeq}/export", workbookSeq))
                .andReturn().getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION);

        assertThat(rfc5987NameOf(disposition)).isEqualTo("보고서.xlsx");
    }

    @Test
    void 제목이_통째로_지워지면_기본_이름을_쓴다() throws Exception {
        // 이름이 ".." 뿐이면 다듬은 뒤 빈 문자열이 된다. ".xlsx" 로 내보내면 안 된다.
        Long workbookSeq = uploadAndGetSeq();
        mockMvc.perform(patch("/api/workbooks/{seq}", workbookSeq)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"workbookName\":\"...\"}"))
                .andExpect(status().isOk());

        String disposition = mockMvc.perform(
                        get("/api/workbooks/{workbookSeq}/export", workbookSeq))
                .andReturn().getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION);

        assertThat(rfc5987NameOf(disposition)).isEqualTo("문서.xlsx");
    }

    @Test
    void 권한_없는_사용자는_내려받지_못한다() throws Exception {
        Long workbookSeq = uploadAndGetSeq();

        loggedInAs("stranger");

        mockMvc.perform(get("/api/workbooks/{workbookSeq}/export", workbookSeq))
                .andExpect(status().isForbidden());
    }

    /** {@code filename*=UTF-8''...} 에서 이름만 떼어 낸다. 퍼센트 인코딩은 되돌린다. */
    private String rfc5987NameOf(String disposition) {
        String marker = "filename*=UTF-8''";
        int start = disposition.indexOf(marker) + marker.length();
        return java.net.URLDecoder.decode(disposition.substring(start),
                java.nio.charset.StandardCharsets.UTF_8);
    }

    private Long uploadAndGetSeq() throws Exception {
        String body = mockMvc.perform(multipart("/api/workbooks/import").file(xlsxFile("영업.xlsx")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        int start = body.indexOf("\"workbookSeq\":") + "\"workbookSeq\":".length();
        int end = start;
        while (end < body.length() && Character.isDigit(body.charAt(end))) {
            end++;
        }
        return Long.valueOf(body.substring(start, end));
    }

    private MockMultipartFile xlsxFile(String name) throws Exception {
        return new MockMultipartFile("file", name, XLSX_MIME, TestWorkbooks.mixedSheet());
    }

    private void loggedInAs(String userKey) {
        when(authenticator.authenticate(any(), any()))
                .thenReturn(new LoginUser(userKey, userKey, userKey, "사원", "D100", "전산팀", "ko"));
    }
}
