/**
 * 내가 보낸 변경이 나에게 되돌아온 것을 가려낸다.
 *
 * <p>WebSocket 브로드캐스트라 보낸 사람도 자기 알림을 받는다. 그래서 셀 하나를
 * 고칠 때마다 자기 화면에 "홍길동 책임님이 셀을 고쳤습니다" 가 떴다(2026-08-07 실측).
 *
 * <p><b>사용자 아이디로 거르지 않는다.</b> 같은 사람이 창을 둘 열면 다른 창의 변경이
 * 안 오게 된다 - 실제로 그렇게 두 사람을 흉내 내며 시험한다. 대신 "내가 이미 아는
 * 상태와 같은가" 로 가른다. 같으면 내 것이 돌아온 것이고, 다르면 남이 바꾼 것이다.
 * 같은 계정 다른 창은 상태가 다르므로 알림이 제대로 뜬다.
 */

/** 셀 알림 하나. {@code value} 는 비교용 직렬화 문자열이다. */
export interface KnownEntry {
  key: string;
  value: string;
}

/** 받은 것이 전부 이미 아는 값과 같으면 메아리다. 빈 것도 메아리로 친다. */
export function isEcho(known: Map<string, string>, incoming: KnownEntry[]): boolean {
  return incoming.every((entry) => known.get(entry.key) === entry.value);
}

/**
 * 두 목록이 같은 것을 담고 있는가. 순서는 보지 않는다.
 *
 * <p>서버는 정렬해 주고 화면은 만든 순서대로 들고 있다. 순서로 가르면 바뀐 것이
 * 없는데도 매번 다르다고 나온다.
 *
 * <p>개수를 함께 본다. 집합으로만 비교하면 같은 값이 두 번 든 목록과 한 번 든
 * 목록이 같아진다.
 */
export function sameList<T>(a: T[], b: T[], keyOf: (item: T) => string): boolean {
  if (a.length !== b.length) {
    return false;
  }
  const counts = new Map<string, number>();
  for (const item of a) {
    const key = keyOf(item);
    counts.set(key, (counts.get(key) ?? 0) + 1);
  }
  for (const item of b) {
    const key = keyOf(item);
    const left = counts.get(key);
    if (!left) {
      return false;
    }
    counts.set(key, left - 1);
  }
  return true;
}
