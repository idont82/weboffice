import { describe, expect, it } from 'vitest';

import { watermark } from './watermark';

describe('watermark', () => {
  it('열람자 이름을 여러 번 적는다', () => {
    // 한 번만 적으면 그 자리를 가리고 찍으면 그만이다.
    const box = document.createElement('div');

    watermark(box, '홍길동 책임 · 2026-08-20 11:05');

    const marks = box.querySelectorAll('.wo-watermark-word');
    expect(marks.length).toBeGreaterThan(4);
    expect(marks[0].textContent).toContain('홍길동 책임');
  });

  it('글이 스크립트가 되지 않는다', () => {
    // 열람자 이름은 다른 시스템이 준 값이다. 믿을 수 없다.
    const box = document.createElement('div');

    watermark(box, '<img src=x onerror=alert(1)>');

    expect(box.querySelector('img')).toBeNull();
  });

  it('두 번 불러도 층은 하나다', () => {
    const box = document.createElement('div');

    watermark(box, '가');
    watermark(box, '나');

    expect(box.querySelectorAll('.wo-watermark').length).toBe(1);
  });

  it('마우스를 가로채지 않는다', () => {
    // 문서를 스크롤·선택하는 것을 막으면 보기 화면이 못 쓰게 된다.
    const box = document.createElement('div');

    watermark(box, '가');

    const layer = box.querySelector<HTMLElement>('.wo-watermark')!;
    expect(layer.style.pointerEvents).toBe('none');
  });
});

describe('기울기', () => {
  it('글씨를 30도 눕혀 깐다', () => {
    const box = document.createElement('div');
    watermark(box, '홍길동 책임 · 2026-08-24 11:05');

    const marks = box.querySelectorAll<HTMLElement>('.wo-watermark-word');
    expect(marks.length).toBeGreaterThan(0);
    for (const mark of marks) {
      expect(mark.style.transform).toBe('rotate(-30deg)');
    }
  });
});
