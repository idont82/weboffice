import { describe, expect, it } from 'vitest';

import { PAGE, paperOffset, rulerTicks, cmToPx } from './ruler';

describe('cmToPx', () => {
  it('1cm 은 96dpi 에서 37.8픽셀쯤이다', () => {
    // Univer 의 길이 단위가 CSS 픽셀이라 96dpi 로 환산한다. A4 폭 21cm 이
    // 794픽셀인 것과 맞아떨어져야 한다.
    expect(cmToPx(1)).toBeCloseTo(37.795, 2);
    expect(cmToPx(21)).toBeCloseTo(PAGE.width, 0);
  });
});

describe('rulerTicks', () => {
  it('1cm 마다 큰 눈금, 0.5cm 마다 작은 눈금을 놓는다', () => {
    const ticks = rulerTicks(cmToPx(2));

    expect(ticks.map((t) => t.major)).toEqual([true, false, true, false, true]);
  });

  it('큰 눈금에만 숫자를 붙인다', () => {
    // 0.5cm 마다 숫자를 적으면 눈금자가 글씨로 덮인다.
    const ticks = rulerTicks(cmToPx(2));

    expect(ticks.map((t) => t.label)).toEqual(['0', null, '1', null, '2']);
  });

  it('눈금 자리는 왼쪽 끝에서 잰 픽셀이다', () => {
    const ticks = rulerTicks(cmToPx(1));

    expect(ticks.map((t) => Math.round(t.px))).toEqual([0, 19, 38]);
  });

  it('길이가 0이면 눈금이 없다', () => {
    expect(rulerTicks(0)).toEqual([]);
  });

  it('시작 자리를 옮기면 숫자도 함께 옮긴다', () => {
    // 본문 시작(왼쪽 여백 끝)이 0이다. 워드·한글이 그렇고, 들여쓰기를 재는
    // 기준도 그 자리다.
    const ticks = rulerTicks(cmToPx(3), cmToPx(1));

    expect(ticks[0].label).toBe('-1');
    expect(ticks.find((t) => Math.round(t.px) === Math.round(cmToPx(1)))?.label).toBe('0');
  });
});

describe('paperOffset', () => {
  it('종이를 캔버스 한가운데 놓는다', () => {
    expect(paperOffset(1000, 800, 1)).toBe(100);
  });

  it('문서 종이 크기로 잰다', () => {
    // 우리 기본(A4 794px)으로 재면 레터 문서(816px)에서 11px 씩 밀린다. 눈금자와
    // 표 손잡이가 실제 줄보다 오른쪽에 붙었다(2026-08-13 지적).
    expect(paperOffset(1000, 816, 1)).toBe(92);
  });

  it('종이가 캔버스보다 넓으면 왼쪽 끝에 붙인다', () => {
    expect(paperOffset(500, 816, 1)).toBe(0);
  });

  it('배율을 곱한 크기로 잰다', () => {
    expect(paperOffset(1000, 800, 0.5)).toBe(300);
  });
});

describe('rulerTicks 기준점', () => {
  it('본문 시작에서 눈금을 센다', () => {
    // 여백이 센티 단위가 아니면(레터 문서는 0.5인치=1.27cm) 0 에서 센 눈금은 어느
    // 것도 정수 센티에 닿지 않아 숫자가 하나도 안 나온다(2026-08-13 지적).
    const ticks = rulerTicks(800, 48);
    const labels = ticks.filter((tick) => tick.label != null);

    expect(labels.length).toBeGreaterThan(10);
    expect(ticks.find((tick) => tick.label === '0')?.px).toBeCloseTo(48, 6);
    expect(ticks.find((tick) => tick.label === '1')?.px).toBeCloseTo(48 + cmToPx(1), 6);
    expect(ticks.find((tick) => tick.label === '-1')?.px).toBeCloseTo(48 - cmToPx(1), 6);
  });

  it('눈금이 눈금자 밖으로 나가지 않는다', () => {
    for (const tick of rulerTicks(800, 48)) {
      expect(tick.px).toBeGreaterThanOrEqual(0);
      expect(tick.px).toBeLessThanOrEqual(800);
    }
  });
});

describe('확대·축소', () => {
  it('배율만큼 눈금 사이가 좁아진다', () => {
    // 종이가 반으로 줄면 눈금도 반으로 줄어야 자와 종이가 맞는다(2026-08-19 지적).
    const half = rulerTicks(400, 0, 0.5);
    const full = rulerTicks(400, 0, 1);

    expect(half[1].px - half[0].px).toBeCloseTo((full[1].px - full[0].px) / 2, 3);
  });

  it('배율이 달라도 숫자는 실제 센티미터다', () => {
    const ticks = rulerTicks(400, 0, 0.5);
    const labelled = ticks.filter((one) => one.label != null);

    expect(labelled[0].label).toBe('0');
    expect(labelled[1].label).toBe('1');
  });
});
