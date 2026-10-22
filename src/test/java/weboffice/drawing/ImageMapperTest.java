package weboffice.drawing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import weboffice.support.DbTestSupport;
import weboffice.workbook.WorkbookDto;
import weboffice.workbook.WorkbookMapper;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class ImageMapperTest {

    @Autowired
    private ImageMapper imageMapper;

    @Autowired
    private WorkbookMapper workbookMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long workbookSeq;

    @BeforeEach
    void 문서를_만든다() {
        DbTestSupport.deleteAll(jdbcTemplate);
        WorkbookDto workbook = new WorkbookDto(null, "그림 문서", "12345", null, null);
        workbookMapper.insertWorkbook(workbook, "12345");
        workbookSeq = workbook.workbookSeq();
    }

    @Test
    void 이미지를_넣으면_생성_키가_채워진다() {
        ImageRow image = new ImageRow(null, workbookSeq, "a".repeat(64),
                "image/png", 3, new byte[] {1, 2, 3});

        imageMapper.insertImage(image, "12345");

        // 생성 키를 못 받으면 그림 행이 어느 이미지를 가리키는지 알 수 없다.
        assertThat(image.imageSeq()).isNotNull();
    }

    @Test
    void 바이트가_그대로_돌아온다() {
        byte[] bytes = {(byte) 0x89, 80, 78, 71, 13, 10, 26, 10};
        ImageRow image = new ImageRow(null, workbookSeq, "b".repeat(64),
                "image/png", bytes.length, bytes);
        imageMapper.insertImage(image, "12345");

        ImageRow read = imageMapper.selectImage(image.imageSeq());

        assertThat(read.imageData()).isEqualTo(bytes);
        assertThat(read.mimeType()).isEqualTo("image/png");
        assertThat(read.imageSize()).isEqualTo(bytes.length);
        assertThat(read.workbookSeq()).isEqualTo(workbookSeq);
        // CHAR(64) 는 고정 길이라 짧게 넣으면 공백이 붙는다. 64자를 넣었으니 그대로여야 한다.
        assertThat(read.imageHash()).isEqualTo("b".repeat(64));
    }

    @Test
    void 해시로_기존_이미지를_찾는다() {
        ImageRow image = new ImageRow(null, workbookSeq, "c".repeat(64),
                "image/png", 3, new byte[] {1, 2, 3});
        imageMapper.insertImage(image, "12345");

        assertThat(imageMapper.selectImageSeqByHash(workbookSeq, "c".repeat(64)))
                .isEqualTo(image.imageSeq());
        assertThat(imageMapper.selectImageSeqByHash(workbookSeq, "d".repeat(64)))
                .isNull();
    }

    @Test
    void 없는_이미지는_null_이다() {
        assertThat(imageMapper.selectImage(999999L)).isNull();
    }
}
