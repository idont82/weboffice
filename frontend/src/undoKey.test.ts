import { describe, expect, it } from 'vitest';

import { undoKeyOf } from './undoKey';

/** 키 눌림 시늉. 어디서 눌렀는지(target)와 키만 본다. */
function 눌림(fields: Partial<{ key: string; ctrlKey: boolean; metaKey: boolean;
  shiftKey: boolean; tag: string; editable: boolean }> = {}) {
  const target = document.createElement(fields.tag ?? 'div');
  if (fields.editable) {
    target.setAttribute('contenteditable', 'true');
  }
  return {
    key: fields.key ?? 'z',
    ctrlKey: fields.ctrlKey ?? true,
    metaKey: fields.metaKey ?? false,
    shiftKey: fields.shiftKey ?? false,
    target,
  };
}

describe('undoKeyOf', () => {
  it('Ctrl+Z 는 되돌리기다', () => {
    expect(undoKeyOf(눌림())).toBe('UNDO');
  });

  it('Ctrl+Shift+Z 는 다시 실행이다', () => {
    expect(undoKeyOf(눌림({ shiftKey: true }))).toBe('REDO');
  });

  it('Ctrl+Y 도 다시 실행이다', () => {
    expect(undoKeyOf(눌림({ key: 'y' }))).toBe('REDO');
  });

  it('맥에서는 Cmd 로도 된다', () => {
    expect(undoKeyOf(눌림({ ctrlKey: false, metaKey: true }))).toBe('UNDO');
  });

  it('그냥 Z 는 아니다', () => {
    expect(undoKeyOf(눌림({ ctrlKey: false }))).toBeNull();
  });

  it('입력칸에서는 브라우저에 맡긴다', () => {
    // 대화 입력칸에서 되돌리면 적던 글이 아니라 시트가 되돌아간다.
    expect(undoKeyOf(눌림({ tag: 'input' }))).toBeNull();
    expect(undoKeyOf(눌림({ tag: 'textarea' }))).toBeNull();
    expect(undoKeyOf(눌림({ editable: true }))).toBeNull();
  });
});
