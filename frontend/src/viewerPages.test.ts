import { describe, expect, it } from 'vitest';

import { pageAt } from './viewerPages';

describe('보고 있는 쪽 세기', () => {
  const 쪽높이 = [1000, 1000, 1000];

  it('맨 위면 첫 쪽이다', () => {
    expect(pageAt(쪽높이, 20, 1, 0, 800)).toBe(1);
  });

  it('화면 한가운데가 걸친 쪽을 센다', () => {
    // 첫 쪽 1000 + 사이 20. 1200 만큼 내렸으면 화면 가운데(1200+400)는 둘째 쪽이다.
    expect(pageAt(쪽높이, 20, 1, 1200, 800)).toBe(2);
  });

  it('확대·축소를 따라간다', () => {
    // 반으로 줄이면 쪽도 반이다. 1200 내렸으면 가운데가 1600 - 셋째 쪽이다.
    expect(pageAt(쪽높이, 20, 0.5, 1200, 800)).toBe(3);
  });

  it('끝까지 내리면 마지막 쪽이다', () => {
    expect(pageAt(쪽높이, 20, 1, 99999, 800)).toBe(3);
  });

  it('쪽이 없으면 0 이다', () => {
    expect(pageAt([], 20, 1, 0, 800)).toBe(0);
  });
});
