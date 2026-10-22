import { describe, expect, it } from 'vitest';
import { toRowColumnData } from './sheetRows';

describe('toRowColumnData', () => {
  it('저장된 행 높이를 그대로 싣는다', () => {
    const { rowData } = toRowColumnData([{ axisType: 'ROW', indexNo: 3, sizePx: 26 }], 10);

    expect(rowData['3'].h).toBe(26);
  });

  it('저장된 열 너비를 그대로 싣는다', () => {
    const { columnData } = toRowColumnData([{ axisType: 'COLUMN', indexNo: 1, sizePx: 127 }], 10);

    expect(columnData['1'].w).toBe(127);
  });

  it('모든 행을 높이 고정으로 표시한다', () => {
    // ia=0 이 없으면 Univer 가 시트를 처음 그릴 때 행마다 글자를 재어 높이를
    // 다시 계산한다. 시트 하나에 11초가 걸렸다(2026-08-18 실측).
    const { rowData } = toRowColumnData([{ axisType: 'ROW', indexNo: 0, sizePx: 26 }], 3);

    expect(rowData['0'].ia).toBe(0);
    expect(rowData['1'].ia).toBe(0);
    expect(rowData['2'].ia).toBe(0);
  });

  it('크기가 없는 행은 높이를 비워 둔다', () => {
    // 높이를 적으면 시트 기본 높이가 아니라 그 값이 쓰인다.
    const { rowData } = toRowColumnData([], 2);

    expect(rowData['1'].h).toBeUndefined();
  });

  it('행 수를 넘겨 짚지 않는다', () => {
    const { rowData } = toRowColumnData([], 2);

    expect(Object.keys(rowData)).toEqual(['0', '1']);
  });

  it('행 수 밖에 저장된 크기가 있으면 그것도 싣는다', () => {
    const { rowData } = toRowColumnData([{ axisType: 'ROW', indexNo: 9, sizePx: 40 }], 2);

    expect(rowData['9'].h).toBe(40);
    expect(rowData['9'].ia).toBe(0);
  });
});
