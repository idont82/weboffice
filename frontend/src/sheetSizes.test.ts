import { describe, expect, it } from 'vitest';

import { HIDDEN, sizesOf } from './sheetSizes';

const DEFAULTS = { defaultRowHeight: 20, defaultColumnWidth: 88 };

describe('sizesOf', () => {
  it('기본과 다른 높이만 담는다', () => {
    // 시트 하나에 행이 백만 개다. 기본인 줄까지 담으면 표가 터진다.
    const sizes = sizesOf({
      ...DEFAULTS,
      rowData: { 0: { h: 20 }, 1: { h: 40 } },
    });

    expect(sizes).toEqual([{ axisType: 'ROW', indexNo: 1, sizePx: 40 }]);
  });

  it('기본과 다른 너비만 담는다', () => {
    const sizes = sizesOf({
      ...DEFAULTS,
      columnData: { 2: { w: 88 }, 3: { w: 120 } },
    });

    expect(sizes).toEqual([{ axisType: 'COLUMN', indexNo: 3, sizePx: 120 }]);
  });

  it('숨긴 열은 0 으로 담는다', () => {
    // 숨김을 안 담으면 새로고침에 되살아난다(2026-09-03 지적). 엑셀도 너비 0 을
    // 숨김으로 본다.
    const sizes = sizesOf({
      ...DEFAULTS,
      columnData: { 1: { w: 88, hd: 1 } },
    });

    expect(sizes).toEqual([{ axisType: 'COLUMN', indexNo: 1, sizePx: HIDDEN }]);
  });

  it('숨긴 행도 0 으로 담는다', () => {
    const sizes = sizesOf({
      ...DEFAULTS,
      rowData: { 5: { h: 20, hd: 1 } },
    });

    expect(sizes).toEqual([{ axisType: 'ROW', indexNo: 5, sizePx: HIDDEN }]);
  });

  it('숨김은 크기가 기본과 같아도 담는다', () => {
    // 크기만 보면 "손댄 적 없음" 이라 빠진다. 숨김은 크기와 다른 사실이다.
    const sizes = sizesOf({
      ...DEFAULTS,
      columnData: { 1: { hd: 1 } },
    });

    expect(sizes).toHaveLength(1);
  });

  it('숨김을 풀면 크기로 돌아간다', () => {
    const sizes = sizesOf({
      ...DEFAULTS,
      columnData: { 1: { w: 150, hd: 0 } },
    });

    expect(sizes).toEqual([{ axisType: 'COLUMN', indexNo: 1, sizePx: 150 }]);
  });

  it('자동 높이(ah)도 높이로 본다', () => {
    const sizes = sizesOf({ ...DEFAULTS, rowData: { 0: { ah: 33 } } });

    expect(sizes).toEqual([{ axisType: 'ROW', indexNo: 0, sizePx: 33 }]);
  });

  it('아무것도 없으면 빈 목록이다', () => {
    expect(sizesOf(DEFAULTS)).toEqual([]);
  });
});
