import { describe, expect, it } from 'vitest';

import { spotAfterRemove } from './afterRemove';

describe('spotAfterRemove', () => {
  it('행을 지우면 그 자리 첫 칸을 고른다', () => {
    // 지운 줄이 그대로 골라져 있으면 다음 조작이 엉뚱한 줄에 걸린다(2026-08-27 지적).
    const spot = spotAfterRemove('sheet.command.remove-row',
      { range: { startRow: 4, endRow: 6, startColumn: 0, endColumn: 20 } });

    expect(spot).toEqual({ row: 4, column: 0 });
  });

  it('열을 지우면 그 자리 첫 줄을 고른다', () => {
    const spot = spotAfterRemove('sheet.command.remove-col',
      { range: { startRow: 0, endRow: 100, startColumn: 3, endColumn: 4 } });

    expect(spot).toEqual({ row: 0, column: 3 });
  });

  it('다른 명령은 손대지 않는다', () => {
    expect(spotAfterRemove('sheet.command.set-range-values',
      { range: { startRow: 1, endRow: 1, startColumn: 1, endColumn: 1 } })).toBeNull();
  });

  it('범위가 없으면 손대지 않는다', () => {
    expect(spotAfterRemove('sheet.command.remove-row', undefined)).toBeNull();
    expect(spotAfterRemove('sheet.command.remove-row', {})).toBeNull();
  });
});
