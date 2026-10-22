package weboffice.doc;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import weboffice.drm.DrmCodec;
import weboffice.security.LoginUser;
import weboffice.security.Authenticator;
import weboffice.support.DbTestSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 글 문서 올리기·내려받기.
 *
 * <p>올리는 것은 docx·hwp·hwpx 셋이고 내려받는 것은 docx 하나다. 한글 형식으로
 * 되돌려 주지 않는다 - 반만 옮긴 파일을 한글 이름으로 주면 받은 사람은 원본이 깨진
 * 줄 안다.
 *
 * <p>xlsx 와 같은 규칙이다. 올라온 것은 사내 문서보안을 풀어 읽고, 내려주는 것은
 * 씌워서 준다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DocFileControllerTest {

    private static final String DOCX_MIME =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DocxIoService docxIoService;

    @Autowired
    private DrmCodec drmCodec;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private Authenticator authenticator;

    @BeforeEach
    void 비우고_로그인한다() {
        DbTestSupport.deleteAll(jdbcTemplate);
        when(authenticator.authenticate(any(), any()))
                .thenReturn(new LoginUser("me", "me", "나", "사원", "D100", "전산팀", "ko"));
    }

    @Test
    void 올리면_글_문서가_된다() throws Exception {
        mockMvc.perform(multipart("/api/docs/import").file(docx("보고서.docx")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workbookSeq").isNumber())
                .andExpect(jsonPath("$.docType").value("DOC"))
                // 확장자는 제목에 넣지 않는다. 시트에서 겪은 것과 같다.
                .andExpect(jsonPath("$.workbookName").value("보고서"));
    }

    @Test
    void 올린_글을_문단으로_읽는다() throws Exception {
        long seq = 올린다();

        mockMvc.perform(get("/api/docs/{seq}/paragraphs", seq))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].text").value("첫째 문단"))
                .andExpect(jsonPath("$[1].text").value("둘째 문단"));
    }

    /**
     * 기본 설정은 문서를 잠그지 않는다. 내보내기가 codec 을 타는 것은 그대로라
     * 자기 문서보안을 끼운 사람에게는 이 시험이 복호화까지 함께 검사한다.
     */
    @Test
    void 내려받으면_열_수_있는_docx_가_나온다() throws Exception {
        long seq = 올린다();

        byte[] body = mockMvc.perform(get("/api/docs/{seq}/export", seq))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        byte[] plain = drmCodec.decrypt(body, "보고서.docx");
        assertThat(new String(plain, 0, 2))
                .as("docx 는 zip 이므로 머리가 PK 다").isEqualTo("PK");
        assertThat(docxIoService.fromDocx(new java.io.ByteArrayInputStream(plain)))
                .extracting(DocParagraphDto::text)
                .containsExactly("첫째 문단", "둘째 문단");
    }

    @Test
    void 내려받는_파일에_문서_제목을_붙인다() throws Exception {
        long seq = 올린다();

        String disposition = mockMvc.perform(get("/api/docs/{seq}/export", seq))
                .andReturn().getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION);

        assertThat(disposition).contains("attachment");
        assertThat(disposition).contains("filename*=UTF-8''");
        assertThat(java.net.URLDecoder.decode(
                disposition.substring(disposition.indexOf("filename*=UTF-8''") + 17),
                java.nio.charset.StandardCharsets.UTF_8)).endsWith(".docx");
    }

    @Test
    void 표는_표로_담아_둔다() throws Exception {
        // 칸 글자를 문단으로 펼치면 세로로 길게 늘어서 원본과 너무 달라 보인다.
        // 화면이 표로 세울 수 있게 표 얼개를 따로 담는다.
        byte[] bytes = docxIoService.toDocx(List.of(
                new DocParagraphDto(0, "머리말", null, null)));
        var withTable = new MockMultipartFile("file", "표.docx", DOCX_MIME, 표가_든_docx());

        String body = mockMvc.perform(multipart("/api/docs/import").file(withTable))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long seq = com.jayway.jsonpath.JsonPath.parse(body).read("$.workbookSeq", Long.class);

        String saved = jdbcTemplate.queryForObject(
                "select RESOURCE_JSON from wo_workbook_resource_line"
                + " where WORKBOOK_SEQ = ? and RESOURCE_NAME = 'WO_IMPORT_TABLES'",
                String.class, seq);
        assertThat(saved).contains("구분").contains("접근 통제");
        assertThat(bytes).isNotEmpty();
    }

    private byte[] 표가_든_docx() throws Exception {
        try (var document = new org.apache.poi.xwpf.usermodel.XWPFDocument();
             var out = new java.io.ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText("머리말");
            var table = document.createTable(2, 2);
            table.getRow(0).getCell(0).setText("구분");
            table.getRow(0).getCell(1).setText("내용");
            table.getRow(1).getCell(0).setText("보안");
            table.getRow(1).getCell(1).setText("접근 통제");
            document.write(out);
            return out.toByteArray();
        }
    }

    @Test
    void 한글_문서도_올라간다() throws Exception {
        HwpTestFiles 시험파일 = new HwpTestFiles();
        byte[] bytes = 시험파일.hwp(f -> {
            시험파일.문단(f, "한글 첫째");
            시험파일.문단(f, "한글 둘째");
        });
        MockMultipartFile file = new MockMultipartFile("file", "품의서.hwp",
                "application/x-hwp", bytes);

        String body = mockMvc.perform(multipart("/api/docs/import").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.docType").value("DOC"))
                .andExpect(jsonPath("$.workbookName").value("품의서"))
                .andReturn().getResponse().getContentAsString();

        long seq = com.jayway.jsonpath.JsonPath.parse(body).read("$.workbookSeq", Long.class);
        mockMvc.perform(get("/api/docs/{seq}/paragraphs", seq))
                .andExpect(jsonPath("$[0].text").value("한글 첫째"))
                .andExpect(jsonPath("$[1].text").value("한글 둘째"));
    }

    @Test
    void hwpx_도_올라간다() throws Exception {
        HwpTestFiles 시험파일 = new HwpTestFiles();
        byte[] bytes = 시험파일.hwpx(f -> 시험파일.문단(f, "한글 문단"));
        MockMultipartFile file = new MockMultipartFile("file", "품의서.hwpx",
                "application/hwp+zip", bytes);

        String body = mockMvc.perform(multipart("/api/docs/import").file(file))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        long seq = com.jayway.jsonpath.JsonPath.parse(body).read("$.workbookSeq", Long.class);
        mockMvc.perform(get("/api/docs/{seq}/paragraphs", seq))
                .andExpect(jsonPath("$[0].text").value("한글 문단"));
    }

    @Test
    void 읽지_못하는_파일은_되돌려_보낸다() throws Exception {
        // 빈 문서를 만들어 주면 올린 사람은 자기 글이 들어간 줄 안다.
        MockMultipartFile file = new MockMultipartFile("file", "가짜.hwp",
                "application/x-hwp", "한글 문서가 아니다".getBytes());

        mockMvc.perform(multipart("/api/docs/import").file(file))
                .andExpect(status().isBadRequest());

        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from wo_workbook_master", Integer.class))
                .as("읽지 못한 파일로 빈 문서를 만들면 안 된다").isZero();
    }

    @Test
    void 모르는_확장자는_되돌려_보낸다() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "그림.png",
                "image/png", new byte[] {1, 2, 3});

        mockMvc.perform(multipart("/api/docs/import").file(file))
                .andExpect(status().isBadRequest());
    }

    private long 올린다() throws Exception {
        String body = mockMvc.perform(multipart("/api/docs/import").file(docx("보고서.docx")))
                .andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.parse(body).read("$.workbookSeq", Long.class);
    }

    private MockMultipartFile docx(String name) throws Exception {
        byte[] bytes = docxIoService.toDocx(List.of(
                new DocParagraphDto(0, "첫째 문단", null, null),
                new DocParagraphDto(1, "둘째 문단", null, null)));
        return new MockMultipartFile("file", name, DOCX_MIME, bytes);
    }
}
