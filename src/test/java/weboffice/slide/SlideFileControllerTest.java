package weboffice.slide;

import java.io.ByteArrayOutputStream;

import org.apache.poi.sl.usermodel.PictureData;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
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

import weboffice.security.LoginUser;
import weboffice.security.Authenticator;
import weboffice.support.DbTestSupport;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * pptx 올리기.
 *
 * <p>보여 주기만 한다. 고쳐 쓰는 화면은 없다(2026-08-13 결정).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SlideFileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private Authenticator authenticator;

    @BeforeEach
    void 로그인한다() {
        DbTestSupport.deleteAll(jdbcTemplate);
        when(authenticator.authenticate(any(), any()))
                .thenReturn(new LoginUser("me", "me", "나", "사원", "D100", "전산팀", "ko"));
    }

    @Test
    void 올린_pptx_를_슬라이드로_담는다() throws Exception {
        mockMvc.perform(multipart("/api/slides/import").file(파일("발표.pptx", 세_장짜리())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workbookName").value("발표"))
                .andExpect(jsonPath("$.docType").value("SLIDE"))
                .andExpect(jsonPath("$.slideCount").value(3));
    }

    @Test
    void 담은_슬라이드를_다시_읽는다() throws Exception {
        String body = mockMvc.perform(multipart("/api/slides/import")
                        .file(파일("발표.pptx", 글이_든_장())))
                .andReturn().getResponse().getContentAsString();
        long workbookSeq = Long.parseLong(body.replaceAll(".*\"workbookSeq\":(\\d+).*", "$1"));

        mockMvc.perform(get("/api/slides/{seq}", workbookSeq))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.width").value(960))
                .andExpect(jsonPath("$.slides[0].texts[0].text").value("첫 장"));
    }

    @Test
    void 그림은_그림_저장소에_넣고_주소만_담는다() throws Exception {
        String body = mockMvc.perform(multipart("/api/slides/import")
                        .file(파일("발표.pptx", 그림이_든_장())))
                .andReturn().getResponse().getContentAsString();
        long workbookSeq = Long.parseLong(body.replaceAll(".*\"workbookSeq\":(\\d+).*", "$1"));

        mockMvc.perform(get("/api/slides/{seq}", workbookSeq))
                .andExpect(jsonPath("$.slides[0].pictures[0].url").value(
                        org.hamcrest.Matchers.startsWith("/api/images/")));
    }

    @Test
    void pptx_가_아니면_되돌려_보낸다() throws Exception {
        mockMvc.perform(multipart("/api/slides/import").file(파일("발표.ppt", new byte[] {1})))
                .andExpect(status().isBadRequest());
    }

    private MockMultipartFile 파일(String name, byte[] bytes) {
        return new MockMultipartFile("file", name,
                "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                bytes);
    }

    private byte[] 세_장짜리() throws Exception {
        try (var show = new XMLSlideShow();
             var out = new ByteArrayOutputStream()) {
            for (int at = 0; at < 3; at++) {
                show.createSlide();
            }
            show.write(out);
            return out.toByteArray();
        }
    }

    private byte[] 글이_든_장() throws Exception {
        try (var show = new XMLSlideShow();
             var out = new ByteArrayOutputStream()) {
            var box = show.createSlide().createTextBox();
            box.setAnchor(new java.awt.geom.Rectangle2D.Double(72, 36, 300, 60));
            box.addNewTextParagraph().addNewTextRun().setText("첫 장");
            show.write(out);
            return out.toByteArray();
        }
    }

    private byte[] 그림이_든_장() throws Exception {
        try (var show = new XMLSlideShow();
             var out = new ByteArrayOutputStream()) {
            var data = show.addPicture(작은_png(), PictureData.PictureType.PNG);
            var picture = show.createSlide().createPicture(data);
            picture.setAnchor(new java.awt.geom.Rectangle2D.Double(0, 0, 150, 100));
            show.write(out);
            return out.toByteArray();
        }
    }

    private byte[] 작은_png() {
        return java.util.Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");
    }
}
