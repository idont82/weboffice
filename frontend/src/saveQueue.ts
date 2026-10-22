/**
 * 저장 대기줄.
 *
 * <p>담을 때의 시트를 함께 들고 다닌다. 보낼 때의 시트로 보내면, 시트를 더하거나
 * 바꾸는 사이에 쌓인 것이 엉뚱한 시트로 간다(2026-08-31 실측: 새 시트를 더했더니
 * 그 값이 첫 시트에 저장돼 남의 표를 덮었다).
 */

export interface Waiting<T> {
  /** 담을 때의 시트. 모르면 null - 그런 것은 보내지 않는다. */
  sheetSeq: number | null;
  cell: T;
}

/** 시트별로 나눈다. 시트를 모르는 것은 뺀다. */
export function bySheet<T>(waiting: Waiting<T>[]): Map<number, T[]> {
  const groups = new Map<number, T[]>();
  for (const one of waiting) {
    if (one.sheetSeq == null) {
      continue;
    }
    const kept = groups.get(one.sheetSeq);
    if (kept == null) {
      groups.set(one.sheetSeq, [one.cell]);
    } else {
      kept.push(one.cell);
    }
  }
  return groups;
}
