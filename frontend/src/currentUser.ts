/**
 * 화면 우측 상단에 띄우는 사용자 표시.
 *
 * <p><b>사번이 없다.</b> 서버가 보내지 않는다 - 사번은 개인을 특정하는 사내
 * 식별자라 화면에 띄우면 어깨너머로 읽히고 스크린샷에 남는다. 저장·잠금·권한
 * 판정에는 계속 쓰지만 브라우저로 내려보내지 않는다.
 *
 * <p>성명·직급·부서는 SSO 가 아니라 인명부에서 온다. SSO 클레임 중
 * 실 응답으로 확인된 것은 사번뿐이다.
 *
 * <p>서버의 CurrentUserDto 와 필드명이 같아야 한다.
 */
export interface CurrentUser {
  userId: string | null;
  displayName: string | null;
  rank: string | null;
  deptName: string | null;
}

function trimmed(value: string | null | undefined): string {
  return value?.trim() ?? '';
}

/**
 * 화면에 띄울 문구. 있는 것만 조합한다.
 *
 * <p>인명부가 없거나 조회 전이면 성명이 비는데, 그때는 아이디로 내려간다.
 * 사번으로 대신하지 않는다 - 그게 바로 감추려는 값이다.
 *
 * @example "홍길동 과장 · 전산팀"
 */
export function describeUser(user: CurrentUser | null): string {
  if (!user) return '';

  const who = [trimmed(user.displayName), trimmed(user.rank)].filter(Boolean).join(' ');
  const dept = trimmed(user.deptName);

  if (who && dept) return `${who} · ${dept}`;
  return who || dept || trimmed(user.userId);
}

/**
 * 윗줄에 늘 보이는 글. 성명과 직급만이다.
 *
 * <p>부서까지 늘 띄우면 줄이 길어 제목을 밀어낸다. 부서는 손을 얹었을 때만
 * 보인다(2026-08-20 지적).
 *
 * @example "홍길동 과장"
 */
export function nameOfUser(user: CurrentUser | null): string {
  if (!user) return '';
  const who = [trimmed(user.displayName), trimmed(user.rank)].filter(Boolean).join(' ');
  return who || trimmed(user.userId);
}

/** 손을 얹었을 때 보일 부서. 없으면 빈 글이다. */
export function deptOfUser(user: CurrentUser | null): string {
  return user ? trimmed(user.deptName) : '';
}
