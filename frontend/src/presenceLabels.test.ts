import { describe, expect, it } from 'vitest';

import { trackMoves, visibleLabels } from './presenceLabels';

/**
 * 이름표를 언제까지 보일지.
 *
 * <p>이름표는 자리 <b>위 칸</b>에 그린다. 상자 안에 넣을 자리가 없어서다. 그래서
 * 누가 R15 에 앉으면 R14 의 글자가 안 보인다(2026-08-07 지적).
 *
 * <p>잠시 뒤 이름표만 감추고 테두리는 남긴다. 누가 어디 있는지는 테두리로 알 수
 * 있고 이름은 위쪽 접속자 칩에 있다.
 *
 * <p>어려운 곳은 "잠시" 를 언제부터 세느냐다. 자리 목록은 주기적으로 다시 읽으므로
 * 받을 때마다 세면 이름표가 영영 안 사라진다. <b>실제로 움직였을 때</b>부터 센다.
 */
describe('trackMoves', () => {
  it('처음 본 자리는 지금부터 센다', () => {
    const moves = trackMoves(new Map(), [{ key: 'a', signature: 'A1' }], 1_000);

    expect(moves.get('a')?.since).toBe(1_000);
  });

  it('그대로인 자리는 처음 본 시각을 지킨다', () => {
    // 목록을 다시 읽었을 뿐인데 시각을 갱신하면 이름표가 영영 안 사라진다.
    const before = trackMoves(new Map(), [{ key: 'a', signature: 'A1' }], 1_000);

    const after = trackMoves(before, [{ key: 'a', signature: 'A1' }], 5_000);

    expect(after.get('a')?.since).toBe(1_000);
  });

  it('자리를 옮기면 다시 센다', () => {
    const before = trackMoves(new Map(), [{ key: 'a', signature: 'A1' }], 1_000);

    const after = trackMoves(before, [{ key: 'a', signature: 'B7' }], 5_000);

    expect(after.get('a')?.since).toBe(5_000);
  });

  it('떠난 자리는 잊는다', () => {
    // 남겨 두면 같은 사람이 다시 들어왔을 때 이름표가 안 뜬다.
    const before = trackMoves(new Map(), [{ key: 'a', signature: 'A1' }], 1_000);

    const after = trackMoves(before, [], 5_000);

    expect(after.has('a')).toBe(false);
  });
});

describe('visibleLabels', () => {
  const moves = new Map([
    ['a', { since: 1_000, signature: 'A1' }],
    ['b', { since: 4_000, signature: 'B7' }],
  ]);

  it('아직 시간이 안 지난 이름표만 보인다', () => {
    expect(visibleLabels(moves, 4_500, 3_000)).toEqual(new Set(['b']));
  });

  it('전부 지나면 아무것도 안 보인다', () => {
    expect(visibleLabels(moves, 10_000, 3_000)).toEqual(new Set());
  });

  it('막 움직인 직후에는 전부 보인다', () => {
    expect(visibleLabels(moves, 4_000, 3_000)).toEqual(new Set(['a', 'b']));
  });

  it('경계에서는 아직 보인다', () => {
    // 딱 떨어지는 순간에 사라지면 깜빡이는 것처럼 보인다.
    expect(visibleLabels(new Map([['a', { since: 0, signature: 'A1' }]]), 3_000, 3_000)).toEqual(new Set(['a']));
  });
});
