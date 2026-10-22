import { describe, expect, it, vi } from 'vitest';

import { renderAdmin, type AdminEvent } from './viewAdmin';

function event(fields: Partial<AdminEvent> = {}): AdminEvent {
  return {
    eventType: 'ISSUE', callerSystem: '전자결재', fileName: '보고서.xlsx',
    byteSize: 12345, encryptedYn: 'Y', viewerName: '홍길동 책임', tookMs: 250,
    resultCode: 'OK', message: null, clientIp: '192.0.2.92',
    changedAt: '2026-08-20T11:05:00', ...fields,
  };
}

describe('renderAdmin', () => {
  it('이력을 줄로 그린다', () => {
    const box = document.createElement('div');

    renderAdmin(box, {
      events: [event()],
      stats: { issued: 1, opened: 1, failed: 0, avgConvertMs: 250, cacheRows: 1 },
      onPick: vi.fn(),
    });

    const row = box.querySelector('.admin-row')!;
    expect(row.textContent).toContain('보고서.xlsx');
    expect(row.textContent).toContain('전자결재');
    expect(row.textContent).toContain('홍길동 책임');
  });

  it('잠긴 문서를 표시한다', () => {
    const box = document.createElement('div');

    renderAdmin(box, {
      events: [event({ encryptedYn: 'Y' })],
      stats: { issued: 1, opened: 0, failed: 0, avgConvertMs: 0, cacheRows: 0 },
      onPick: vi.fn(),
    });

    expect(box.textContent).toContain('잠김');
  });

  it('통계를 위에 적는다', () => {
    const box = document.createElement('div');

    renderAdmin(box, {
      events: [],
      stats: { issued: 12, opened: 9, failed: 2, avgConvertMs: 340, cacheRows: 5 },
      onPick: vi.fn(),
    });

    expect(box.textContent).toContain('12');
    expect(box.textContent).toContain('340');
  });

  it('갈래를 누르면 알려 준다', () => {
    const box = document.createElement('div');
    const onPick = vi.fn();

    renderAdmin(box, {
      events: [],
      stats: { issued: 0, opened: 0, failed: 0, avgConvertMs: 0, cacheRows: 0 },
      onPick,
    });
    box.querySelector<HTMLElement>('[data-type="ERROR"]')!.click();

    expect(onPick).toHaveBeenCalledWith('ERROR');
  });

  it('파일 이름이 스크립트가 되지 않는다', () => {
    const box = document.createElement('div');

    renderAdmin(box, {
      events: [event({ fileName: '<img src=x onerror=alert(1)>' })],
      stats: { issued: 1, opened: 0, failed: 0, avgConvertMs: 0, cacheRows: 0 },
      onPick: vi.fn(),
    });

    expect(box.querySelector('img')).toBeNull();
  });
});
