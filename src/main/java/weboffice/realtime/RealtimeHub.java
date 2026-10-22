package weboffice.realtime;

import java.util.List;
import java.util.Map;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import weboffice.drawing.DrawingDto;
import weboffice.excel.CellDto;
import weboffice.excel.MergeRangeDto;
import weboffice.lock.PresenceDto;
import weboffice.security.Actor;
import weboffice.security.UserContext;
import weboffice.sheet.SheetSizeDto;

/**
 * 알림 전달만 한다. 상태를 보관하지 않는다(스펙 3절 모듈 책임).
 *
 * <p>나가는 이벤트에 <b>사번이 없다.</b> 2026-08-06 이전에는 모든 이벤트가
 * {@code userKey} 를 싣고 있었다 - 화면에 쓰이지 않는데도 브라우저까지 갔다.
 * 행위자는 {@code USER_ID} 로 가리킨다(설계 3절).
 */
@Component
public class RealtimeHub {

    private final SimpMessagingTemplate template;

    public RealtimeHub(SimpMessagingTemplate template) {
        this.template = template;
    }

    /** 누가 어디를 보고 있다. 받는 쪽은 칠하기만 하고 막지 않는다. */
    public void presenceSet(PresenceDto presence) {
        send(presence.sheetSeq(), new SheetEvent("PRESENCE_SET", presence.sheetSeq(),
                presence.userId(), presence.label(), presence));
    }

    /**
     * 자리를 비웠다.
     *
     * <p>사람이 아니라 {@code lockSeq} 로 지운다. 만료 회수는 행 단위로 일어나고
     * 그때는 누구였는지 다시 조회하지 않는다.
     */
    public void presenceCleared(Long sheetSeq, Long lockSeq) {
        send(sheetSeq, new SheetEvent("PRESENCE_CLEARED", sheetSeq, null, null,
                Map.of("lockSeq", lockSeq)));
    }

    public void cellsChanged(Long sheetSeq, Actor actor, List<CellDto> cells) {
        if (cells.isEmpty()) {
            return;
        }
        send(sheetSeq, new SheetEvent("CELLS_CHANGED", sheetSeq,
                actor.userId(), actor.name(), UserContext.currentClient(), cells));
    }

    /**
     * 그림 변경 알림.
     *
     * <p>payload 가 Map 인 이유: 바뀐 그림과 지운 식별자를 함께 보내야 하는데
     * 이 조합을 위한 타입을 만들면 이 한 곳에서만 쓰인다.
     */
    public void drawingsChanged(Long sheetSeq, Actor actor,
                                List<DrawingDto> drawings, List<String> deletedIds) {
        // 빈 이벤트가 나가면 받는 쪽이 헛되이 "변경이 반영됐습니다" 를 띄운다.
        if (drawings.isEmpty() && deletedIds.isEmpty()) {
            return;
        }
        send(sheetSeq, new SheetEvent("DRAWINGS_CHANGED", sheetSeq,
                actor.userId(), actor.name(), UserContext.currentClient(),
                Map.of("drawings", drawings, "deletedIds", deletedIds)));
    }

    /** 행 높이·열 너비 변경. 받는 쪽은 바뀐 줄만 다시 그린다. */
    public void sizesChanged(Long sheetSeq, Actor actor,
                             List<SheetSizeDto> changed, List<SheetSizeDto> deleted) {
        if (changed.isEmpty() && deleted.isEmpty()) {
            return;
        }
        send(sheetSeq, new SheetEvent("SIZES_CHANGED", sheetSeq,
                actor.userId(), actor.name(), UserContext.currentClient(),
                Map.of("changed", changed, "deleted", deleted)));
    }

    /**
     * 병합 변경. 지금 있는 병합을 통째로 보낸다.
     *
     * <p>바뀐 것만 보내지 않는 이유: 병합은 하나를 풀면 그 자리에 다른 병합이 생기는
     * 식이라 델타로는 받는 쪽이 순서에 따라 다른 그림을 만든다. 시트당 많아야 수백
     * 개라 통째로 보내도 무겁지 않다.
     *
     * <p>빈 목록도 보낸다. "전부 풀었다" 가 뜻이 있는 상태이기 때문이다 - 셀·크기와
     * 다른 점이다.
     */
    public void mergesChanged(Long sheetSeq, Actor actor, List<MergeRangeDto> merges) {
        send(sheetSeq, new SheetEvent("MERGES_CHANGED", sheetSeq,
                actor.userId(), actor.name(), UserContext.currentClient(), Map.of("merges", merges)));
    }

    /**
     * 시트 목록이 바뀌었다(더하기·이름·지우기).
     *
     * <p>토픽이 시트 단위라 문서 단위 알림을 보낼 곳이 없다. 그 문서의 시트 토픽에
     * 모두 보낸다 - 어느 시트를 보고 있든 탭 줄은 같이 바뀌어야 한다.
     *
     * <p>목록을 인자로 받는다. 여기서 조회하면 이 클래스가 "전달만 한다" 는 규칙을
     * 깬다(설계 3절 모듈 책임).
     */
    public void sheetsChanged(List<Long> sheetSeqs, Actor actor) {
        for (Long sheetSeq : sheetSeqs) {
            send(sheetSeq, new SheetEvent("SHEETS_CHANGED", sheetSeq,
                    actor.userId(), actor.name(), Map.of()));
        }
    }

    /** 토픽을 시트 단위로 나눈다. 합치면 각자 다른 문서를 연 사용자들이 서로의 이벤트를 전부 받는다. */
    private void send(Long sheetSeq, SheetEvent event) {
        template.convertAndSend("/topic/sheets/" + sheetSeq, event);
    }
}
