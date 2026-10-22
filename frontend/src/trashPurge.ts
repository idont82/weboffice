/**
 * 휴지통 비우기 갈래.
 *
 * <p>비우기는 되돌릴 수 없다. 그래서 "무엇을 남길지" 를 먼저 고르게 하고, 누르기
 * 전에 <b>몇 개가 사라지는지</b> 보여 준다 - 되돌릴 수 없는 일은 누르기 전에
 * 알려야 한다.
 */

/** 비우기 한 갈래. keepDays 는 "최근 이 날 수 안에 버린 것은 남긴다" 는 뜻이다. */
export interface PurgeChoice {
  keepDays: number;
  label: string;
}

/**
 * 고를 수 있는 갈래.
 *
 * <p>날 수를 숫자로 적는다. "사흘·이레" 는 세는 말이라 한눈에 안 들어온다는
 * 지적을 받았다(2026-09-03).
 *
 * <p>"n일 지난 것" 은 <b>지워지는 쪽</b>을 가리킨다 - 남는 쪽을 적으면 무엇이
 * 사라지는지 한 번 더 뒤집어 생각해야 한다.
 */
export const PURGE_CHOICES: PurgeChoice[] = [
  { keepDays: 0, label: '전부 비우기' },
  { keepDays: 1, label: '1일 지난 것 비우기' },
  { keepDays: 3, label: '3일 지난 것 비우기' },
  { keepDays: 7, label: '7일 지난 것 비우기' },
];

/** 휴지통 한 줄에서 우리가 보는 것. */
interface Thrown {
  deletedAt: string | null;
}

/**
 * 이 갈래로 비우면 몇 개가 사라지는가.
 *
 * <p>버린 때를 모르는 줄은 세지 않는다. 옛 문서에는 시각이 없는데, 세면 "몇 개가
 * 지워진다" 가 거짓말이 된다.
 */
export function countToPurge(rows: Thrown[], keepDays: number, now = new Date()): number {
  const border = new Date(now.getTime() - keepDays * 24 * 60 * 60 * 1000);
  return rows.filter((row) => {
    if (!row.deletedAt) {
      return false;
    }
    const when = new Date(row.deletedAt);
    return !Number.isNaN(when.getTime()) && when.getTime() < border.getTime();
  }).length;
}
