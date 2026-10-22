import { describe, expect, it } from 'vitest';

import { deptOfUser, describeUser, nameOfUser, type CurrentUser } from './currentUser';

function user(fields: Partial<CurrentUser> = {}): CurrentUser {
  return { userId: null, displayName: null, rank: null, deptName: null, ...fields };
}

describe('describeUser', () => {
  it('성명 직급 부서를 함께 보여준다', () => {
    expect(describeUser(user({ displayName: '홍길동', rank: '과장', deptName: '전산팀' })))
        .toBe('홍길동 과장 · 전산팀');
  });

  it('직급이 없으면 성명과 부서만 보여준다', () => {
    // 활성 사용자의 1%는 EMP_RANK 가 비어 있다(2026-08-05 실측).
    expect(describeUser(user({ displayName: '홍길동', deptName: '전산팀' })))
        .toBe('홍길동 · 전산팀');
  });

  it('부서가 없으면 성명만 보여준다', () => {
    expect(describeUser(user({ displayName: '홍길동', rank: '과장' }))).toBe('홍길동 과장');
  });

  it('성명이 없으면 아이디로 대신한다', () => {
    // 인명부가 없을 때다. 사번으로 대신하지 않는다 - 그게 감추려는 값이다.
    expect(describeUser(user({ userId: 'hong' }))).toBe('hong');
  });

  it('아무것도 없으면 빈 문자열이다', () => {
    // 화면에 "undefined" 나 "null" 이 뜨는 것보다 아무것도 안 뜨는 편이 낫다.
    expect(describeUser(null)).toBe('');
    expect(describeUser(user())).toBe('');
  });

  it('공백뿐인 값은 없는 것으로 본다', () => {
    expect(describeUser(user({ displayName: '  ', deptName: ' ', userId: 'hong' }))).toBe('hong');
  });
});

describe('nameOfUser', () => {
  it('성명과 직급만 준다', () => {
    // 위쪽 줄에는 이름만 둔다. 부서는 손을 얹었을 때 보인다(2026-08-20 지적).
    expect(nameOfUser(user({ displayName: '홍길동', rank: '과장', deptName: '전산팀' })))
      .toBe('홍길동 과장');
  });

  it('성명이 없으면 아이디로 내려간다', () => {
    expect(nameOfUser(user({ userId: 'hong', deptName: '전산팀' }))).toBe('hong');
  });

  it('아무것도 없으면 빈 글이다', () => {
    expect(nameOfUser(null)).toBe('');
  });
});

describe('deptOfUser', () => {
  it('부서만 준다', () => {
    expect(deptOfUser(user({ displayName: '홍길동', deptName: '전산팀' }))).toBe('전산팀');
  });

  it('부서가 없으면 빈 글이다', () => {
    expect(deptOfUser(user({ displayName: '홍길동' }))).toBe('');
    expect(deptOfUser(null)).toBe('');
  });
});
