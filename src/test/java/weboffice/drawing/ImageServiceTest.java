package weboffice.drawing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import weboffice.common.AuditUser;
import weboffice.support.DbTestSupport;
import weboffice.workbook.WorkbookDto;
import weboffice.workbook.WorkbookMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class ImageServiceTest {

    @Autowired
    private ImageService imageService;

    @Autowired
    private WorkbookMapper workbookMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final AuditUser user = AuditUser.of("12345");
    private Long workbookSeq;

    @BeforeEach
    void 문서를_만든다() {
        DbTestSupport.deleteAll(jdbcTemplate);
        WorkbookDto workbook = new WorkbookDto(null, "그림 문서", "12345", null, null);
        workbookMapper.insertWorkbook(workbook, "12345");
        workbookSeq = workbook.workbookSeq();
    }

    @Test
    void 같은_바이트를_두_번_올리면_행이_하나만_생긴다() {
        Long first = imageService.store(workbookSeq, TestImages.png(), user);
        Long second = imageService.store(workbookSeq, TestImages.png(), user);

        assertThat(second).isEqualTo(first);
        Integer rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM wo_sheet_image_master", Integer.class);
        assertThat(rows).isEqualTo(1);
    }

    @Test
    void 바이트에서_형식을_판별한다() {
        assertThat(mimeOf(TestImages.png())).isEqualTo("image/png");
        assertThat(mimeOf(TestImages.jpeg())).isEqualTo("image/jpeg");
        assertThat(mimeOf(TestImages.gif())).isEqualTo("image/gif");
        assertThat(mimeOf(TestImages.bmp())).isEqualTo("image/bmp");
    }

    @Test
    void SVG_는_거부된다() {
        // 받아들이면 업로드가 곧 XSS 통로가 된다. 우리 오리진에서 스크립트가 돈다.
        assertThatThrownBy(() -> imageService.store(workbookSeq, TestImages.svg(), user))
                .isInstanceOf(UnsupportedImageException.class);
    }

    @Test
    void 알_수_없는_형식은_거부된다() {
        assertThatThrownBy(() ->
                imageService.store(workbookSeq, new byte[] {1, 2, 3, 4}, user))
                .isInstanceOf(UnsupportedImageException.class);
    }

    @Test
    void 빈_바이트는_거부된다() {
        assertThatThrownBy(() -> imageService.store(workbookSeq, new byte[0], user))
                .isInstanceOf(UnsupportedImageException.class);
    }

    @Test
    void 시그니처보다_짧은_바이트도_거부된다() {
        // 두 바이트짜리 입력이 여덟 바이트 PNG 시그니처와 비교되면 배열 범위를 넘는다.
        assertThatThrownBy(() -> imageService.store(workbookSeq, new byte[] {(byte) 0x89}, user))
                .isInstanceOf(UnsupportedImageException.class);
    }

    @Test
    void 상한을_넘는_이미지는_거부된다() {
        byte[] big = new byte[6 * 1024 * 1024];
        byte[] header = TestImages.png();
        System.arraycopy(header, 0, big, 0, header.length);

        assertThatThrownBy(() -> imageService.store(workbookSeq, big, user))
                .isInstanceOf(UnsupportedImageException.class);
    }

    @Test
    void 해시는_64자_소문자_hex_다() {
        // 빈 입력의 SHA-256 은 공개된 상수다. 직접 계산한 값과 비교하면
        // 구현이 틀려도 같이 틀려서 아무것도 검증하지 못한다.
        assertThat(imageService.sha256(new byte[0]))
                .isEqualTo("e3b0c44298fc1c149afbf4c8996fb924"
                        + "27ae41e4649b934ca495991b7852b855");
    }

    @Test
    void 없는_이미지를_읽으면_거부된다() {
        assertThatThrownBy(() -> imageService.load(999999L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private String mimeOf(byte[] bytes) {
        return imageService.load(imageService.store(workbookSeq, bytes, user)).mimeType();
    }
}
