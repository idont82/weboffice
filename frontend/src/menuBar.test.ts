import { describe, expect, it, vi } from 'vitest';

import { createMenuBar, type MenuDefinition } from './menuBar';

function build(overrides: Partial<MenuDefinition>[] = []) {
  const run = vi.fn();
  const other = vi.fn();
  const menus: MenuDefinition[] = [
    {
      key: 'file',
      label: '파일',
      items: [
        { label: '새 문서', run },
        { label: '다운로드', run: other, enabled: () => false },
      ],
    },
    { key: 'insert', label: '삽입', items: [{ label: '그림', run: vi.fn() }] },
  ];
  const container = document.createElement('div');
  document.body.append(container);
  createMenuBar(container, menus.map((menu, at) => ({ ...menu, ...overrides[at] })));
  return { container, run, other };
}

function titleOf(container: HTMLElement, key: string) {
  return container.querySelector<HTMLButtonElement>(`[data-menu="${key}"]`)!;
}

function listOf(container: HTMLElement, key: string) {
  return container.querySelector<HTMLElement>(`[data-menu-list="${key}"]`)!;
}

describe('createMenuBar', () => {
  it('다시 그리면 앞의 것이 남지 않는다', () => {
    // 시트를 열었다 글 문서를 열면 메뉴가 통째로 바뀐다. 비우지 않으면 두 벌이
    // 나란히 선다.
    const container = document.createElement('div');
    document.body.append(container);
    createMenuBar(container, [{ key: 'data', label: '데이터', items: [] }]);
    createMenuBar(container, [{ key: 'format', label: '서식', items: [] }]);

    expect(container.querySelector('[data-menu="data"]')).toBeNull();
    expect(container.querySelector('[data-menu="format"]')).not.toBeNull();
  });

  it('누르면 열린다', () => {
    const { container } = build();

    titleOf(container, 'file').click();

    expect(listOf(container, 'file').hidden).toBe(false);
    expect(titleOf(container, 'file').getAttribute('aria-expanded')).toBe('true');
  });

  it('같은 것을 다시 누르면 닫힌다', () => {
    const { container } = build();

    titleOf(container, 'file').click();
    titleOf(container, 'file').click();

    expect(listOf(container, 'file').hidden).toBe(true);
  });

  it('다른 메뉴를 누르면 앞 것이 닫힌다', () => {
    // 둘이 함께 펼쳐져 있으면 어느 것을 고르는 중인지 알 수 없다.
    const { container } = build();

    titleOf(container, 'file').click();
    titleOf(container, 'insert').click();

    expect(listOf(container, 'file').hidden).toBe(true);
    expect(listOf(container, 'insert').hidden).toBe(false);
  });

  it('바깥을 누르면 닫힌다', () => {
    const { container } = build();
    titleOf(container, 'file').click();

    document.body.dispatchEvent(new MouseEvent('pointerdown', { bubbles: true }));

    expect(listOf(container, 'file').hidden).toBe(true);
  });

  it('Esc 로 닫힌다', () => {
    const { container } = build();
    titleOf(container, 'file').click();

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));

    expect(listOf(container, 'file').hidden).toBe(true);
  });

  it('항목을 고르면 실행하고 닫는다', () => {
    const { container, run } = build();
    titleOf(container, 'file').click();

    listOf(container, 'file').querySelector('button')!.click();

    expect(run).toHaveBeenCalledOnce();
    expect(listOf(container, 'file').hidden).toBe(true);
  });

  it('못 쓰는 항목은 눌러도 아무 일이 없다', () => {
    // 문서를 안 열었는데 "다운로드" 가 눌리면 무엇이 내려받아졌는지 알 수 없다.
    const { container, other } = build();
    titleOf(container, 'file').click();

    const items = listOf(container, 'file').querySelectorAll('button');
    expect(items[1].disabled).toBe(true);
    items[1].click();

    expect(other).not.toHaveBeenCalled();
  });

  it('항목을 고른 뒤 뒷정리를 부른다', () => {
    // 누른 뒤 포커스가 메뉴 단추에 남으면 Univer 단축키가 안 듣는다. 그쪽은 포커스가
    // 격자 안일 때만 키를 받는다 - 행을 넣고 Ctrl+Z 를 눌러도 아무 일이 없었다
    // (2026-08-10 지적). 고른 뒤 격자로 포커스를 돌려주는 자리다.
    const afterRun = vi.fn();
    const run = vi.fn();
    const container = document.createElement('div');
    document.body.append(container);
    createMenuBar(container, [{ key: 'file', label: '파일', items: [{ label: '새 문서', run }] }],
      { afterRun });

    titleOf(container, 'file').click();
    listOf(container, 'file').querySelector('button')!.click();

    expect(run).toHaveBeenCalledOnce();
    expect(afterRun).toHaveBeenCalledOnce();
  });

  it('못 쓰는 항목은 뒷정리도 안 부른다', () => {
    const afterRun = vi.fn();
    const container = document.createElement('div');
    document.body.append(container);
    createMenuBar(container, [{
      key: 'file', label: '파일',
      items: [{ label: '다운로드', run: vi.fn(), enabled: () => false }],
    }], { afterRun });

    titleOf(container, 'file').click();
    listOf(container, 'file').querySelector('button')!.click();

    expect(afterRun).not.toHaveBeenCalled();
  });

  it('창을 여는 항목은 포커스를 뺏지 않는다', () => {
    // 표 넣기·찾기처럼 대화상자를 여는 항목이다. 뒷정리로 편집기에 포커스를 주면
    // 방금 뜬 창이 곧바로 닫힌다(2026-08-11 실측: 표가 들어가지 않았다).
    const afterRun = vi.fn();
    const container = document.createElement('div');
    document.body.append(container);
    createMenuBar(container, [{
      key: 'insert', label: '삽입',
      items: [{ label: '표', run: vi.fn(), keepFocus: true }],
    }], { afterRun });

    titleOf(container, 'insert').click();
    listOf(container, 'insert').querySelector('button')!.click();

    expect(afterRun).not.toHaveBeenCalled();
  });

  it('아이콘을 글자 앞에 둔다', () => {
    // 글자만 늘어놓으면 목록이 한 덩어리로 읽힌다. 아이콘이 앞에 있으면 찾던 것에
    // 눈이 먼저 간다.
    const container = document.createElement('div');
    document.body.append(container);
    createMenuBar(container, [{
      key: 'file', label: '파일',
      items: [{ label: '새 문서', run: vi.fn(), icon: '<svg data-test="doc"></svg>' }],
    }]);

    const item = listOf(container, 'file').querySelector('button')!;
    expect(item.querySelector('.menu-icon [data-test="doc"]')).not.toBeNull();
    expect(item.firstElementChild!.className).toBe('menu-icon');
    expect(item.textContent).toBe('새 문서');
  });

  it('아이콘이 없어도 글자는 자리를 맞춘다', () => {
    // 아이콘 없는 항목만 들여쓰기가 어긋나면 목록이 삐뚤어 보인다.
    const container = document.createElement('div');
    document.body.append(container);
    createMenuBar(container, [{
      key: 'file', label: '파일', items: [{ label: '새 문서', run: vi.fn() }],
    }]);

    const item = listOf(container, 'file').querySelector('button')!;
    expect(item.firstElementChild?.className).toBe('menu-icon');
  });

  it('열 때마다 쓸 수 있는지 다시 본다', () => {
    // 문서를 연 뒤 메뉴를 열면 그때는 쓸 수 있어야 한다. 만들 때 한 번 보고 말면
    // 시작 화면에서 굳은 상태 그대로 남는다.
    let ready = false;
    const container = document.createElement('div');
    document.body.append(container);
    createMenuBar(container, [{
      key: 'file', label: '파일',
      items: [{ label: '다운로드', run: vi.fn(), enabled: () => ready }],
    }]);

    titleOf(container, 'file').click();
    expect(listOf(container, 'file').querySelector('button')!.disabled).toBe(true);

    titleOf(container, 'file').click();
    ready = true;
    titleOf(container, 'file').click();

    expect(listOf(container, 'file').querySelector('button')!.disabled).toBe(false);
  });
});

