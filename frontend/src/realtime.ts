import { Client, type IMessage } from '@stomp/stompjs';

import type { CellDto, MergeRangeDto } from './adapter';
import type { DrawingDto, PresenceRow, SheetSize } from './api';
import { fromOtherClient } from './clientId';

export interface SheetEventHandlers {
  onPresenceSet?: (presence: PresenceRow) => void;
  onPresenceCleared?: (lockSeq: number) => void;
  onCellsChanged?: (cells: CellDto[], actorName: string | null) => void;
  onDrawingsChanged?: (drawings: DrawingDto[], deletedIds: string[],
                       actorName: string | null) => void;
  onSizesChanged?: (changed: SheetSize[], deleted: SheetSize[],
                    actorName: string | null) => void;
  onMergesChanged?: (merges: MergeRangeDto[], actorName: string | null) => void;
  onSheetsChanged?: (actorName: string | null) => void;
}

/**
 * 시트 토픽을 구독한다. 반환된 함수를 호출하면 해제된다.
 *
 * 핸드셰이크에 세션 쿠키가 자동으로 실린다(same-origin). 서버가 그 쿠키로
 * 인증하지 못하면 연결이 401 로 끊긴다.
 *
 * SockJS 는 쓰지 않는다. 사내 브라우저가 전부 WebSocket 을 지원하고,
 * 넣으면 폴백 경로까지 테스트해야 한다.
 */
export function connect(sheetSeq: number, handlers: SheetEventHandlers): () => void {
  const scheme = location.protocol === 'https:' ? 'wss' : 'ws';
  const client = new Client({
    brokerURL: `${scheme}://${location.host}/ws`,
    reconnectDelay: 3000,
  });

  client.onConnect = () => {
    client.subscribe(`/topic/sheets/${sheetSeq}`, (message: IMessage) => {
      const event = JSON.parse(message.body);
      // 내 창이 만든 알림은 흘려보낸다. 되받아 화면에 다시 쓰면 그 쓰기가
      // 되돌리기 이력에 쌓여 Ctrl+Z 가 엉뚱한 것을 되돌린다(2026-08-27 실측).
      if (!fromOtherClient(event)) {
        return;
      }
      if (event.type === 'SIZES_CHANGED') {
        handlers.onSizesChanged?.(event.payload.changed, event.payload.deleted,
          event.actorName ?? null);
      }
      if (event.type === 'PRESENCE_SET') handlers.onPresenceSet?.(event.payload);
      if (event.type === 'PRESENCE_CLEARED') {
        handlers.onPresenceCleared?.(event.payload.lockSeq);
      }
      if (event.type === 'CELLS_CHANGED') {
        handlers.onCellsChanged?.(event.payload, event.actorName ?? null);
      }
      if (event.type === 'SHEETS_CHANGED') {
        handlers.onSheetsChanged?.(event.actorName ?? null);
      }
      if (event.type === 'MERGES_CHANGED') {
        handlers.onMergesChanged?.(event.payload.merges, event.actorName ?? null);
      }
      if (event.type === 'DRAWINGS_CHANGED') {
        handlers.onDrawingsChanged?.(event.payload.drawings, event.payload.deletedIds,
          event.actorName ?? null);
      }
    });
  };

  client.activate();
  return () => void client.deactivate();
}
