/**
 * 되돌리기 키.
 *
 * <p>AI 대화창이나 옆 단추에 손이 가 있으면 Ctrl+Z 가 격자에 닿지 않는다
 * (2026-08-26 실측: 되돌리기가 안 먹는 것처럼 보였는데, 실은 키가 다른 데로
 * 갔다). 화면 어디서 눌러도 듣도록 우리가 받아 넘긴다.
 *
 * <p>글을 적는 칸에서는 손대지 않는다 - 적던 글이 아니라 시트가 되돌아간다.
 */

export type UndoKey = 'UNDO' | 'REDO';

interface KeyLike {
  key: string;
  ctrlKey: boolean;
  metaKey: boolean;
  shiftKey: boolean;
  target: EventTarget | null;
}

export function undoKeyOf(event: KeyLike): UndoKey | null {
  if (!event.ctrlKey && !event.metaKey) {
    return null;
  }
  if (writing(event.target)) {
    return null;
  }
  const key = event.key.toLowerCase();
  if (key === 'y') {
    return 'REDO';
  }
  if (key !== 'z') {
    return null;
  }
  return event.shiftKey ? 'REDO' : 'UNDO';
}

/** 글을 적는 자리인가. */
function writing(target: EventTarget | null): boolean {
  if (!(target instanceof HTMLElement)) {
    return false;
  }
  const tag = target.tagName.toLowerCase();
  return tag === 'input' || tag === 'textarea' || target.isContentEditable
    || target.getAttribute('contenteditable') === 'true';
}
