import { describe, expect, it } from 'vitest';
import { CJK_SPACING_FLAG, gateCjkSpacing } from './cjkSpacing';

const SOURCE = `function addCJKLatinSpacing(shapedTextList) {
\tconst shapedGlyphs = shapedTextList.flatMap((s) => s.glyphs);
}
function shaping() {
\taddCJKLatinSpacing(shapedTextList);
\treturn shapedTextList;
}`;

describe('gateCjkSpacing', () => {
  it('호출 앞에 스위치를 단다', () => {
    const gated = gateCjkSpacing(SOURCE);

    expect(gated).toContain(`globalThis.${CJK_SPACING_FLAG} !== false && addCJKLatinSpacing(shapedTextList);`);
  });

  it('함수 자체는 건드리지 않는다', () => {
    // 지우지 않는다 - 워드 문서는 이 간격이 있어야 원본과 같다.
    const gated = gateCjkSpacing(SOURCE);

    expect(gated).toContain('function addCJKLatinSpacing(shapedTextList) {');
  });

  it('판올림으로 자리가 바뀌면 빌드를 세운다', () => {
    // 조용히 지나가면 시트 편집기가 다시 벌어진 채로 나간다.
    expect(() => gateCjkSpacing('function nothing() {}'))
      .toThrow(/addCJKLatinSpacing/);
  });
});
