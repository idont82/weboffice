import { describe, expect, it } from 'vitest';

import { hideBusy, renderStatus, showBusy } from './busy';

describe('renderStatus', () => {
  it('글만 있는 알림에는 도는 표시가 없다', () => {
    const box = document.createElement('span');

    renderStatus(box, '저장했습니다');

    expect(box.textContent).toBe('저장했습니다');
    expect(box.querySelector('.wo-spinner')).toBeNull();
  });

  it('일하는 중이면 도는 표시를 앞에 붙인다', () => {
    // 글만 바뀌면 눈에 안 띈다(2026-08-13 지적: 올리는 중인지 모르겠다).
    const box = document.createElement('span');

    renderStatus(box, '올리는 중…', true);

    expect(box.querySelector('.wo-spinner')).not.toBeNull();
    expect(box.textContent).toContain('올리는 중…');
  });

  it('일이 끝나면 도는 표시를 걷는다', () => {
    const box = document.createElement('span');

    renderStatus(box, '올리는 중…', true);
    renderStatus(box, '올렸습니다');

    expect(box.querySelector('.wo-spinner')).toBeNull();
  });

  it('빈 알림은 자리까지 비운다', () => {
    const box = document.createElement('span');

    renderStatus(box, '올리는 중…', true);
    renderStatus(box, '');

    expect(box.textContent).toBe('');
    expect(box.children).toHaveLength(0);
  });
});

describe('busyOverlay', () => {
  it('일하는 동안 화면 가운데에 알린다', () => {
    // 목록 화면에는 상단 알림 줄이 없다. 파일을 올리는 동안 아무 표시가 없어
    // 멈춘 줄 안다(2026-08-13 지적).
    const root = document.createElement('div');

    showBusy(root, '올리는 중…');

    const layer = root.querySelector('.wo-busy');
    expect(layer).not.toBeNull();
    expect(layer?.textContent).toContain('올리는 중…');
    expect(layer?.querySelector('.wo-spinner')).not.toBeNull();
  });

  it('두 번 알려도 하나만 뜬다', () => {
    const root = document.createElement('div');

    showBusy(root, '올리는 중…');
    showBusy(root, '아직 올리는 중…');

    expect(root.querySelectorAll('.wo-busy')).toHaveLength(1);
    expect(root.querySelector('.wo-busy')?.textContent).toContain('아직');
  });

  it('끝나면 걷는다', () => {
    const root = document.createElement('div');

    showBusy(root, '올리는 중…');
    hideBusy(root);

    expect(root.querySelector('.wo-busy')).toBeNull();
  });

  it('안 띄운 채로 걷어도 탈이 없다', () => {
    expect(() => hideBusy(document.createElement('div'))).not.toThrow();
  });
});
