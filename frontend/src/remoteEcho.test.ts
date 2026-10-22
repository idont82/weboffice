import { describe, expect, it } from 'vitest';

import { isEcho, sameList } from './remoteEcho';

/**
 * 알림이 나에게 되돌아오는 것을 가려낸다.
 *
 * <p>WebSocket 브로드캐스트라 내가 보낸 변경도 내가 받는다. 그래서 셀 하나를
 * 고칠 때마다 내 화면에 "홍길동 책임님이 셀을 고쳤습니다" 가 떴다(2026-08-07 실측).
 *
 * <p>사용자 아이디로 거르면 안 된다. 같은 사람이 창을 둘 열면 다른 창의 변경이
 * 안 오게 된다 - 실제로 그렇게 두 사람을 흉내 내며 시험한다. 대신 <b>내가 이미
 * 아는 상태와 같은가</b>로 가른다. 같으면 내 것이 돌아온 것이고, 다르면 남이 바꾼
 * 것이다. 같은 계정 다른 창은 상태가 다르므로 알림이 제대로 뜬다.
 */
describe('isEcho', () => {
  it('이미 아는 값과 같으면 메아리다', () => {
    const known = new Map([['1:2', '{"v":"가"}']]);

    expect(isEcho(known, [{ key: '1:2', value: '{"v":"가"}' }])).toBe(true);
  });

  it('하나라도 다르면 메아리가 아니다', () => {
    const known = new Map([['1:2', '{"v":"가"}'], ['1:3', '{"v":"나"}']]);

    expect(isEcho(known, [
      { key: '1:2', value: '{"v":"가"}' },
      { key: '1:3', value: '{"v":"다"}' },
    ])).toBe(false);
  });

  it('모르는 자리가 오면 메아리가 아니다', () => {
    expect(isEcho(new Map(), [{ key: '9:9', value: '{"v":"새"}' }])).toBe(false);
  });

  it('빈 알림은 메아리로 친다', () => {
    // 빈 것을 남의 변경이라 하면 아무 일도 없었는데 알림이 뜬다.
    expect(isEcho(new Map(), [])).toBe(true);
  });
});

describe('sameList', () => {
  const key = (r: { a: number; b: number }) => `${r.a}:${r.b}`;

  it('순서가 달라도 같은 것은 같다', () => {
    // 서버는 정렬해 주고 화면은 만든 순서대로 들고 있다. 순서로 가르면
    // 바뀐 것이 없는데도 매번 다르다고 나온다.
    const a = [{ a: 1, b: 2 }, { a: 3, b: 4 }];
    const b = [{ a: 3, b: 4 }, { a: 1, b: 2 }];

    expect(sameList(a, b, key)).toBe(true);
  });

  it('개수가 다르면 다르다', () => {
    expect(sameList([{ a: 1, b: 2 }], [{ a: 1, b: 2 }, { a: 3, b: 4 }], key)).toBe(false);
  });

  it('내용이 다르면 다르다', () => {
    expect(sameList([{ a: 1, b: 2 }], [{ a: 1, b: 9 }], key)).toBe(false);
  });

  it('둘 다 비면 같다', () => {
    expect(sameList([], [], key)).toBe(true);
  });

  it('같은 값이 두 번 들어 있어도 개수로 가른다', () => {
    // 집합으로만 비교하면 [x, x] 와 [x] 가 같아진다.
    const a = [{ a: 1, b: 2 }, { a: 1, b: 2 }];
    const b = [{ a: 1, b: 2 }];

    expect(sameList(a, b, key)).toBe(false);
  });
});
