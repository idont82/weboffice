/**
 * 화면의 행 높이·열 너비·숨김을 저장할 모양으로 옮긴다.
 *
 * <p>숨김은 크기 0 으로 적는다. 표에 칸을 더하지 않으려는 것이고, 엑셀도 너비 0 을
 * 숨김으로 본다. 숨김을 안 담던 때에는 열을 숨겨도 새로고침하면 되살아났다
 * (2026-09-03 지적).
 */

/** 숨김을 뜻하는 크기. 0 은 "보이지 않는다" 이지 "폭이 0" 이 아니다. */
export const HIDDEN = 0;

/** 저장할 크기 한 줄. */
export interface SheetSize {
  axisType: 'ROW' | 'COLUMN';
  indexNo: number;
  sizePx: number;
}

/** Univer 스냅샷에서 우리가 보는 것만. */
export interface SizeSnapshot {
  defaultRowHeight: number;
  defaultColumnWidth: number;
  /** hd 는 숨김 표시(1 이면 숨김). */
  rowData?: Record<string, { h?: number; ah?: number; hd?: number }>;
  columnData?: Record<string, { w?: number; hd?: number }>;
}

/**
 * 기본과 다른 것만 담는다.
 *
 * <p>숨김은 크기가 기본과 같아도 담는다 - 크기만 보면 "손댄 적 없음" 으로 읽혀
 * 빠지는데, 숨김은 크기와 다른 사실이다.
 */
export function sizesOf(snapshot: SizeSnapshot): SheetSize[] {
  const sizes: SheetSize[] = [];

  for (const [indexNo, row] of Object.entries(snapshot.rowData ?? {})) {
    const height = row.h ?? row.ah;
    if (row.hd) {
      sizes.push({ axisType: 'ROW', indexNo: Number(indexNo), sizePx: HIDDEN });
    } else if (height != null && height !== snapshot.defaultRowHeight) {
      sizes.push({ axisType: 'ROW', indexNo: Number(indexNo), sizePx: Math.round(height) });
    }
  }

  for (const [indexNo, column] of Object.entries(snapshot.columnData ?? {})) {
    if (column.hd) {
      sizes.push({ axisType: 'COLUMN', indexNo: Number(indexNo), sizePx: HIDDEN });
    } else if (column.w != null && column.w !== snapshot.defaultColumnWidth) {
      sizes.push({ axisType: 'COLUMN', indexNo: Number(indexNo), sizePx: Math.round(column.w) });
    }
  }

  return sizes;
}
