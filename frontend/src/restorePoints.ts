/**
 * 복구 지점.
 *
 * <p>버전 기록과 한 줄기로 보인다. 따로 패널을 만들면 "어제 오후" 를 찾을 때 두
 * 곳을 번갈아 봐야 한다.
 *
 * <p>여기는 섞고 이름 붙이는 일만 한다. 그리는 일은 historyPanel 이 한다.
 */
import type { Version } from './historyPanel';

/** 서버가 준 복구 지점 한 줄. 사번도 본문도 없다. */
export interface RestorePoint {
  snapshotSeq: number;
  takenAt: string;
  /** MANUAL · SESSION · DAILY · BEFORE_RESTORE · BEFORE_AI · BEFORE_IMPORT */
  reason: string;
  label: string | null;
  takenBy: string | null;
  /** 푼 크기(바이트). */
  bodySize: number;
}

/** 섞인 줄 하나. 어느 쪽인지와 언제인지를 들고 있다. */
export type TimelineRow =
  | { kind: 'version'; at: string | null; version: Version }
  | { kind: 'point'; at: string; point: RestorePoint };

/** 이유를 사람 말로. 이름을 붙였으면 그 이름이 먼저다. */
const REASON_WORDS: Record<string, string> = {
  MANUAL: '직접 만든 지점',
  SESSION: '편집을 마친 뒤',
  DAILY: '하루 한 번',
  BEFORE_RESTORE: '되돌리기 직전',
  BEFORE_AI: 'AI 가 고치기 전',
  BEFORE_IMPORT: '가져오기 전',
};

export function pointWord(point: RestorePoint): string {
  const label = point.label?.trim();
  if (label) {
    return label;
  }
  return REASON_WORDS[point.reason] ?? '복구 지점';
}

/**
 * 버전과 복구 지점을 시각 차례로 섞는다.
 *
 * <p>시각이 같으면 복구 지점이 앞이다 - 되돌리기 직전 지점은 그 편집보다 앞선
 * 모습이다.
 *
 * <p>시각을 모르는 줄은 맨 뒤로 보낸다. 옛 문서에는 기록 시각이 없는데, 앞에 오면
 * "지금 상태" 자리를 빼앗는다.
 */
export function timeline(versions: Version[], points: RestorePoint[]): TimelineRow[] {
  const rows: TimelineRow[] = [
    ...points.map((point): TimelineRow => ({ kind: 'point', at: point.takenAt, point })),
    ...versions.map((version): TimelineRow => ({
      kind: 'version', at: version.toAt, version,
    })),
  ];
  return rows.sort((left, right) => {
    if (left.at == null || right.at == null) {
      return left.at == null ? (right.at == null ? 0 : 1) : -1;
    }
    if (left.at === right.at) {
      // 같은 시각이면 지점이 앞.
      return left.kind === right.kind ? 0 : (left.kind === 'point' ? -1 : 1);
    }
    return left.at < right.at ? 1 : -1;
  });
}

/** 크기를 사람 말로. 목록에 그대로 바이트를 적으면 아무도 안 읽는다. */
export function sizeWord(bytes: number): string {
  if (bytes < 1024) {
    return `${bytes}B`;
  }
  if (bytes < 1024 * 1024) {
    return `${Math.round(bytes / 1024)}KB`;
  }
  return `${(bytes / (1024 * 1024)).toFixed(1)}MB`;
}
