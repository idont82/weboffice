import { describe, expect, it } from 'vitest';

import { PURGE_CHOICES, countToPurge } from './trashPurge';

function item(deletedAt: string) {
  return { workbookSeq: 1, workbookName: '버린 것', docType: 'SHEET' as const, deletedAt };
}

const NOW = new Date('2026-09-03T15:00:00');

describe('PURGE_CHOICES', () => {
  it('전부·1일·3일·7일 네 갈래다', () => {
    expect(PURGE_CHOICES.map((one) => one.keepDays)).toEqual([0, 1, 3, 7]);
  });

  it('날 수를 숫자로 적는다', () => {
    // "사흘·이레" 는 세는 말이라 한눈에 안 들어온다(2026-09-03 지적).
    expect(PURGE_CHOICES.map((one) => one.label))
      .toEqual(['전부 비우기', '1일 지난 것 비우기', '3일 지난 것 비우기',
        '7일 지난 것 비우기']);
  });
});

describe('countToPurge', () => {
  it('전부 비우기는 다 센다', () => {
    const rows = [item('2026-09-03T14:00:00'), item('2026-08-20T10:00:00')];

    expect(countToPurge(rows, 0, NOW)).toBe(2);
  });

  it('오늘 것을 남기면 어제 것만 센다', () => {
    const rows = [item('2026-09-03T09:00:00'), item('2026-09-02T09:00:00')];

    expect(countToPurge(rows, 1, NOW)).toBe(1);
  });

  it('사흘을 남기면 그보다 오래된 것만 센다', () => {
    const rows = [
      item('2026-09-01T09:00:00'), // 이틀 전 - 남는다
      item('2026-08-29T09:00:00'), // 닷새 전 - 지운다
    ];

    expect(countToPurge(rows, 3, NOW)).toBe(1);
  });

  it('이레를 남기면 그보다 오래된 것만 센다', () => {
    const rows = [item('2026-08-29T09:00:00'), item('2026-08-20T09:00:00')];

    expect(countToPurge(rows, 7, NOW)).toBe(1);
  });

  it('버린 때를 모르는 것은 세지 않는다', () => {
    // 옛 문서에는 시각이 없다. 세면 "몇 개가 지워진다" 가 거짓말이 된다.
    expect(countToPurge([{ ...item(''), deletedAt: null as unknown as string }], 1, NOW))
      .toBe(0);
  });

  it('휴지통이 비면 0 이다', () => {
    expect(countToPurge([], 0, NOW)).toBe(0);
  });
});
