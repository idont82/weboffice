import { describe, expect, it } from 'vitest';

import { bySheet } from './saveQueue';

/** 저장 대기 한 줄. 어느 시트 것인지 함께 들고 다닌다. */
function 대기(sheetSeq: number, rowNo: number) {
  return { sheetSeq, cell: { rowNo, columnNo: 0 } };
}

describe('bySheet', () => {
  it('시트별로 나눠 준다', () => {
    // 큐에 담을 때의 시트로 보내야 한다. 보낼 때의 시트로 보내면 시트를 바꾸는
    // 사이에 쌓인 것이 엉뚱한 시트로 간다(2026-08-31 실측: 새 시트를 더했더니
    // 그 값이 첫 시트로 저장됐다).
    const groups = bySheet([대기(7, 0), 대기(9, 1), 대기(7, 2)]);

    expect([...groups.keys()]).toEqual([7, 9]);
    expect(groups.get(7)).toHaveLength(2);
    expect(groups.get(9)).toHaveLength(1);
  });

  it('빈 목록이면 빈 묶음이다', () => {
    expect(bySheet([]).size).toBe(0);
  });

  it('시트를 모르는 것은 뺀다', () => {
    // 어느 시트 것인지 모르면 보내지 않는다 - 찍어서 보내면 남의 시트를 덮는다.
    const groups = bySheet([{ sheetSeq: null, cell: { rowNo: 0, columnNo: 0 } }, 대기(7, 1)]);

    expect([...groups.keys()]).toEqual([7]);
  });
});
