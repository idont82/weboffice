import { describe, expect, it, vi } from 'vitest';

import { keepEditorFocus } from './focusKeeper';

function setup() {
  document.body.innerHTML = '';
  const editor = document.createElement('div');
  editor.id = 'editor';
  editor.tabIndex = 0;
  const other = document.createElement('input');
  document.body.append(editor, other);
  return { editor, other };
}

describe('keepEditorFocus', () => {
  it('아무 데도 안 가고 떨어진 포커스를 편집기로 되돌린다', () => {
    // 색 팝업이 닫히면서 포커스를 body 에 떨군다. 그 상태에서는 키보드가 아무
    // 데도 닿지 않아 글자가 입력되지 않는다(2026-08-11 실측).
    const { editor } = setup();
    keepEditorFocus(() => 'editor');

    vi.useFakeTimers();
    editor.focus();
    editor.blur();
    vi.runAllTimers();
    vi.useRealTimers();

    expect(document.activeElement).toBe(editor);
  });

  it('다른 입력칸으로 간 포커스는 뺏지 않는다', () => {
    // 제목을 고치거나 공유 창에 글을 칠 때 편집기가 포커스를 가로채면 안 된다.
    const { editor, other } = setup();
    keepEditorFocus(() => 'editor');

    editor.focus();
    other.focus();

    expect(document.activeElement).toBe(other);
  });

  it('글 문서를 닫았으면 아무 일도 하지 않는다', () => {
    const { editor } = setup();
    keepEditorFocus(() => null);

    editor.focus();
    editor.blur();

    expect(document.activeElement).not.toBe(editor);
  });
});
