import { describe, expect, it } from 'vitest';

import {
  COLORS, NO_FILTER, colorName, isFiltering, matching, splitStarred, toggleColor,
} from './fileMarks';

interface Row {
  workbookSeq: number;
  starred: boolean;
  color: string | null;
}

function row(workbookSeq: number, starred = false, color: string | null = null): Row {
  return { workbookSeq, starred, color };
}

describe('COLORS', () => {
  it('일곱 가지다', () => {
    // 서버가 아는 색과 같아야 한다. 어긋나면 칠한 색이 화면에서 사라진다.
    expect(COLORS).toEqual(['RED', 'ORANGE', 'YELLOW', 'GREEN', 'BLUE', 'PURPLE', 'GRAY']);
  });
});

describe('isFiltering', () => {
  it('아무것도 안 골랐으면 거르지 않는 것이다', () => {
    expect(isFiltering(NO_FILTER)).toBe(false);
  });

  it('별표만 골라도 거르는 것이다', () => {
    expect(isFiltering({ starred: true, colors: [] })).toBe(true);
  });

  it('색만 골라도 거르는 것이다', () => {
    expect(isFiltering({ starred: false, colors: ['RED'] })).toBe(true);
  });
});

describe('matching', () => {
  it('안 고르면 다 남는다', () => {
    const rows = [row(1), row(2, true, 'RED')];
    expect(matching(rows, NO_FILTER)).toEqual(rows);
  });

  it('별표를 고르면 별표만 남는다', () => {
    const rows = [row(1), row(2, true)];
    expect(matching(rows, { starred: true, colors: [] })).toEqual([rows[1]]);
  });

  it('색을 여럿 고르면 그중 하나라도 맞으면 남는다', () => {
    const rows = [row(1, false, 'RED'), row(2, false, 'BLUE'), row(3, false, 'GRAY')];
    expect(matching(rows, { starred: false, colors: ['RED', 'BLUE'] }))
      .toEqual([rows[0], rows[1]]);
  });

  it('별표와 색을 함께 고르면 둘 다 맞아야 남는다', () => {
    // 칩을 둘 누르면 좁혀지기를 바란다 - 넓어지면 누를수록 결과가 늘어난다.
    const rows = [row(1, true, 'BLUE'), row(2, true, 'RED'), row(3, false, 'RED')];
    expect(matching(rows, { starred: true, colors: ['RED'] })).toEqual([rows[1]]);
  });

  it('색을 안 칠한 것은 색으로 거를 때 빠진다', () => {
    const rows = [row(1), row(2, false, 'RED')];
    expect(matching(rows, { starred: false, colors: ['RED'] })).toEqual([rows[1]]);
  });
});

describe('toggleColor', () => {
  it('없던 색은 더한다', () => {
    expect(toggleColor(NO_FILTER, 'RED').colors).toEqual(['RED']);
  });

  it('있던 색은 뺀다', () => {
    expect(toggleColor({ starred: false, colors: ['RED', 'BLUE'] }, 'RED').colors)
      .toEqual(['BLUE']);
  });

  it('색을 눌러도 별표 여부는 그대로다', () => {
    expect(toggleColor({ starred: true, colors: [] }, 'RED').starred).toBe(true);
  });
});

describe('splitStarred', () => {
  it('별표를 앞 묶음으로 뽑는다', () => {
    const rows = [row(1), row(2, true), row(3, true)];
    expect(splitStarred(rows)).toEqual({ starred: [rows[1], rows[2]], rest: [rows[0]] });
  });

  it('별표가 없으면 앞 묶음이 빈다', () => {
    const rows = [row(1), row(2)];
    expect(splitStarred(rows)).toEqual({ starred: [], rest: rows });
  });

  it('묶음 안 차례는 원래 차례 그대로다', () => {
    // 목록은 수정일 최신순이다. 별표를 뽑았다고 그 안에서 뒤섞이면 안 된다.
    const rows = [row(9, true), row(1, true)];
    expect(splitStarred(rows).starred.map((one) => one.workbookSeq)).toEqual([9, 1]);
  });
});

describe('colorName', () => {
  it('이름을 지었으면 그 이름이다', () => {
    expect(colorName([{ colorCode: 'RED', labelName: '급함' }], 'RED')).toBe('급함');
  });

  it('안 지었으면 기본 색 이름이다', () => {
    expect(colorName([], 'RED')).toBe('빨강');
  });

  it('모르는 색은 코드를 그대로 부른다', () => {
    expect(colorName([], 'RAINBOW')).toBe('RAINBOW');
  });
});
