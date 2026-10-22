import { describe, expect, it } from 'vitest';

import {
  columnEdges, hitTable, resizeColumns, resizeRow, resizeTable, resizeTableLeft, resizeMargin,
  rowEdges, tableAt,
  MIN_COLUMN_WIDTH, MIN_ROW_HEIGHT,
} from './tableResize';

describe('columnEdges', () => {
  it('칸 사이 경계 자리를 준다', () => {
    // 표 왼쪽이 100 이고 칸이 50·80 이면 경계는 150 하나다. 표 바깥 끝(230)은
    // 경계가 아니다 - 그 오른쪽에는 늘려 줄 칸이 없다.
    expect(columnEdges([50, 80], 100)).toEqual([150]);
  });

  it('칸이 셋이면 경계는 둘이다', () => {
    expect(columnEdges([50, 50, 50], 0)).toEqual([50, 100]);
  });

  it('칸이 하나면 경계가 없다', () => {
    expect(columnEdges([100], 0)).toEqual([]);
  });
});

describe('resizeColumns', () => {
  it('경계를 끌면 양옆 칸만 바뀐다', () => {
    // 표 전체 폭은 그대로 두어야 한다. 한 칸을 늘리면 옆 칸이 그만큼 준다 -
    // 그러지 않으면 표가 종이 밖으로 나간다.
    expect(resizeColumns([100, 100, 100], 0, 20)).toEqual([120, 80, 100]);
  });

  it('왼쪽으로 끌면 반대로 바뀐다', () => {
    expect(resizeColumns([100, 100], 0, -30)).toEqual([70, 130]);
  });

  it('가장 좁은 너비보다 더 줄이지 않는다', () => {
    // 칸이 0 이 되면 그 칸의 글을 다시 잡을 수 없다.
    const widths = resizeColumns([100, 100], 0, 200);

    expect(widths[1]).toBe(MIN_COLUMN_WIDTH);
    expect(widths[0] + widths[1]).toBe(200);
  });

  it('반대쪽도 가장 좁은 너비를 지킨다', () => {
    const widths = resizeColumns([100, 100], 0, -200);

    expect(widths[0]).toBe(MIN_COLUMN_WIDTH);
    expect(widths[0] + widths[1]).toBe(200);
  });

  it('없는 경계는 그대로 둔다', () => {
    expect(resizeColumns([100, 100], 5, 20)).toEqual([100, 100]);
  });
});

describe('tableAt', () => {
  const tables = [
    { startIndex: 10, endIndex: 40, tableId: 'wo-table-1' },
    { startIndex: 60, endIndex: 90, tableId: 'wo-table-2' },
  ];

  it('커서가 든 표를 찾는다', () => {
    expect(tableAt(tables, 20)?.tableId).toBe('wo-table-1');
    expect(tableAt(tables, 70)?.tableId).toBe('wo-table-2');
  });

  it('표 밖이면 아무것도 아니다', () => {
    // 표 밖에서 눈금자에 표 손잡이를 그리면 무엇을 끄는지 알 수 없다.
    expect(tableAt(tables, 5)).toBeNull();
    expect(tableAt(tables, 50)).toBeNull();
    expect(tableAt(tables, 200)).toBeNull();
  });

  it('표 끝자리도 표 안이다', () => {
    expect(tableAt(tables, 40)?.tableId).toBe('wo-table-1');
  });
});

describe('rowEdges', () => {
  it('줄 아래마다 경계가 있다', () => {
    // 칸 너비와 달리 마지막 줄 아래도 경계다. 아래로 늘리면 표가 길어질 뿐
    // 종이 밖으로 밀려나지 않는다.
    expect(rowEdges([30, 40], 100)).toEqual([130, 170]);
  });
});

describe('resizeRow', () => {
  it('끈 줄만 높아진다', () => {
    // 줄 높이는 아래 줄에서 뺏지 않는다. 글은 세로로 흐르므로 늘린 만큼 표가
    // 길어지면 된다.
    expect(resizeRow([30, 40], 0, 15)).toEqual([45, 40]);
  });

  it('가장 낮은 높이보다 더 줄이지 않는다', () => {
    expect(resizeRow([30, 40], 1, -100)).toEqual([30, MIN_ROW_HEIGHT]);
  });

  it('없는 줄은 그대로 둔다', () => {
    expect(resizeRow([30, 40], 9, 15)).toEqual([30, 40]);
  });
});

