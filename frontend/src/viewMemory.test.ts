import { beforeEach, describe, expect, it } from 'vitest';

import {
  clearMemory, lastSheetOf, rememberSheet, rememberZoom, useMemory, zoomOf,
} from './viewMemory';

describe('viewMemory', () => {
  beforeEach(() => {
    clearMemory();
  });

  it('서버에서 받은 기억을 그대로 쓴다', () => {
    // 계정을 따라간다. 브라우저에 두면 다른 PC 에서 기억이 없다(2026-08-13 지적).
    useMemory(7, { lastSheetSeq: 42, zooms: { 42: 1.5 } });

    expect(lastSheetOf(7)).toBe(42);
    expect(zoomOf(42)).toBe(1.5);
  });

  it('다른 문서 기억을 받으면 앞 문서 것은 잊는다', () => {
    useMemory(7, { lastSheetSeq: 42, zooms: { 42: 1.5 } });
    useMemory(8, { lastSheetSeq: 99, zooms: {} });

    expect(lastSheetOf(8)).toBe(99);
    expect(lastSheetOf(7)).toBeNull();
  });

  it('받은 적 없으면 아무것도 없다', () => {
    expect(lastSheetOf(1)).toBeNull();
    expect(zoomOf(42)).toBeNull();
  });

  it('시트를 옮기면 그 시트를 마지막으로 본 것으로 둔다', () => {
    useMemory(7, { lastSheetSeq: 42, zooms: {} });

    rememberSheet(7, 43);

    expect(lastSheetOf(7)).toBe(43);
  });

  it('배율을 바꾸면 그 시트 것으로 둔다', () => {
    useMemory(7, { lastSheetSeq: 42, zooms: {} });

    rememberZoom(42, 0.8);

    expect(zoomOf(42)).toBe(0.8);
  });

  it('말이 안 되는 배율은 담지 않는다', () => {
    useMemory(7, { lastSheetSeq: 42, zooms: {} });

    rememberZoom(42, 0);
    rememberZoom(43, 12);
    rememberZoom(44, Number.NaN);

    expect(zoomOf(42)).toBeNull();
    expect(zoomOf(43)).toBeNull();
    expect(zoomOf(44)).toBeNull();
  });

  it('담을 만한 배율인지 가른다', () => {
    expect(rememberZoom(42, 1.5)).toBe(true);
    expect(rememberZoom(42, 9)).toBe(false);
  });
});
