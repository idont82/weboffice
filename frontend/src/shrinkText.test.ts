import { describe, expect, it } from 'vitest';
import { shrinkRatio } from './shrinkText';

describe('넘치는 글 줄이기', () => {
  it('들어가면 그대로 둔다', () => {
    expect(shrinkRatio(80, 100)).toBe(1);
  });

  it('넘친 만큼 줄인다', () => {
    expect(shrinkRatio(200, 100)).toBe(0.5);
  });

  it('4분의 1보다 작게는 안 줄인다', () => {
    expect(shrinkRatio(1000, 100)).toBe(0.25);
  });

  it('잴 수 없으면 그대로 둔다', () => {
    expect(shrinkRatio(0, 100)).toBe(1);
    expect(shrinkRatio(100, 0)).toBe(1);
  });
});
