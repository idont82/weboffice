package weboffice.view;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DocKindTest {

    @Test
    void 확장자로_가린다() {
        assertThat(DocKind.of("보고서.xlsx")).isEqualTo(DocKind.XLSX);
        assertThat(DocKind.of("보고서.docx")).isEqualTo(DocKind.DOCX);
        assertThat(DocKind.of("보고서.hwp")).isEqualTo(DocKind.HWP);
        assertThat(DocKind.of("보고서.hwpx")).isEqualTo(DocKind.HWPX);
        assertThat(DocKind.of("보고서.pptx")).isEqualTo(DocKind.PPTX);
    }

    @Test
    void 대소문자를_가리지_않는다() {
        assertThat(DocKind.of("REPORT.XLSX")).isEqualTo(DocKind.XLSX);
    }

    @Test
    void 모르는_형식은_UNKNOWN_이다() {
        assertThat(DocKind.of("사진.png")).isEqualTo(DocKind.UNKNOWN);
        assertThat(DocKind.of("이름없음")).isEqualTo(DocKind.UNKNOWN);
        assertThat(DocKind.of(null)).isEqualTo(DocKind.UNKNOWN);
    }

    @Test
    void 화면이_쓰는_종류로_바꾼다() {
        assertThat(DocKind.XLSX.docType()).isEqualTo("SHEET");
        assertThat(DocKind.DOCX.docType()).isEqualTo("DOC");
        assertThat(DocKind.HWP.docType()).isEqualTo("DOC");
        assertThat(DocKind.PPTX.docType()).isEqualTo("SLIDE");
    }
}
