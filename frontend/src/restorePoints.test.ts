import { describe, expect, it } from 'vitest';

import { pointWord, timeline } from './restorePoints';
import type { RestorePoint } from './restorePoints';
import type { Version } from './historyPanel';

function version(versionId: number, toAt: string): Version {
  return { versionId, fromAt: toAt, toAt, changeCount: 1, editors: ['나'] };
}

function point(snapshotSeq: number, takenAt: string,
  fields: Partial<RestorePoint> = {}): RestorePoint {
  return {
    snapshotSeq, takenAt, reason: 'MANUAL', label: null, takenBy: '나', bodySize: 1000,
    ...fields,
  };
}

describe('timeline', () => {
  it('버전과 복구 지점을 시각 차례로 섞는다', () => {
    const rows = timeline(
      [version(1, '2026-08-31T10:00:00'), version(2, '2026-08-31T08:00:00')],
      [point(7, '2026-08-31T09:00:00')],
    );

    expect(rows.map((one) => one.kind)).toEqual(['version', 'point', 'version']);
  });

  it('최근 것이 앞이다', () => {
    const rows = timeline([version(1, '2026-08-31T08:00:00')],
      [point(7, '2026-08-31T12:00:00')]);

    expect(rows[0].kind).toBe('point');
  });

  it('시각을 모르는 줄은 맨 뒤로 보낸다', () => {
    // 옛 문서에는 기록 시각이 없다. 앞에 오면 "지금 상태" 자리를 빼앗는다.
    const rows = timeline(
      [{ versionId: 1, fromAt: null, toAt: null, changeCount: 0, editors: [] },
        version(2, '2026-08-31T08:00:00')],
      [],
    );

    expect(rows[0].at).toBe('2026-08-31T08:00:00');
  });

  it('복구 지점만 있어도 그린다', () => {
    expect(timeline([], [point(7, '2026-08-31T09:00:00')])).toHaveLength(1);
  });

  it('둘 다 없으면 빈 줄이다', () => {
    expect(timeline([], [])).toEqual([]);
  });

  it('같은 시각이면 복구 지점이 앞이다', () => {
    // 되돌리기 직전 지점은 그 편집보다 앞선 모습이다.
    const rows = timeline([version(1, '2026-08-31T09:00:00')],
      [point(7, '2026-08-31T09:00:00')]);

    expect(rows[0].kind).toBe('point');
  });
});

describe('pointWord', () => {
  it('손으로 만든 것은 붙인 이름으로 부른다', () => {
    expect(pointWord(point(1, '2026-08-31T09:00:00', { label: '보내기 전' })))
      .toBe('보내기 전');
  });

  it('이름이 없으면 왜 찍혔는지로 부른다', () => {
    expect(pointWord(point(1, '2026-08-31T09:00:00', { reason: 'SESSION', label: null })))
      .toBe('편집을 마친 뒤');
  });

  it('AI 가 고치기 전 것은 그렇게 부른다', () => {
    expect(pointWord(point(1, '2026-08-31T09:00:00', { reason: 'BEFORE_AI', label: null })))
      .toBe('AI 가 고치기 전');
  });

  it('모르는 이유는 그냥 복구 지점이다', () => {
    expect(pointWord(point(1, '2026-08-31T09:00:00', { reason: 'ZZZ', label: null })))
      .toBe('복구 지점');
  });
});
