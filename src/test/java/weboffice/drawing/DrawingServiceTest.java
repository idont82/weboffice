package weboffice.drawing;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import weboffice.common.AuditUser;
import weboffice.realtime.RealtimeHub;
import weboffice.security.Actor;
import weboffice.sheet.SheetMapper;
import weboffice.sheet.SheetSummaryDto;
import weboffice.support.DbTestSupport;
import weboffice.workbook.WorkbookDto;
import weboffice.workbook.WorkbookMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@SpringBootTest
@ActiveProfiles("test")
class DrawingServiceTest {

    private static final AuditUser USER = AuditUser.of("12345");

    @Autowired
    private DrawingService drawingService;

    @Autowired
    private ImageService imageService;

    @Autowired
    private SheetMapper sheetMapper;

    @Autowired
    private WorkbookMapper workbookMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private RealtimeHub realtimeHub;

    private Long sheetSeq;
    private Long imageSeq;

    @BeforeEach
    void 문서와_시트와_이미지를_만든다() {
        DbTestSupport.deleteAll(jdbcTemplate);
        WorkbookDto workbook = new WorkbookDto(null, "그림 문서", "12345", null, null);
        workbookMapper.insertWorkbook(workbook, "12345");
        SheetSummaryDto sheet =
                new SheetSummaryDto(null, workbook.workbookSeq(), "시트1", 0, 0, 0);
        sheetMapper.insertSheet(sheet, "12345");
        sheetSeq = sheet.sheetSeq();
        imageSeq = imageService.store(workbook.workbookSeq(), TestImages.png(), USER);
    }

    private DrawingDto drawing(String drawingId, int zOrder) {
        return new DrawingDto(drawingId, imageSeq,
                1, 5, 2, 10, 6, 15, 4, 20, "TWO_CELL", zOrder, null);
    }

    @Test
    void 넣은_그림을_다시_읽는다() {
        int saved = drawingService.saveDelta(sheetSeq,
                List.of(drawing("dw-1", 0)), List.of(), USER);

        assertThat(saved).isEqualTo(1);
        assertThat(drawingService.readDrawings(sheetSeq)).hasSize(1);
    }

    @Test
    void 지운_것과_넣은_것을_한_번에_처리한다() {
        drawingService.saveDelta(sheetSeq, List.of(drawing("dw-1", 0)), List.of(), USER);

        int saved = drawingService.saveDelta(sheetSeq,
                List.of(drawing("dw-2", 1)), List.of("dw-1"), USER);

        assertThat(saved).isEqualTo(2);
        assertThat(drawingService.readDrawings(sheetSeq))
                .extracting(DrawingDto::drawingId)
                .containsExactly("dw-2");
    }

    @Test
    void 같은_호출에_같은_식별자가_양쪽에_있으면_넣은_것이_남는다() {
        // 삭제를 업서트보다 먼저 하기 때문이다. 순서가 반대면 방금 넣은 것을 지운다.
        drawingService.saveDelta(sheetSeq, List.of(drawing("dw-1", 0)), List.of(), USER);

        drawingService.saveDelta(sheetSeq,
                List.of(drawing("dw-1", 5)), List.of("dw-1"), USER);

        assertThat(drawingService.readDrawings(sheetSeq))
                .extracting(DrawingDto::drawingId)
                .containsExactly("dw-1");
    }

    @Test
    void 빈_델타는_아무것도_하지_않는다() {
        assertThat(drawingService.saveDelta(sheetSeq, List.of(), List.of(), USER)).isZero();

        verify(realtimeHub, never()).drawingsChanged(any(), any(), any(), any());
    }

    @Test
    void 커밋_뒤에_브로드캐스트한다() {
        // 커밋 전에 쏘면 알림을 받은 클라이언트가 곧바로 조회했을 때 아직
        // 반영되지 않은 상태를 본다.
        drawingService.saveDelta(sheetSeq, List.of(drawing("dw-1", 0)), List.of("dw-0"), USER);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<DrawingDto>> drawings = ArgumentCaptor.forClass(List.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> deleted = ArgumentCaptor.forClass(List.class);
        // 행위자는 USER_ID 와 표시 이름이다. 이 테스트는 세션 없이 서비스를 직접
        // 부르므로 둘 다 비어 있다.
        verify(realtimeHub).drawingsChanged(eq(sheetSeq), eq(Actor.unknown()),
                drawings.capture(), deleted.capture());

        assertThat(drawings.getValue()).extracting(DrawingDto::drawingId).containsExactly("dw-1");
        assertThat(deleted.getValue()).containsExactly("dw-0");
    }
}
