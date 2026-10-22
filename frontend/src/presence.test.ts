import { describe, expect, it } from 'vitest';

import { colorKeyOf, colorOf, createPresenceStore, describePresence, tabKey } from './presence';

function seat(fields: Partial<Parameters<typeof describePresence>[0]> = {}) {
  return {
    lockSeq: 1, sheetSeq: 9, userId: 'hong', displayName: '홍길동', rank: '과장',
    sessionKey: 'tab-1',
    fromRowNo: 2, fromColumnNo: 1, toRowNo: 2, toColumnNo: 1, ...fields,
  };
}

describe('describePresence', () => {
  it('이름에 직급을 붙여 보여준다', () => {
    // 동명이인이 흔하다. 이름만으로는 누구인지 갈리지 않는다.
    expect(describePresence(seat())).toBe('홍길동 과장 · B3');
  });

  it('범위면 시작과 끝을 보여준다', () => {
    expect(describePresence(seat({ toRowNo: 5, toColumnNo: 3 })))
      .toBe('홍길동 과장 · B3:D6');
  });

  it('직급을 모르면 이름만 쓴다', () => {
    expect(describePresence(seat({ rank: null }))).toBe('홍길동 · B3');
  });

  it('이름을 모르면 아이디로 부른다', () => {
    // 인명부를 못 읽은 자리다. 빈 이름표가 뜨면 누군지 알 수 없다.
    expect(describePresence(seat({ displayName: null, rank: null }))).toBe('hong · B3');
  });

  it('아이디도 모르면 그렇게 적는다', () => {
    expect(describePresence(seat({ displayName: null, rank: null, userId: null })))
      .toBe('다른 사용자 · B3');
  });
});