describe('지금 값 짚기', () => {
  it('짚은 항목에 표시를 남긴다', () => {
    // 줄 간격처럼 여럿 가운데 하나가 지금 값인 항목이다. 표시가 없으면 지금
    // 무엇인지 알 수 없다(2026-08-21 지적).
    const container = document.createElement('div');
    createMenuBar(container, [{
      key: 'format',
      label: '서식',
      items: [
        { label: '줄 간격 1.0', run: () => {}, checked: () => false },
        { label: '줄 간격 1.5', run: () => {}, checked: () => true },
      ],
    }]);

    container.querySelector<HTMLElement>('[data-menu="format"]')!.click();
    const buttons = container.querySelectorAll('[data-menu-list="format"] button');

    expect(buttons[0].getAttribute('aria-checked')).toBe('false');
    expect(buttons[1].getAttribute('aria-checked')).toBe('true');
    expect(buttons[1].classList.contains('menu-checked')).toBe(true);
  });

  it('펼칠 때마다 다시 묻는다', () => {
    // 만들 때 한 번 보고 말면 커서를 옮겨도 표시가 그대로 남는다.
    const container = document.createElement('div');
    let now = 1;
    createMenuBar(container, [{
      key: 'format',
      label: '서식',
      items: [{ label: '줄 간격 1.5', run: () => {}, checked: () => now === 1.5 }],
    }]);

    const title = container.querySelector<HTMLElement>('[data-menu="format"]')!;
    title.click();
    expect(container.querySelector('[data-menu-list="format"] button')!
      .getAttribute('aria-checked')).toBe('false');

    title.click();
    now = 1.5;
    title.click();
    expect(container.querySelector('[data-menu-list="format"] button')!
      .getAttribute('aria-checked')).toBe('true');
  });
});
