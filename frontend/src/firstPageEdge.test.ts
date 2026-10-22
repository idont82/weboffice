import { describe, it, expect } from 'vitest';

import { withFirstPageEdge } from './firstPageEdge';

describe('withFirstPageEdge', () => {
  it('첫 쪽을 다르게 쓰는 문서면 구역에도 그렇게 적는다', () => {
    // Univer 1.0 은 워드 호환 모드에서 구역이 있으면 documentStyle 의 깃발을
    // 안 보고 꺼 버린다 - 첫 쪽에도 쪽마다 쓰는 머릿글이 찍힌다(2026-10-16 실측).
    const doc = {
      documentStyle: { useFirstPageHeaderFooter: 1 },
      body: { sectionBreaks: [{ startIndex: 120 }] as Array<
        { startIndex: number; useFirstPageHeaderFooter?: number }> },
    };

    const got = withFirstPageEdge(doc);

    expect(got.body.sectionBreaks).toEqual([{ startIndex: 120, useFirstPageHeaderFooter: 1 }]);
  });

  it('구역이 여럿이면 모두 적는다', () => {
    const doc = {
      documentStyle: { useFirstPageHeaderFooter: 1 },
      body: { sectionBreaks: [{ startIndex: 10 }, { startIndex: 20 }] as Array<
        { startIndex: number; useFirstPageHeaderFooter?: number }> },
    };

    expect(withFirstPageEdge(doc).body.sectionBreaks.map((one) => one.useFirstPageHeaderFooter))
      .toEqual([1, 1]);
  });

  it('구역이 제 값을 들고 있으면 덮지 않는다', () => {
    const doc = {
      documentStyle: { useFirstPageHeaderFooter: 1 },
      body: { sectionBreaks: [{ startIndex: 10, useFirstPageHeaderFooter: 0 }] },
    };

    expect(withFirstPageEdge(doc).body.sectionBreaks[0].useFirstPageHeaderFooter).toBe(0);
  });

  it('첫 쪽이 같은 문서는 손대지 않는다', () => {
    const doc = { documentStyle: {}, body: { sectionBreaks: [{ startIndex: 10 }] } };

    expect(withFirstPageEdge(doc).body.sectionBreaks).toEqual([{ startIndex: 10 }]);
  });

  it('구역이 없으면 그대로 둔다', () => {
    const doc = { documentStyle: { useFirstPageHeaderFooter: 1 }, body: { dataStream: '\r\n' } };

    expect(withFirstPageEdge(doc)).toEqual(doc);
  });

  it('본문이 없어도 터지지 않는다', () => {
    const doc = { documentStyle: { useFirstPageHeaderFooter: 1 } };

    expect(withFirstPageEdge(doc)).toEqual(doc);
  });
});
