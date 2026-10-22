import { describe, expect, it } from 'vitest';

import { describeMember, needsSearch } from './shareDialog';

describe('describeMember', () => {
  it('이름 직급 부서를 함께 보여준다', () => {
    expect(describeMember({ userId: 'hong', displayName: '홍길동', rank: '과장',
      deptName: '전산팀', role: 'EDITOR' })).toBe('홍길동 과장 · 전산팀');
  });

  it('인명부에서 못 찾은 사람도 줄이 사라지지 않는다', () => {
    // 퇴사했거나 인명부가 없는 환경이다. 빈 줄이 나오면 제거 버튼도 같이 사라진다.
    expect(describeMember({ userId: null, displayName: null, rank: null,
      deptName: null, role: 'VIEWER' })).toBe('(알 수 없는 사용자)');
  });

  it('직급이나 부서 한쪽만 있어도 읽힌다', () => {
    expect(describeMember({ userId: 'a', displayName: '김철수', rank: null,
      deptName: null, role: 'EDITOR' })).toBe('김철수');
    expect(describeMember({ userId: 'b', displayName: null, rank: null,
      deptName: '영업팀', role: 'EDITOR' })).toBe('영업팀');
  });
});

describe('needsSearch', () => {
  it('두 글자부터 찾는다', () => {
    // 서버도 막지만, 한 글자마다 요청을 보내면 인명부를 훑는 꼴이 된다.
    expect(needsSearch('')).toBe(false);
    expect(needsSearch('홍')).toBe(false);
    expect(needsSearch('홍길')).toBe(true);
    expect(needsSearch('  홍길  ')).toBe(true);
  });
});
