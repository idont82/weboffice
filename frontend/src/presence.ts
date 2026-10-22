/**
 * 누가 어디를 보고 있는지.
 *
 * 2026-08-06 에 잠금에서 표시로 바뀌었다. 막지 않고 알린다 - 구글 시트가 그렇다.
 * 여기는 판단만 하고, 화면에 그리는 것은 main.ts 가 한다.
 *
 * 서버가 주는 자료에 사번이 없다. 사람을 가리키는 것은 USER_ID 다.
 */
import { toA1 } from './lockUi';

export interface Presence {
  lockSeq: number;
  sheetSeq: number;
  /** 인명부에서 못 찾으면 null 이다. 그래도 자리는 보여야 한다. */
  userId: string | null;
  displayName: string | null;
  rank: string | null;
  /** 자리를 잡은 브라우저 탭. 색을 여기서 뽑는다. 옛 자리는 null 일 수 있다. */
  sessionKey: string | null;
  fromRowNo: number;
  fromColumnNo: number;
  toRowNo: number;
  toColumnNo: number;
}

/**
 * 이름표 문구.
 *
 * <p>이름 → 아이디 → "다른 사용자" 순으로 물러선다. 빈 이름표가 뜨면 거기 누가
 * 있다는 것만 알고 누구인지는 모르는 상태가 된다.
 */
const TAB_KEY_NAME = 'weboffice.tabKey';

/**
 * 이 탭의 식별자.
 *
 * <p>자리 색을 사람이 아니라 탭 단위로 나누기 위한 것이다. sessionStorage 는 탭마다
 * 따로라 같은 브라우저의 다른 창과 값이 갈린다 - localStorage 는 공유돼 안 된다.
 * 새로고침해도 유지되므로 색이 튀지 않는다.
 *
 * <p>{@code crypto.randomUUID} 를 쓰지 않는다. 그것은 보안 컨텍스트(https 또는
 * localhost)에만 있어서, 사내 http 주소로 열면 "crypto.randomUUID is not a
 * function" 으로 죽는다(2026-08-06 실측). getRandomValues 는 http 에서도 있고,
 * 그것마저 없으면 Math.random 으로 물러선다 - 이 값은 색을 가르는 데만 쓰므로
 * 예측 가능해도 잃을 것이 없다.
 */
export function tabKey(storage: Storage): string {
  const saved = storage.getItem(TAB_KEY_NAME);
  if (saved !== null) {
    return saved;
  }
  const fresh = randomKey();
  storage.setItem(TAB_KEY_NAME, fresh);
  return fresh;
}

function randomKey(): string {
  const bytes = new Uint8Array(16);
  if (typeof globalThis.crypto?.getRandomValues === 'function') {
    globalThis.crypto.getRandomValues(bytes);
  } else {
    for (let i = 0; i < bytes.length; i += 1) {
      bytes[i] = Math.floor(Math.random() * 256);
    }
  }
  return [...bytes].map((b) => b.toString(16).padStart(2, '0')).join('');
}

/** 색을 뽑을 키. 탭이 먼저고, 없으면 사람으로 물러선다. */
export function colorKeyOf(presence: Presence): string | null {
  return presence.sessionKey ?? presence.userId;
}

export function describePresence(presence: Presence): string {
  // 이름에 직급을 붙인다. 동명이인이 흔해 이름만으로는 갈리지 않는다 -
  // 실제로 인명부에 같은 이름이 둘 있을 수 있다(2026-08-06 확인).
  const named = [presence.displayName?.trim(), presence.rank?.trim()]
    .filter(Boolean).join(' ');
  const who = named || presence.userId?.trim() || '다른 사용자';
  const from = toA1(presence.fromRowNo, presence.fromColumnNo);
  if (presence.fromRowNo === presence.toRowNo
      && presence.fromColumnNo === presence.toColumnNo) {
    return `${who} · ${from}`;
  }
  return `${who} · ${from}:${toA1(presence.toRowNo, presence.toColumnNo)}`;
}

