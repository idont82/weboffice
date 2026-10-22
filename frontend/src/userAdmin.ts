/**
 * 사용자 관리 화면의 셈.
 *
 * <p>그리는 일과 나눠 둔다 - 여기는 "무엇을 보여 줄지" 만 정하고 DOM 을 모른다.
 * viewAdmin 과 같은 방식이다.
 */

export interface AdminUser {
  userKey: string;
  email: string;
  displayName: string;
  role: string;
  canSignIn: boolean;
  disabled: boolean;
}

export interface Invite {
  email: string;
  inviteUrl: string;
  expiresAt: string;
}

/** 한 줄에 보여 줄 것. */
export interface UserLine {
  userKey: string;
  email: string;
  displayName: string;
  role: string;
  /** 지금 어떤 상태인가. 셋 중 하나다. */
  state: '쓰는 중' | '초대 대기' | '막힘';
  /** 막기 단추에 적을 말. */
  toggleLabel: '막기' | '풀기';
  /** 초대를 다시 낼 수 있는가. 막힌 사람에게는 뜻이 없다. */
  canInvite: boolean;
}

export function lineOf(user: AdminUser): UserLine {
  const state = user.disabled ? '막힘' : (user.canSignIn ? '쓰는 중' : '초대 대기');
  return {
    userKey: user.userKey,
    email: user.email,
    displayName: user.displayName,
    role: user.role === 'ADMIN' ? '관리자' : '사용자',
    state,
    toggleLabel: user.disabled ? '풀기' : '막기',
    canInvite: !user.disabled,
  };
}

/**
 * 목록 순서. 손이 가야 하는 사람을 앞에 둔다.
 *
 * <p>초대 대기가 맨 앞이다 - 링크를 아직 전하지 않았을 수 있고, 그것이 관리자가
 * 이 화면에 온 가장 흔한 까닭이다. 막힌 사람은 맨 뒤다.
 */
export function sortedLines(users: AdminUser[]): UserLine[] {
  const rank = (one: UserLine) => (one.state === '초대 대기' ? 0 : (one.state === '쓰는 중' ? 1 : 2));
  return users.map(lineOf).sort((a, b) => {
    const gap = rank(a) - rank(b);
    return gap !== 0 ? gap : a.displayName.localeCompare(b.displayName, 'ko');
  });
}

/**
 * 보낼 수 있는 등록인가.
 *
 * <p>서버도 같은 것을 본다. 화면에서 먼저 막는 것은 왕복을 줄이기 위한 것이고,
 * 서버 검사를 대신하지 않는다.
 */
export function canRegister(email: string): boolean {
  // 닻(^ $)이 있어야 한다. 없으면 "a b@c.com" 이 가운데만 맞아 통과하는데
  // 서버는 matches() 라 통째로 본다 - 화면이 서버보다 느슨하면 보냈다가
  // 거절당한다.
  return /^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email.trim());
}

/** 유효기간을 사람이 읽는 꼴로. */
export function expiryText(expiresAt: string): string {
  const when = new Date(expiresAt);
  if (Number.isNaN(when.getTime())) {
    return '';
  }
  return when.toLocaleString('ko-KR');
}
