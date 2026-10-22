import { describe, expect, it } from 'vitest';

import { CLIENT_HEADER, clientId, fromOtherClient } from './clientId';

describe('clientId', () => {
  it('창마다 같은 값을 준다', () => {
    expect(clientId()).toBe(clientId());
    expect(clientId().length).toBeGreaterThan(6);
  });
});

describe('fromOtherClient', () => {
  it('내 창이 보낸 알림은 남의 것이 아니다', () => {
    expect(fromOtherClient({ actorClient: clientId() })).toBe(false);
  });

  it('다른 창이 보낸 것은 남의 것이다', () => {
    expect(fromOtherClient({ actorClient: 'tab-other' })).toBe(true);
  });

  it('창 표시가 없으면 남의 것으로 본다', () => {
    // 옛 서버는 이 값을 안 싣는다. 모르면 받아 두는 쪽이 안전하다.
    expect(fromOtherClient({})).toBe(true);
    expect(fromOtherClient({ actorClient: null })).toBe(true);
  });
});

describe('CLIENT_HEADER', () => {
  it('서버가 읽는 이름과 같다', () => {
    expect(CLIENT_HEADER).toBe('X-WO-Client');
  });
});
