import { describe, expect, it } from 'vitest';

import { DEFAULT_FONT_SIZE, linesToDoc, textOfDoc } from './cellLines';

describe('linesToDoc', () => {
  it('줄마다 문단을 만든다', () => {
    // Univer 문서는 문단을 \r 로 끊고 마지막에 \n 을 둔다.
    const doc = linesToDoc('가\n나\n다');

    expect(doc.body.dataStream).toBe('가\r나\r다\r\n');
  });

  it('문단 자리를 함께 적는다', () => {
    // paragraphs 가 없으면 Univer 가 한 문단으로 읽어 줄이 도로 붙는다.
    const doc = linesToDoc('가\n나');

    expect(doc.body.paragraphs).toEqual([{ startIndex: 1 }, { startIndex: 3 }]);
  });

  it('빈 줄도 한 문단이다', () => {
    const doc = linesToDoc('가\n\n나');

    expect(doc.body.dataStream).toBe('가\r\r나\r\n');
  });

  it('윈도 줄바꿈도 한 줄로 센다', () => {
    // xlsx 에서 온 값에는 \r\n 이 섞여 있다.
    expect(linesToDoc('가\r\n나').body.dataStream).toBe('가\r나\r\n');
  });
});

describe('textOfDoc', () => {
  it('문단을 줄바꿈으로 되돌린다', () => {
    expect(textOfDoc({ body: { dataStream: '가\r나\r다\r\n' } })).toBe('가\n나\n다');
  });

  it('한 줄짜리도 그대로 준다', () => {
    expect(textOfDoc({ body: { dataStream: '가\r\n' } })).toBe('가');
  });

  it('만든 것을 다시 읽으면 원래 글이다', () => {
    const text = '1) 첫째 줄\n  ① 둘째 줄\n  ② 셋째 줄';

    expect(textOfDoc(linesToDoc(text))).toBe(text);
  });

  it('본문이 없으면 빈 글이다', () => {
    expect(textOfDoc({})).toBe('');
  });

  it('문단 끝 표시가 없어도 읽는다', () => {
    expect(textOfDoc({ body: { dataStream: '가' } })).toBe('가');
  });
});

describe('linesToDoc 의 글자 서식', () => {
  const TWO_LINES = '가\n나';

  it('서식이 없으면 기본 글꼴 크기를 적는다', () => {
    // 안 적으면 문단이 문서 기본으로 그려져 한 줄 셀보다 크게 보인다.
    const doc = linesToDoc(TWO_LINES);

    expect(doc.documentStyle.textStyle.fs).toBe(DEFAULT_FONT_SIZE);
    expect(doc.body.textRuns[0].ts.fs).toBe(DEFAULT_FONT_SIZE);
  });

  it('셀 서식의 글꼴을 문단에 옮긴다', () => {
    const doc = linesToDoc(TWO_LINES, { ff: '맑은 고딕', fs: 9, bl: 1 });

    expect(doc.documentStyle.textStyle).toEqual({ fs: 9, ff: '맑은 고딕', bl: 1 });
  });

  it('글 전체에 서식을 건다', () => {
    const doc = linesToDoc(TWO_LINES);

    expect(doc.body.textRuns[0]).toMatchObject({ st: 0, ed: doc.body.dataStream.length });
  });

  it('셀만의 서식은 문단에 넣지 않는다', () => {
    // 배경·테두리·정렬은 셀이 그린다. 문단에도 넣으면 두 번 그려진다.
    const doc = linesToDoc(TWO_LINES, { bg: { rgb: '#fff' }, ht: 2, bd: {} });

    expect(doc.documentStyle.textStyle).toEqual({ fs: DEFAULT_FONT_SIZE });
  });
});
