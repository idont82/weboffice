package weboffice.view;

import java.io.ByteArrayInputStream;

import org.springframework.stereotype.Service;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import weboffice.doc.DocContent;
import weboffice.doc.DocxIoService;
import weboffice.doc.HwpIoService;
import weboffice.excel.ExcelImportService;
import weboffice.excel.WorkbookDataDto;
import weboffice.slide.PptxIoService;
import weboffice.slide.SlideDeck;

/**
 * 받은 바이트를 화면이 읽을 한 덩어리로 바꾼다.
 *
 * <p>DB 를 거치지 않는다. 뷰어 문서는 문서함에 들어가지 않으므로 가져오기 서비스의
 * "읽기" 부분만 쓴다(설계 2절).
 */
@Service
public class SnapshotBuilder {

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private final ExcelImportService excelImportService;
    private final DocxIoService docxIoService;
    private final HwpIoService hwpIoService;
    private final PptxIoService pptxIoService;

    public SnapshotBuilder(ExcelImportService excelImportService, DocxIoService docxIoService,
                           HwpIoService hwpIoService, PptxIoService pptxIoService) {
        this.excelImportService = excelImportService;
        this.docxIoService = docxIoService;
        this.hwpIoService = hwpIoService;
        this.pptxIoService = pptxIoService;
    }

    public ViewSnapshot build(byte[] bytes, String fileName, boolean encrypted) {
        DocKind kind = DocKind.of(fileName);
        if (kind == DocKind.UNKNOWN) {
            throw new ViewException(ViewErrorCode.UNSUPPORTED_TYPE,
                    "볼 수 없는 형식입니다: " + fileName);
        }
        try {
            String json = switch (kind) {
                case XLSX -> sheetJson(bytes, fileName);
                case DOCX -> docJson(docxIoService.readDocx(new ByteArrayInputStream(bytes)));
                case HWP -> docJson(hwpIoService.readHwp(bytes));
                case HWPX -> docJson(hwpIoService.readHwpx(bytes));
                case PPTX -> slideJson(pptxIoService.readPptx(new ByteArrayInputStream(bytes)));
                case UNKNOWN -> throw new ViewException(ViewErrorCode.UNSUPPORTED_TYPE);
            };
            return new ViewSnapshot(kind.docType(), fileName, encrypted, json);
        } catch (ViewException e) {
            throw e;
        } catch (Exception e) {
            // 깨진 파일·암호 걸린 zip·다른 형식에 확장자만 바꾼 것. 무엇이든 여기로 온다.
            throw new ViewException(ViewErrorCode.CONVERT_FAILED,
                    "문서를 읽지 못했습니다: " + e.getMessage());
        }
    }

    private String sheetJson(byte[] bytes, String fileName) throws Exception {
        WorkbookDataDto workbook =
                excelImportService.importWorkbook(new ByteArrayInputStream(bytes), fileName);
        return MAPPER.writeValueAsString(workbook);
    }

    private String docJson(DocContent content) {
        return MAPPER.writeValueAsString(content);
    }

    private String slideJson(SlideDeck deck) {
        return MAPPER.writeValueAsString(deck);
    }
}
