import { describe, expect, it } from 'vitest';

import { wordRangeAt } from './wordRange';

describe('wordRangeAt', () => {
  it('낱말 가운데 커서면 그 낱말을 잡는다', () => {
    // 워드가 그렇게 한다 - 고른 글자가 없으면 커서가 놓인 낱말에 서식이 걸린다.
    expect(wordRangeAt('hello world', 8)).toEqual({ start: 6, end: 11 });
  });

  it('한글도 낱말로 잡는다', () => {
    expect(wordRangeAt('가나다 라마바', 5)).toEqual({ start: 4, end: 7 });
  });

  it('낱말 끝에 붙은 커서도 그 낱말을 잡는다', () => {
    // 글을 치다 멈춘 자리가 여기다. 가장 흔한 자리라 놓치면 안 된다.
    expect(wordRangeAt('hello world', 11)).toEqual({ start: 6, end: 11 });
  });

  it('낱말 앞에 붙은 커서도 그 낱말을 잡는다', () => {
    expect(wordRangeAt('hello world', 6)).toEqual({ start: 6, end: 11 });
  });

  it('빈 곳에 커서가 있으면 잡지 않는다', () => {
    // 공백 한가운데다. 엉뚱한 낱말을 물들이느니 아무것도 안 한다.
    expect(wordRangeAt('a  b', 2)).toBeNull();
  });

  it('빈 문단에서는 잡지 않는다', () => {
    expect(wordRangeAt('', 0)).toBeNull();
  });

  it('구두점은 낱말에 넣지 않는다', () => {
    expect(wordRangeAt('안녕, 세상', 2)).toEqual({ start: 0, end: 2 });
  });
});
