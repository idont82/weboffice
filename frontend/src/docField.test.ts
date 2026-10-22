import { describe, it, expect } from 'vitest';

import { FIELD_END, FIELD_START, insertFields } from './docField';

describe('insertFields', () => {
  it('필드 자리를 울타리로 감싼다', () => {
    // Univer 는 custom range 를 글 속의 울타리 글자 두 개로 가린다. 그 안의 첫
    // 글자 자리에 쪽 번호를 다시 찍는다.
    const got = insertFields('Page 1 of 9', [{ st: 5, ed: 6, fld: 'PAGE' }], 'f');

    expect(got.text).toBe(`Page ${FIELD_START}1${FIELD_END} of 9`);
    expect(got.ranges).toHaveLength(1);
    expect(got.ranges[0].startIndex).toBe(5);
    expect(got.ranges[0].endIndex).toBe(7);
    expect(got.ranges[0].properties).toEqual({ instruction: 'PAGE', fieldType: 'PAGE' });
  });

  it('필드가 여럿이면 뒤쪽 자리도 밀어 맞춘다', () => {
    const got = insertFields('Page 1 of 9',
      [{ st: 5, ed: 6, fld: 'PAGE' }, { st: 10, ed: 11, fld: 'NUMPAGES' }], 'f');

    expect(got.text).toBe(`Page ${FIELD_START}1${FIELD_END} of ${FIELD_START}9${FIELD_END}`);
    expect(got.ranges.map((one) => [one.startIndex, one.endIndex])).toEqual([[5, 7], [12, 14]]);
  });

  it('글자 서식 자리도 함께 민다', () => {
    // 울타리를 끼우면 그 뒤의 모든 자리가 한 칸씩 밀린다. 안 밀면 굵게·색이
    // 엉뚱한 글자에 붙는다.
    const got = insertFields('Page 1 of 9',
      [{ st: 5, ed: 6, fld: 'PAGE' }], 'f',
      [{ st: 0, ed: 4 }, { st: 7, ed: 11 }]);

    expect(got.runs).toEqual([{ st: 0, ed: 4 }, { st: 9, ed: 13 }]);
  });

  it('필드가 없으면 글을 그대로 둔다', () => {
    const got = insertFields('그냥 글', [], 'f', [{ st: 0, ed: 2 }]);

    expect(got.text).toBe('그냥 글');
    expect(got.ranges).toEqual([]);
    expect(got.runs).toEqual([{ st: 0, ed: 2 }]);
  });

  it('이름표는 서로 다르다', () => {
    const got = insertFields('1 9',
      [{ st: 0, ed: 1, fld: 'PAGE' }, { st: 2, ed: 3, fld: 'NUMPAGES' }], 'f');

    expect(got.ranges[0].rangeId).not.toBe(got.ranges[1].rangeId);
  });

  it('거꾸로 적힌 자리는 건너뛴다', () => {
    const got = insertFields('Page 1', [{ st: 6, ed: 5, fld: 'PAGE' }], 'f');

    expect(got.text).toBe('Page 1');
    expect(got.ranges).toEqual([]);
  });
});
