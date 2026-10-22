import { describe, expect, it } from 'vitest';
import { shrinkBeforePageBreak } from './docTrimBreak';

const BREAK = JSON.stringify({ pageBreakBefore: 1 });

describe('쪽 나눔 앞 빈 줄', () => {
  it('줄 높이를 눌러 앞 쪽에 남긴다', () => {
    const rows = [
      { paragraphNo: 0, text: '앞 글', style: null, runs: null },
      { paragraphNo: 1, text: '', style: null, runs: null },
      { paragraphNo: 2, text: '', style: null, runs: null },
      { paragraphNo: 3, text: '새 쪽', style: BREAK, runs: null },
    ];

    const written = shrinkBeforePageBreak(rows);

    expect(written).toHaveLength(4);
    const squeezed = JSON.parse(written[1].style!).paragraphStyle;
    expect(squeezed.lineSpacing).toBe(6);
    expect(squeezed.spaceBelow.v).toBe(0);
    expect(JSON.parse(written[2].style!).paragraphStyle.lineSpacing).toBe(6);
    // 글자 상자도 줄인다. 그러지 않으면 상자가 줄 높이를 밀어 올린다.
    expect(JSON.parse(written[1].runs!)[0].ts.fs).toBe(1);
  });

  it('글이 있는 줄은 그대로 둔다', () => {
    const rows = [
      { paragraphNo: 0, text: '앞 글', style: null, runs: null },
      { paragraphNo: 1, text: '새 쪽', style: BREAK, runs: null },
    ];

    expect(shrinkBeforePageBreak(rows)[0].style).toBeNull();
  });

  it('표나 그림이 걸린 빈 줄은 그대로 둔다', () => {
    const rows = [
      { paragraphNo: 0, text: '', style: null, runs: null },
      { paragraphNo: 1, text: '새 쪽', style: BREAK, runs: null },
    ];

    expect(shrinkBeforePageBreak(rows, new Set([0]))[0].style).toBeNull();
  });
});
