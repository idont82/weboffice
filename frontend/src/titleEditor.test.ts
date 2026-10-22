import { beforeEach, describe, expect, it, vi } from 'vitest';

import { createTitleEditor } from './titleEditor';

function element() {
  const el = document.createElement('div');
  document.body.append(el);
  return el;
}

describe('createTitleEditor', () => {
  beforeEach(() => { document.body.innerHTML = ''; });

  it('제목을 보여준다', () => {
    const el = element();
    createTitleEditor({ element: el, canWrite: true, onRename: vi.fn() }).show('2분기 실적');

    expect(el.textContent).toBe('2분기 실적');
  });

  it('쓰기 권한이 없으면 고칠 수 없다', () => {
    const el = element();
    createTitleEditor({ element: el, canWrite: false, onRename: vi.fn() }).show('제목');

    // isContentEditable 은 jsdom 이 구현하지 않아 늘 undefined 다. 속성을 본다.
    expect(el.getAttribute('contenteditable')).toBeNull();
  });

  it('쓰기 권한이 있으면 고칠 수 있다', () => {
    const el = element();
    createTitleEditor({ element: el, canWrite: true, onRename: vi.fn() }).show('제목');

    // plaintext-only 여야 붙여넣기로 서식·태그가 들어오지 않는다.
    expect(el.getAttribute('contenteditable')).toBe('plaintext-only');
  });

  it('권한은 나중에 바뀔 수 있다', () => {
    // 문서를 바꿔 열면 권한도 바뀐다. 편집기를 다시 만들면 리스너가 겹쳐 붙는다.
    const el = element();
    const editor = createTitleEditor({ element: el, canWrite: true, onRename: vi.fn() });

    editor.setEditable(false);
    expect(el.getAttribute('contenteditable')).toBeNull();
    editor.setEditable(true);
    expect(el.getAttribute('contenteditable')).toBe('plaintext-only');
  });

  it('읽기 전용이면 blur 가 와도 저장하지 않는다', () => {
    const el = element();
    const onRename = vi.fn();
    const editor = createTitleEditor({ element: el, canWrite: false, onRename });
    editor.show('제목');

    el.dispatchEvent(new FocusEvent('blur'));

    expect(onRename).not.toHaveBeenCalled();
    expect(el.textContent).toBe('제목');
  });

  it('바뀐 제목만 보낸다', () => {
    // 누르기만 하고 안 고쳐도 저장하면 이력이 헛되이 쌓인다.
    const el = element();
    const onRename = vi.fn();
    createTitleEditor({ element: el, canWrite: true, onRename }).show('제목');

    el.dispatchEvent(new FocusEvent('blur'));
    expect(onRename).not.toHaveBeenCalled();

    el.textContent = '새 제목';
    el.dispatchEvent(new FocusEvent('blur'));
    expect(onRename).toHaveBeenCalledWith('새 제목');
  });

  it('비우면 옛 제목으로 되돌린다', () => {
    // 서버도 거부하지만, 화면이 빈 채로 남으면 무엇이 잘못됐는지 안 보인다.
    const el = element();
    const onRename = vi.fn();
    createTitleEditor({ element: el, canWrite: true, onRename }).show('제목');

    el.textContent = '   ';
    el.dispatchEvent(new FocusEvent('blur'));

    expect(el.textContent).toBe('제목');
    expect(onRename).not.toHaveBeenCalled();
  });

  it('엔터로 확정하고 Esc 로 되돌린다', () => {
    const el = element();
    const onRename = vi.fn();
    createTitleEditor({ element: el, canWrite: true, onRename }).show('제목');

    el.textContent = '새 제목';
    el.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    expect(el.textContent).toBe('제목');
    expect(onRename).not.toHaveBeenCalled();
  });
});