/**
 * 자리마다 고정된 색.
 *
 * <p>탭(세션 키)에서 뽑는다. 사람 단위로 뽑으면 같은 사람이 두 창을 띄웠을 때
 * 둘이 같은 색이라 어느 쪽이 어느 쪽인지 알 수 없다. 세션 키가 없는 옛 자리는
 * 아이디로 물러선다.
 *
 * <p>접속 순서로 나눠 주지 않는 이유: 새로고침할 때마다 색이 바뀌어 누가 누구인지
 * 못 따라간다. 키에서 만들어 내면 같은 탭은 늘 같은 색이다.
 */
const COLORS = [
  '#d93025', '#1a73e8', '#188038', '#e37400',
  '#8430ce', '#0b8043', '#c5221f', '#1967d2',
];

export function colorOf(key: string | null): string {
  const source = key ?? '';
  let hash = 0;
  for (let i = 0; i < source.length; i += 1) {
    hash = (hash * 31 + source.charCodeAt(i)) % 100_000;
  }
  return COLORS[hash % COLORS.length];
}

/**
 * 지금 화면에 그려야 할 자리들.
 *
 * <p>키는 {@code lockSeq} 하나다. 아이디로 묶어 대체하지 않는다 - 그러면 같은
 * 사람이 두 창을 띄웠을 때 한쪽이 안 보인다. 그 둘은 실제로 다른 자리다.
 *
 * <p>대신 자리가 사라지는 길이 둘 있다. 상대가 옮기며 보내는 해제 알림, 그리고
 * 주기적인 전체 갱신이다. 알림 하나를 놓치면 유령이 남는데 갱신이 그것을 쓸어낸다.
 */
export function createPresenceStore(initialMine: number | null = null) {
  const byLockSeq = new Map<number, Presence>();
  // 내 자리는 아이디가 아니라 자리 번호로 가린다. 아이디로 가리면 같은 사람이
  // 두 탭을 열었을 때 다른 탭이 안 보인다 - 그 둘은 실제로 다른 자리다.
  let myLockSeq = initialMine;
  // 자리 번호만으로는 부족하다. 새로고침하면 이전 페이지의 자리가 TTL 동안 살아
  // 있는데 새 페이지는 그 번호를 모른다 - 내 자리가 남처럼 그려진다(2026-08-06 지적).
  // 탭 키는 새로고침해도 유지되므로 그것으로 함께 거른다.
  let myTabKey: string | null = null;

  return {
    /** 내가 방금 잡은 자리. 알림이 되돌아와도 내 선택 위에 덧칠하지 않는다. */
    setMine(lockSeq: number | null) {
      myLockSeq = lockSeq;
      if (lockSeq !== null) {
        byLockSeq.delete(lockSeq);
      }
    },

    /** 이 탭의 키. 새로고침 전에 남긴 내 자리까지 가린다. */
    setMyTabKey(key: string) {
      myTabKey = key;
      for (const [lockSeq, seat] of byLockSeq) {
        if (seat.sessionKey === key) {
          byLockSeq.delete(lockSeq);
        }
      }
    },

    set(presence: Presence) {
      // 내 자리에 내 색을 덧칠하면 선택 테두리가 안 보인다.
      if (presence.lockSeq === myLockSeq) {
        return;
      }
      if (myTabKey !== null && presence.sessionKey === myTabKey) {
        return;
      }
      byLockSeq.set(presence.lockSeq, presence);
    },

    clear(lockSeq: number) {
      byLockSeq.delete(lockSeq);
    },

    replaceAll(list: Presence[]) {
      byLockSeq.clear();
      for (const presence of list) {
        this.set(presence);
      }
    },

    all(): Presence[] {
      return [...byLockSeq.values()];
    },
  };
}

export type PresenceStore = ReturnType<typeof createPresenceStore>;
