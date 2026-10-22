/**
 * 행·열을 지우거나 끼운 뒤 서버를 따라오게 한다.
 *
 * <p>값 저장은 "바뀐 셀만" 보낸다. 그런데 행을 지우면 아래 값이 통째로 위로 밀리고,
 * 그 이동은 우리가 듣던 뮤테이션(SetRangeValues)으로 오지 않는다 - 화면만 줄고
 * 서버는 그대로였다(2026-08-27 실측: 340줄 문서에서 두 줄을 지워도 서버는 안 바뀜).
 *
 * <p>그래서 이동이 일어나면 화면 전체와 "이미 보낸 것" 을 견주어, 달라진 자리는
 * 다시 보내고 사라진 자리는 지우라고 보낸다.
 */

export interface Moved {
  /** 다시 보내야 할 자리("행:열"). */
  changed: string[];
  /** 서버에서 지워야 할 자리. */
  deleted: string[];
}

export function movedCells(sent: Map<string, string>, now: Map<string, string>): Moved {
  const changed: string[] = [];
  for (const [key, value] of now) {
    if (sent.get(key) !== value) {
      changed.push(key);
    }
  }
  const deleted: string[] = [];
  for (const key of sent.keys()) {
    if (!now.has(key)) {
      deleted.push(key);
    }
  }
  return { changed, deleted };
}
