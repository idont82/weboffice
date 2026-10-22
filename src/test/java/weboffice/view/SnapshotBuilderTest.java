package weboffice.view;

import java.io.ByteArrayOutputStream;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 바이트를 화면이 읽을 한 덩어리로 바꾼다.
 *
 * <p>DB 를 거치지 않는 것이 요점이다 - 뷰어 문서는 문서함에 들어가지 않는다.
 */
@SpringBootTest
@ActiveProfiles("test")
class SnapshotBuilderTest {

    @Autowired
    private SnapshotBuilder builder;

    private static byte[] 엑셀한장() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            workbook.createSheet("시트1").createRow(0).createCell(0).setCellValue("값");
            workbook.write(out);
            return out.toByteArray();
        }
    }

    @Test
    void 엑셀을_스냅샷으로_바꾼다() throws Exception {
        ViewSnapshot snapshot = builder.build(엑셀한장(), "보고서.xlsx", false);

        assertThat(snapshot.docType()).isEqualTo("SHEET");
        assertThat(snapshot.fileName()).isEqualTo("보고서.xlsx");
        assertThat(snapshot.encrypted()).isFalse();
        assertThat(snapshot.json()).contains("시트1").contains("값");
    }

    @Test
    void 못_여는_형식은_거절한다() {
        assertThatThrownBy(() -> builder.build(new byte[] { 1, 2, 3 }, "사진.png", false))
                .isInstanceOf(ViewException.class)
                .hasMessageContaining("형식");
    }

    @Test
    void 깨진_파일은_변환_실패다() {
        assertThatThrownBy(() -> builder.build(new byte[] { 1, 2, 3 }, "보고서.xlsx", false))
                .isInstanceOf(ViewException.class);
    }
}
