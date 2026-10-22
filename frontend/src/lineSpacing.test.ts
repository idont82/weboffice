import { describe, expect, it } from 'vitest';

import { LINE_SPACINGS, lineSpacingOf, lineSpacingSetting, shownLineSpacing }
  from './lineSpacing';

describe('줄 간격', () => {
  it('고른 값을 문단 서식 설정으로 바꾼다', () => {
    // Univer 문단 설정 명령은 문단 서식 조각을 그대로 받는다.
    expect(lineSpacingSetting(1.5)).toEqual({
      paragraph: { lineSpacing: 1.5, spacingRule: 0 },
    });
  });

  it('배수로 준다 - 길이로 주면 글자 크기를 안 따라간다', () => {
    // 규칙 0(AUTO)이 배수다. 2(EXACT)로 주면 적힌 px 로 못박혀 글자를 키워도
    // 줄이 안 늘어난다.
    expect(lineSpacingSetting(2).paragraph.spacingRule).toBe(0);
  });

  it('쓰는 값은 워드와 같은 다섯 가지다', () => {
    expect(LINE_SPACINGS.map((one) => one.value)).toEqual([1, 1.15, 1.5, 2, 2.5]);
    expect(LINE_SPACINGS[0].label).toBe('1.0');
  });
});

describe('지금 줄 간격', () => {
  const 문단 = (at: number, style: object | null) => ({ startIndex: at, paragraphStyle: style });

  it('커서가 든 문단의 배수를 읽는다', () => {
    const now = lineSpacingOf([문단(5, { lineSpacing: 1.5, spacingRule: 0 })], 3);

    expect(now).toBe(1.5);
  });

  it('길이로 못박은 문단은 배수가 아니다', () => {
    // 가져온 워드 문서가 그렇다. 배수 목록에서 고를 수 없으니 아무것도 안 짚는다.
    const now = lineSpacingOf([문단(5, { lineSpacing: 16.8, spacingRule: 2 })], 3);

    expect(now).toBeNull();
  });

  it('안 적힌 문단은 기본 한 줄이다', () => {
    expect(lineSpacingOf([문단(5, null)], 3)).toBe(1);
  });

  it('커서가 없거나 문단이 없으면 아무것도 아니다', () => {
    expect(lineSpacingOf([문단(5, null)], null)).toBeNull();
    expect(lineSpacingOf([], 3)).toBeNull();
  });
});

describe('보여 줄 줄 간격', () => {
  const 문단 = (at: number, style: object | null) => ({ startIndex: at, paragraphStyle: style });

  it('배수면 그대로 보인다', () => {
    expect(shownLineSpacing([문단(5, { lineSpacing: 1.5, spacingRule: 0 })], 3, 11))
      .toBe(1.5);
  });

  it('길이로 못박혔으면 배수로 환산한다', () => {
    // 11pt 글자의 한 줄은 14.667px 다. 16.87px 은 1.15줄이다.
    expect(shownLineSpacing([문단(5, { lineSpacing: 16.866, spacingRule: 2 })], 3, 11))
      .toBe(1.15);
  });

  it('글자 크기를 따라간다', () => {
    // 20pt 글자의 한 줄은 26.667px 다.
    expect(shownLineSpacing([문단(5, { lineSpacing: 26.666, spacingRule: 2 })], 3, 20))
      .toBe(1);
  });

  it('알 수 없으면 아무것도 아니다', () => {
    expect(shownLineSpacing([], 3, 11)).toBeNull();
  });
});
