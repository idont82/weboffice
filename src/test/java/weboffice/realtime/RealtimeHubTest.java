package weboffice.realtime;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import weboffice.drawing.DrawingDto;
import weboffice.excel.CellDto;
import weboffice.lock.PresenceDto;
import weboffice.security.Actor;
import weboffice.security.UserContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class RealtimeHubTest {

    private final SimpMessagingTemplate template = mock(SimpMessagingTemplate.class);
    private final RealtimeHub hub = new RealtimeHub(template);

    @Test
    void 자리_표시를_해당_시트_토픽으로만_보낸다() {
        PresenceDto lock = new PresenceDto(7L, 42L, "user1-id", "홍길동", "과장", "tab-1", 0, 0, 9, 9);

        hub.presenceSet(lock);

        ArgumentCaptor<SheetEvent> event = ArgumentCaptor.forClass(SheetEvent.class);
        verify(template).convertAndSend(eq("/topic/sheets/42"), event.capture());
        assertThat(event.getValue().type()).isEqualTo("PRESENCE_SET");
        assertThat(event.getValue().userId()).isEqualTo("user1-id");
    }

    @Test
    void 어느_창이_고쳤는지_함께_알린다() {
        // 같은 사람이 두 창을 열어 두면 아이디만으로는 자기 것을 가릴 수 없다.
        // 보낸 창을 함께 실어야 그 창만 자기 알림을 건너뛴다(2026-08-27 실측:
        // 자기 저장이 남의 변경으로 보여 되돌리기가 그 위에서 돌았다).
        UserContext.bindClient("tab-abc");
        try {
            hub.cellsChanged(42L, new Actor("user1-id", "홍길동 과장"),
                    List.of(new CellDto(0, 0, CellDto.ValueType.STRING, "가",
                            null, null, null, null, null)));
        } finally {
            UserContext.clearRequest();
        }

        ArgumentCaptor<SheetEvent> event = ArgumentCaptor.forClass(SheetEvent.class);
        verify(template).convertAndSend(eq("/topic/sheets/42"), event.capture());
        assertThat(event.getValue().actorClient()).isEqualTo("tab-abc");
    }

    @Test
    void 창_표시가_없으면_비워_둔다() {
        UserContext.clearRequest();

        hub.presenceCleared(42L, 7L);

        ArgumentCaptor<SheetEvent> event = ArgumentCaptor.forClass(SheetEvent.class);
        verify(template).convertAndSend(eq("/topic/sheets/42"), event.capture());
        assertThat(event.getValue().actorClient()).isNull();
    }

    @Test
    void 자리를_비운_것을_알린다() {
        hub.presenceCleared(42L, 7L);

        ArgumentCaptor<SheetEvent> event = ArgumentCaptor.forClass(SheetEvent.class);
        verify(template).convertAndSend(eq("/topic/sheets/42"), event.capture());
        assertThat(event.getValue().type()).isEqualTo("PRESENCE_CLEARED");
    }

    @Test
    void 셀_변경을_알린다() {
        hub.cellsChanged(42L, new Actor("user1", "홍길동 과장"), List.of(
                new CellDto(0, 0, CellDto.ValueType.NUMBER, null,
                        BigDecimal.ONE, null, null, null, null)));

        ArgumentCaptor<SheetEvent> event = ArgumentCaptor.forClass(SheetEvent.class);
        verify(template).convertAndSend(eq("/topic/sheets/42"), event.capture());
        assertThat(event.getValue().type()).isEqualTo("CELLS_CHANGED");
    }

    @Test
    void 빈_변경_목록은_보내지_않는다() {
        hub.cellsChanged(42L, new Actor("user1", "홍길동 과장"), List.of());

        verify(template, never()).convertAndSend(any(String.class), any(Object.class));
    }

    @Test
    void 그림_변경을_알린다() {
        hub.drawingsChanged(42L, new Actor("user1", "홍길동 과장"),
                List.of(new DrawingDto("dw-1", 3L, 0, 0, 0, 0, 2, 0, 2, 0, "TWO_CELL", 0, null)),
                List.of("dw-2"));

        ArgumentCaptor<SheetEvent> event = ArgumentCaptor.forClass(SheetEvent.class);
        verify(template).convertAndSend(eq("/topic/sheets/42"), event.capture());
        assertThat(event.getValue().type()).isEqualTo("DRAWINGS_CHANGED");
        assertThat(event.getValue().userId()).isEqualTo("user1");
    }

    @Test
    void 바뀐_것도_지운_것도_없으면_그림_변경을_보내지_않는다() {
        hub.drawingsChanged(42L, new Actor("user1", "홍길동 과장"), List.of(), List.of());

        verify(template, never()).convertAndSend(any(String.class), any(Object.class));
    }
}
