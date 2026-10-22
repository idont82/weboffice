import type { SheetSize } from './api';

/** Univer 스냅샷의 행 하나. ia=0 은 "높이를 스스로 바꾸지 마라" 는 뜻이다. */
interface RowSnapshot {
  h?: number;
  ia: 0;
}

/**
 * 저장된 크기를 Univer 스냅샷 모양으로 바꾼다.
 *
 * <p>행은 전부 "높이 고정"(ia=0)으로 표시한다. 그러지 않으면 Univer 가 시트를
 * 처음 그릴 때 행마다 글자를 재어 높이를 다시 계산한다 - 시트 하나를 여는 데
 * 11초가 걸렸고(2026-08-18 실측), 다시 계산한 높이가 엑셀에서 가져온 높이를
 * 덮어써 서버로도 나갔다.
 *
 * <p>높이는 손댄 행만 적는다. 비워 두면 시트 기본 높이가 쓰인다 - 엑셀도 같다.
 */
export function toRowColumnData(sizes: SheetSize[], rowCount: number) {
  const rowData: Record<string, RowSnapshot> = {};
  const columnData: Record<string, { w: number }> = {};

  for (let row = 0; row < rowCount; row += 1) {
    rowData[String(row)] = { ia: 0 };
  }

  for (const size of sizes) {
    if (size.sizePx == null) continue;
    if (size.axisType === 'ROW') {
      rowData[String(size.indexNo)] = { h: size.sizePx, ia: 0 };
    } else {
      columnData[String(size.indexNo)] = { w: size.sizePx };
    }
  }

  return { rowData, columnData };
}
