import { describe, expect, it } from 'vitest';
import { withLineHeights } from './docLineHeight';

/** 글꼴 행간을 1.22 로 보는 자. 캔버스 대신 쓴다. */
const ratio = () => 1.22;

describe('줄 높이', () => {
  it('배수와 글꼴 행간으로 다시 잰다', () => {
    const rows = [{
      paragraphNo: 0,
      text: '본문',
      style: JSON.stringify({ paragraphStyle: { lineMultiple: 1.079, lineSpacing: 16, spacingRule: 2 } }),
      runs: JSON.stringify([{ st: 0, ed: 2, ts: { fs: 9 } }]),
    }];

    const written = JSON.parse(withLineHeights(rows, null, ratio)[0].style!);
    // 9pt = 12px, 12 * 1.079 * 1.22 = 15.8px
    expect(written.paragraphStyle.lineSpacing).toBeCloseTo(15.8, 1);
    expect(written.paragraphStyle.spacingRule).toBe(2);
  });

  it('배수를 안 적은 문단은 그대로 둔다', () => {
    const rows = [{
      paragraphNo: 0,
      text: '본문',
      style: JSON.stringify({ paragraphStyle: { lineSpacing: 21, spacingRule: 2 } }),
      runs: null,
    }];

    const written = JSON.parse(withLineHeights(rows, null, ratio)[0].style!);
    expect(written.paragraphStyle.lineSpacing).toBe(21);
  });

  it('글자 크기를 안 적었으면 문서 기본 크기로 잰다', () => {
    const rows = [{
      paragraphNo: 0,
      text: '본문',
      style: JSON.stringify({ paragraphStyle: { lineMultiple: 1, spacingRule: 2 } }),
      runs: null,
    }];

    const written = JSON.parse(withLineHeights(rows, null, ratio, 11)[0].style!);
    // 11pt = 14.67px, * 1 * 1.22 = 17.9px
    expect(written.paragraphStyle.lineSpacing).toBeCloseTo(17.9, 1);
  });
});
