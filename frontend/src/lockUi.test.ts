import { describe, expect, it } from 'vitest';
import { toA1 } from './lockUi';

describe('lockUi', () => {
  it('0-기반 좌표를 A1 표기로 바꾼다', () => {
    expect(toA1(0, 0)).toBe('A1');
    expect(toA1(0, 25)).toBe('Z1');
    expect(toA1(0, 26)).toBe('AA1');
    expect(toA1(99, 27)).toBe('AB100');
  });
});