describe('hitTable', () => {
  const rects = [
    { tableId: 'wo-table-1', left: 100, top: 200, width: 300, height: 100 },
    { tableId: 'wo-table-2', left: 100, top: 400, width: 300, height: 100 },
  ];

  it('마우스가 얹힌 표를 찾는다', () => {
    expect(hitTable(rects, 150, 250)?.tableId).toBe('wo-table-1');
    expect(hitTable(rects, 150, 450)?.tableId).toBe('wo-table-2');
  });

  it('표 밖이면 아무것도 아니다', () => {
    // 표 밖에서는 손잡이를 두지 않는다. 글자 고르기를 방해할 뿐이다.
    expect(hitTable(rects, 50, 250)).toBeNull();
    expect(hitTable(rects, 150, 350)).toBeNull();
  });

  it('가장자리 바로 밖도 잡는다', () => {
    // 경계선 위에서 잡으려면 테두리 언저리까지는 표로 봐야 한다.
    expect(hitTable(rects, 150, 196)?.tableId).toBe('wo-table-1');
    expect(hitTable(rects, 150, 180)).toBeNull();
  });
});

describe('resizeTable', () => {
  it('오른쪽 끝을 끌면 마지막 칸이 늘어난다', () => {
    // 표 전체가 넓어진다. 워드·한글도 오른쪽 테두리를 이렇게 다룬다.
    expect(resizeTable([100, 100], 50, 600)).toEqual([100, 150]);
  });

  it('본문 폭보다 넓어지지 않는다', () => {
    // 종이 밖으로 나간 표는 인쇄도 내려받기도 어긋난다.
    expect(resizeTable([100, 100], 900, 300)).toEqual([100, 200]);
  });

  it('마지막 칸이 가장 좁은 너비보다 줄지 않는다', () => {
    expect(resizeTable([100, 100], -300, 600)).toEqual([100, MIN_COLUMN_WIDTH]);
  });

  it('칸이 없으면 그대로 둔다', () => {
    expect(resizeTable([], 50, 600)).toEqual([]);
  });
});

describe('resizeTableLeft', () => {
  it('왼쪽으로 끌면 들여쓰기가 줄고 첫 칸이 늘어난다', () => {
    // 표 오른쪽 끝은 그대로 있어야 한다. 왼쪽 테두리만 움직인다.
    const moved = resizeTableLeft([100, 100], 50, -30, 600);

    expect(moved.indent).toBe(20);
    expect(moved.widths).toEqual([130, 100]);
  });

  it('오른쪽으로 끌면 들여쓰기가 늘고 첫 칸이 준다', () => {
    const moved = resizeTableLeft([100, 100], 0, 40, 600);

    expect(moved.indent).toBe(40);
    expect(moved.widths).toEqual([60, 100]);
  });

  it('종이 왼쪽 밖으로 나가지 않는다', () => {
    // 들여쓰기가 음수면 표가 여백을 넘어 종이 밖에 걸린다.
    const moved = resizeTableLeft([100, 100], 20, -80, 600);

    expect(moved.indent).toBe(0);
    expect(moved.widths).toEqual([120, 100]);
  });

  it('첫 칸이 가장 좁은 너비보다 줄지 않는다', () => {
    const moved = resizeTableLeft([100, 100], 0, 300, 600);

    expect(moved.widths[0]).toBe(MIN_COLUMN_WIDTH);
    expect(moved.indent).toBe(80);
  });
});

describe('resizeMargin', () => {
  it('왼쪽 여백을 끈 만큼 옮긴다', () => {
    expect(resizeMargin(96, 30, 816, 48)).toBe(126);
  });

  it('종이 밖으로 나가지 않는다', () => {
    // 여백이 음수면 글이 종이 밖에 걸린다.
    expect(resizeMargin(96, -200, 816, 48)).toBe(0);
  });

  it('반대쪽 여백을 넘어서지 않는다', () => {
    // 본문이 사라지면 글을 넣을 자리가 없다.
    const moved = resizeMargin(96, 900, 816, 48);

    expect(moved).toBeLessThan(816 - 48);
  });
});
