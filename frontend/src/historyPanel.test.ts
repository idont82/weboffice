import { describe, expect, it, vi } from 'vitest';

import { renderHistoryPanel, renderVersionPanel, type HistoryEntry, type Version } from './historyPanel';

function entry(fields: Partial<HistoryEntry> = {}): HistoryEntry {
  return {
    changeType: 'UPDATE', before: '처음', after: '고침',
    changedAt: '2026-08-20T09:30:00', changedBy: '홍길동 과장', ...fields,
  };
}

describe('renderHistoryPanel', () => {
  it('값이 어떻게 바뀌었는지 보인다', () => {
    const box = document.createElement('div');

    renderHistoryPanel(box, { title: 'B2 수정 기록', entries: [entry()], onClose: vi.fn() });

    const first = box.querySelector('.history-entry')!;
    expect(first.textContent).toContain('처음');
    expect(first.textContent).toContain('고침');
    expect(first.textContent).toContain('홍길동 과장');
  });

  it('새로 넣은 칸은 앞 값이 없다', () => {
    const box = document.createElement('div');

    renderHistoryPanel(box, {
      title: 'B2', entries: [entry({ changeType: 'INSERT', before: null })], onClose: vi.fn(),
    });

    expect(box.querySelector('.history-before')).toBeNull();
    expect(box.querySelector('.history-after')!.textContent).toBe('고침');
  });

  it('지운 칸은 빈 값으로 적는다', () => {
    const box = document.createElement('div');

    renderHistoryPanel(box, {
      title: 'B2', entries: [entry({ changeType: 'DELETE', after: null })], onClose: vi.fn(),
    });

    expect(box.querySelector('.history-after')!.textContent).toBe('(빈 칸)');
  });

  it('기록이 없으면 그렇다고 적는다', () => {
    const box = document.createElement('div');

    renderHistoryPanel(box, { title: 'B2', entries: [], onClose: vi.fn() });

    expect(box.textContent).toContain('기록이 없습니다');
  });

  it('닫기를 누르면 알려 준다', () => {
    const box = document.createElement('div');
    const onClose = vi.fn();

    renderHistoryPanel(box, { title: 'B2', entries: [], onClose });
    box.querySelector<HTMLElement>('.history-close')!.click();

    expect(onClose).toHaveBeenCalled();
  });

  it('제목에 사람 글이 그대로 들어가도 스크립트가 되지 않는다', () => {
    // 제목에는 문서·시트 이름이 들어간다. 남이 지은 이름이 내 화면에서 실행되면 안 된다.
    const box = document.createElement('div');

    renderHistoryPanel(box, {
      title: '<img src=x onerror=alert(1)>', entries: [], onClose: vi.fn(),
    });

    expect(box.querySelector('img')).toBeNull();
  });
});

function version(fields: Partial<Version> = {}): Version {
  return {
    versionId: 42, fromAt: '2026-08-20T09:00:00', toAt: '2026-08-20T09:20:00',
    changeCount: 7, editors: ['홍길동 과장'], ...fields,
  };
}