describe('colorOf', () => {
  it('같은 키는 늘 같은 색이다', () => {
    // 움직일 때마다 색이 바뀌면 누가 누구인지 못 따라간다.
    expect(colorOf('tab-1')).toBe(colorOf('tab-1'));
  });

  it('다른 키는 되도록 다른 색이다', () => {
    expect(colorOf('tab-1')).not.toBe(colorOf('tab-2'));
  });

  it('키가 없어도 색은 나온다', () => {
    expect(colorOf(null)).toMatch(/^#[0-9a-f]{6}$/i);
  });
});

describe('colorKeyOf', () => {
  it('같은 사람의 두 탭은 색이 갈린다', () => {
    // 사람 단위로 색을 뽑으면 두 창이 같은 색이라 어느 쪽인지 알 수 없다.
    const one = colorKeyOf(seat({ sessionKey: 'tab-1' }));
    const two = colorKeyOf(seat({ sessionKey: 'tab-2' }));

    expect(colorOf(one)).not.toBe(colorOf(two));
  });

  it('세션 키가 없으면 아이디로 물러선다', () => {
    // 만료 전 옛 자리다. 색이 없는 것보다 사람 단위라도 나은다.
    expect(colorKeyOf(seat({ sessionKey: null }))).toBe('hong');
  });
});

describe('createPresenceStore', () => {
  it('자리를 넣고 뺀다', () => {
    const store = createPresenceStore();

    store.set(seat({ lockSeq: 7 }));
    expect(store.all()).toHaveLength(1);

    store.clear(7);
    expect(store.all()).toHaveLength(0);
  });

  it('같은 아이디라도 자리 번호가 다르면 둘 다 담는다', () => {
    // 같은 사람이 두 창을 띄운 경우다. 아이디로 묶어 대체하면 한쪽이 사라진다.
    const store = createPresenceStore();

    store.set(seat({ lockSeq: 1, userId: 'hong', fromRowNo: 0 }));
    store.set(seat({ lockSeq: 2, userId: 'hong', fromRowNo: 9 }));

    expect(store.all()).toHaveLength(2);
  });

  it('같은 자리 번호는 덮어쓴다', () => {
    const store = createPresenceStore();

    store.set(seat({ lockSeq: 1, fromRowNo: 0 }));
    store.set(seat({ lockSeq: 1, fromRowNo: 9 }));

    expect(store.all()).toHaveLength(1);
    expect(store.all()[0].fromRowNo).toBe(9);
  });

  it('내 자리는 담지 않는다', () => {
    // 내가 고른 범위에 내 색이 덧칠되면 선택 테두리가 안 보인다.
    const store = createPresenceStore(4);

    store.set(seat({ lockSeq: 4, userId: 'me' }));

    expect(store.all()).toHaveLength(0);
  });

  it('새로고침 전에 남긴 내 자리도 가린다', () => {
    // 이전 페이지의 자리는 TTL 동안 살아 있는데 번호가 다르다. 탭 키는 새로고침해도
    // 유지되므로 그것으로 걸러야 내 자리가 남처럼 그려지지 않는다.
    const store = createPresenceStore();
    store.setMyTabKey('tab-1');

    store.set(seat({ lockSeq: 99, sessionKey: 'tab-1' }));

    expect(store.all()).toHaveLength(0);
  });

  it('이미 담긴 내 자리는 탭 키를 알려주면 사라진다', () => {
    // 아이디를 늦게 알듯 탭 키도 첫 목록보다 늦게 정해질 수 있다.
    const store = createPresenceStore();
    store.set(seat({ lockSeq: 99, sessionKey: 'tab-1' }));

    store.setMyTabKey('tab-1');

    expect(store.all()).toHaveLength(0);
  });

  it('다른 탭 키는 남는다', () => {
    const store = createPresenceStore();
    store.setMyTabKey('tab-1');

    store.set(seat({ lockSeq: 99, sessionKey: 'tab-2' }));

    expect(store.all()).toHaveLength(1);
  });

  it('같은 사람의 다른 탭은 보여준다', () => {
    // 아이디로 가리면 두 창을 띄운 자신의 다른 자리가 안 보인다. 그 둘은
    // 실제로 다른 자리다 - 구글 시트도 따로 보여 준다.
    const store = createPresenceStore(4);

    store.set(seat({ lockSeq: 9, userId: 'me' }));

    expect(store.all()).toHaveLength(1);
  });

  it('아이디를 모르는 자리도 담는다', () => {
    // 누군지 몰라도 거기 사람이 있다는 것은 알려야 한다.
    const store = createPresenceStore();

    store.set(seat({ lockSeq: 3, userId: null }));

    expect(store.all()).toHaveLength(1);
  });

  it('통째로 갈아 끼운다', () => {
    const store = createPresenceStore();
    store.set(seat({ lockSeq: 1 }));

    store.replaceAll([seat({ lockSeq: 5, userId: 'kim' })]);

    expect(store.all().map((p) => p.lockSeq)).toEqual([5]);
  });
});

describe('tabKey', () => {
  function fakeStorage(): Storage {
    const map = new Map<string, string>();
    return {
      getItem: (k: string) => map.get(k) ?? null,
      setItem: (k: string, v: string) => void map.set(k, v),
      removeItem: (k: string) => void map.delete(k),
      clear: () => map.clear(),
      key: () => null,
      get length() { return map.size; },
    } as Storage;
  }

  it('같은 탭에서는 같은 값이다', () => {
    // 새로고침할 때마다 바뀌면 색이 튄다.
    const storage = fakeStorage();

    expect(tabKey(storage)).toBe(tabKey(storage));
  });

  it('탭이 다르면 값도 다르다', () => {
    expect(tabKey(fakeStorage())).not.toBe(tabKey(fakeStorage()));
  });

  it('crypto 가 없어도 만들어진다', () => {
    // crypto.randomUUID 는 https 나 localhost 에서만 있다. 사내 http 주소로 열면
    // 없어서 "not a function" 으로 죽었다(2026-08-06 실측).
    const saved = globalThis.crypto;
    Reflect.deleteProperty(globalThis, 'crypto');
    try {
      expect(tabKey(fakeStorage())).toMatch(/^[0-9a-f]{32}$/);
    } finally {
      Object.defineProperty(globalThis, 'crypto', { value: saved, configurable: true });
    }
  });
});
