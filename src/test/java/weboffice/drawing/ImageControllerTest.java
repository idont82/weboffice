package weboffice.drawing;

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
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;

import weboffice.security.LoginUser;
import weboffice.security.Authenticator;
import weboffice.support.DbTestSupport;
import weboffice.workbook.WorkbookDto;
import weboffice.workbook.WorkbookMapper;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ImageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private WorkbookMapper workbookMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private Authenticator authenticator;

    private Long workbookSeq;

    @BeforeEach
    void 문서를_만들고_로그인한다() {
        DbTestSupport.deleteAll(jdbcTemplate);
        loggedInAs("owner1");
        workbookSeq = 문서를_만든다("owner1");
    }

    @Test
    void 올린_이미지를_다시_받는다() throws Exception {
        byte[] png = TestImages.png();

        String url = 올린다(png, "a.png", "image/png");

        mockMvc.perform(get(url))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(content().bytes(png));
    }

    @Test
    void 업로드_응답에_번호와_주소와_형식이_담긴다() throws Exception {
        mockMvc.perform(multipart("/api/workbooks/{seq}/images", workbookSeq)
                        .file(new MockMultipartFile("file", "a.png", "image/png",
                                TestImages.png())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imageSeq").isNumber())
                .andExpect(jsonPath("$.url").value(containsString("/api/images/")))
                .andExpect(jsonPath("$.mimeType").value("image/png"));
    }

    @Test
    void 서빙_응답에_보안_헤더가_붙는다() throws Exception {
        // 사용자가 올린 바이트를 우리 오리진에서 내보낸다. 이 헤더들이 방어선이다.
        String url = 올린다(TestImages.png(), "a.png", "image/png");

        mockMvc.perform(get(url))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Disposition", "inline"))
                .andExpect(header().exists("ETag"))
                .andExpect(header().string("Cache-Control", containsString("immutable")));
    }

    @Test
    void SVG_업로드는_400_이다() throws Exception {
        mockMvc.perform(multipart("/api/workbooks/{seq}/images", workbookSeq)
                        .file(new MockMultipartFile("file", "x.svg", "image/svg+xml",
                                TestImages.svg())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 선언한_타입이_아니라_바이트로_판별한다() throws Exception {
        // 파일명과 Content-Type 이 png 라고 우겨도 내용이 svg 면 거부한다.
        mockMvc.perform(multipart("/api/workbooks/{seq}/images", workbookSeq)
                        .file(new MockMultipartFile("file", "a.png", "image/png",
                                TestImages.svg())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 권한_없는_문서에는_못_올린다() throws Exception {
        Long other = 문서를_만든다("stranger");

        mockMvc.perform(multipart("/api/workbooks/{seq}/images", other)
                        .file(new MockMultipartFile("file", "a.png", "image/png",
                                TestImages.png())))
                .andExpect(status().isForbidden());
    }

    @Test
    void 권한_없는_문서의_이미지는_못_받는다() throws Exception {
        String url = 올린다(TestImages.png(), "a.png", "image/png");

        loggedInAs("stranger");

        mockMvc.perform(get(url)).andExpect(status().isForbidden());
    }

    private String 올린다(byte[] bytes, String fileName, String declaredType) throws Exception {
        String body = mockMvc.perform(multipart("/api/workbooks/{seq}/images", workbookSeq)
                        .file(new MockMultipartFile("file", fileName, declaredType, bytes)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.url");
    }

    private Long 문서를_만든다(String ownerUser) {
        WorkbookDto workbook = new WorkbookDto(null, "그림 문서", ownerUser, null, null);
        workbookMapper.insertWorkbook(workbook, ownerUser);
        return workbook.workbookSeq();
    }

    private void loggedInAs(String userKey) {
        when(authenticator.authenticate(any(), any()))
                .thenReturn(new LoginUser(userKey, userKey, userKey, "사원", "D100", "전산팀", "ko"));
    }
}