describe('renderVersionPanel', () => {
  it('버전마다 시각과 사람을 보인다', () => {
    const box = document.createElement('div');

    renderVersionPanel(box, { versions: [version()], onRestore: vi.fn(), onClose: vi.fn() });

    const first = box.querySelector('.history-entry')!;
    expect(first.textContent).toContain('홍길동 과장');
    expect(first.textContent).toContain('바뀐 자리 보기');
  });

  it('펼치기 전에는 수를 적지 않는다', () => {
    // 기록 건수(139)와 실제 바뀐 자리(2)는 다른 수다. 펼치면 숫자가 확 줄어
    // 무엇이 맞는 수인지 헷갈렸다(2026-08-20 지적).
    const box = document.createElement('div');

    renderVersionPanel(box, {
      versions: [version({ changeCount: 139 })], onRestore: vi.fn(), onClose: vi.fn(),
    });

    expect(box.textContent).not.toContain('139');
  });

  it('되돌리기는 두 번 눌러야 한다', () => {
    const box = document.createElement('div');
    const onRestore = vi.fn();

    // 맨 위는 지금 상태라 되돌릴 것이 없다. 그 아래 것을 누른다.
    renderVersionPanel(box, {
      versions: [version({ versionId: 99 }), version()], onRestore, onClose: vi.fn(),
    });
    const button = box.querySelector<HTMLElement>('[data-version="42"]')!;
    button.click();

    // 한 번으로 되돌아가면 잘못 누른 것이 곧 되돌리기가 된다. 다시 물어본다.
    expect(onRestore).not.toHaveBeenCalled();
    expect(button.textContent).toContain('정말');

    button.click();
    expect(onRestore).toHaveBeenCalledWith(42);
  });

  it('맨 위(지금 상태) 버전에는 되돌리기를 두지 않는다', () => {
    // 지금 상태로 되돌리는 것은 아무 일도 아니다. 단추가 있으면 눌러 보게 된다.
    const box = document.createElement('div');

    renderVersionPanel(box, {
      versions: [version({ versionId: 42 }), version({ versionId: 7 })],
      onRestore: vi.fn(), onClose: vi.fn(),
    });

    expect(box.querySelector('[data-version="42"]')).toBeNull();
    expect(box.querySelector('[data-version="7"]')).not.toBeNull();
  });

  it('기록이 없으면 그렇다고 적는다', () => {
    const box = document.createElement('div');

    renderVersionPanel(box, { versions: [], onRestore: vi.fn(), onClose: vi.fn() });

    expect(box.textContent).toContain('기록이 없습니다');
  });

  it('버전이 있으면 없다는 말을 붙이지 않는다', () => {
    // 목록 위에 "기록이 없습니다" 가 함께 떠 있었다(2026-08-20 실측).
    const box = document.createElement('div');

    renderVersionPanel(box, { versions: [version()], onRestore: vi.fn(), onClose: vi.fn() });

    expect(box.textContent).not.toContain('기록이 없습니다');
  });
});

describe('버전 펼쳐 보기', () => {
  it('펼치면 무엇이 바뀌었는지 물어본다', async () => {
    // "고친 칸 5개" 만으로는 어느 판인지 알 수 없다(2026-08-20 지적).
    const box = document.createElement('div');
    const onOpen = vi.fn().mockResolvedValue([
      { where: '시트1 · C2', before: '처음', after: '고침',
        changedAt: '2026-08-20T09:30:00', changedBy: '홍길동 과장' },
    ]);

    renderVersionPanel(box, {
      versions: [version({ versionId: 7 })], onRestore: vi.fn(), onClose: vi.fn(),
      onOpen,
    });
    box.querySelector<HTMLElement>('[data-open="7"]')!.click();
    await Promise.resolve();
    await Promise.resolve();

    expect(onOpen).toHaveBeenCalledWith(7);
    // 펼치고 나면 실제로 바뀐 자리 수를 적는다.
    expect(box.querySelector('[data-open="7"]')!.textContent).toContain('바뀐 자리 1개');
    expect(box.textContent).toContain('시트1 · C2');
    expect(box.textContent).toContain('처음');
    expect(box.textContent).toContain('고침');
  });

  it('바뀐 것이 없으면 그렇다고 적는다', async () => {
    const box = document.createElement('div');

    renderVersionPanel(box, {
      versions: [version({ versionId: 7 })], onRestore: vi.fn(), onClose: vi.fn(),
      onOpen: vi.fn().mockResolvedValue([]),
    });
    box.querySelector<HTMLElement>('[data-open="7"]')!.click();
    await Promise.resolve();
    await Promise.resolve();

    expect(box.textContent).toContain('바뀐 자리를 찾지 못했습니다');
  });
});

describe('다시 읽기', () => {
  it('단추를 누르면 알려 준다', () => {
    // 기록을 열어 둔 채로 고치면 바뀐 것이 바로 안 보였다(2026-08-20 지적).
    const box = document.createElement('div');
    const onRefresh = vi.fn();

    renderVersionPanel(box, {
      versions: [version()], onRestore: vi.fn(), onClose: vi.fn(), onRefresh,
    });
    box.querySelector<HTMLElement>('.history-refresh')!.click();

    expect(onRefresh).toHaveBeenCalled();
  });

  it('다시 읽기를 안 주면 단추도 없다', () => {
    const box = document.createElement('div');

    renderVersionPanel(box, { versions: [version()], onRestore: vi.fn(), onClose: vi.fn() });

    expect(box.querySelector('.history-refresh')).toBeNull();
  });
});
