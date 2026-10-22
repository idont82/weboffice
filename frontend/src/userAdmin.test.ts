import { describe, it, expect } from 'vitest';

import {
  canRegister, expiryText, lineOf, sortedLines, type AdminUser,
} from './userAdmin';

function 사용자(given: Partial<AdminUser> = {}): AdminUser {
  return {
    userKey: 'k1',
    email: 'hong@example.com',
    displayName: '홍길동',
    role: 'USER',
    canSignIn: true,
    disabled: false,
    ...given,
  };
}

describe('lineOf', () => {
  it('비밀번호를 정한 사람은 쓰는 중이다', () => {
    expect(lineOf(사용자()).state).toBe('쓰는 중');
  });

  it('아직 비밀번호가 없으면 초대 대기다', () => {
    // 관리자가 링크를 전했는지 알 수 없으므로 이 상태가 보여야 한다.
    expect(lineOf(사용자({ canSignIn: false })).state).toBe('초대 대기');
  });

  it('막힌 사람은 비밀번호가 있어도 막힘이다', () => {
    expect(lineOf(사용자({ disabled: true })).state).toBe('막힘');
  });

  it('막힌 사람에게는 초대를 다시 내지 않는다', () => {
    expect(lineOf(사용자({ disabled: true })).canInvite).toBe(false);
  });

  it('막기 단추의 말이 상태에 따라 뒤집힌다', () => {
    expect(lineOf(사용자()).toggleLabel).toBe('막기');
    expect(lineOf(사용자({ disabled: true })).toggleLabel).toBe('풀기');
  });

  it('역할을 우리말로 적는다', () => {
    expect(lineOf(사용자({ role: 'ADMIN' })).role).toBe('관리자');
    expect(lineOf(사용자({ role: 'USER' })).role).toBe('사용자');
  });
});

describe('sortedLines', () => {
  it('손이 가야 하는 사람을 앞에 둔다', () => {
    // 초대 대기 → 쓰는 중 → 막힘. 관리자가 이 화면에 오는 가장 흔한 까닭이
    // "링크를 아직 못 전했다" 이다.
    const got = sortedLines([
      사용자({ userKey: 'a', displayName: '막힌이', disabled: true }),
      사용자({ userKey: 'b', displayName: '쓰는이' }),
      사용자({ userKey: 'c', displayName: '대기이', canSignIn: false }),
    ]);

    expect(got.map((one) => one.displayName)).toEqual(['대기이', '쓰는이', '막힌이']);
  });

  it('같은 상태면 이름순이다', () => {
    const got = sortedLines([
      사용자({ userKey: 'a', displayName: '홍길동' }),
      사용자({ userKey: 'b', displayName: '강감찬' }),
    ]);

    expect(got.map((one) => one.displayName)).toEqual(['강감찬', '홍길동']);
  });

  it('빈 목록도 터지지 않는다', () => {
    expect(sortedLines([])).toEqual([]);
  });
});

describe('canRegister', () => {
  it('이메일 모양이면 보낸다', () => {
    expect(canRegister('hong@example.com')).toBe(true);
    expect(canRegister('  hong@example.com  ')).toBe(true);
  });

  it('이메일이 아니면 막는다', () => {
    for (const bad of ['', '  ', 'hong', 'hong@', '@example.com', 'a@b', 'a b@c.com']) {
      expect(canRegister(bad), bad).toBe(false);
    }
  });
});

describe('expiryText', () => {
  it('읽을 수 있는 꼴로 적는다', () => {
    expect(expiryText('2026-10-25T09:00:00')).not.toBe('');
  });

  it('못 읽는 값이면 빈 글이다', () => {
    // 비어 보이는 것이 "Invalid Date" 가 뜨는 것보다 낫다.
    expect(expiryText('그냥글자')).toBe('');
  });
});
