/**
 * 보던 자리 기억.
 *
 * <p>어느 시트를 보고 있었는지, 그 시트를 몇 %로 보고 있었는지 기억한다. 엑셀도
 * 그렇게 한다(2026-08-13 지적).
 *
 * <p>기억은 <b>계정</b>을 따라간다. 서버가 사람마다 담아 두고 문서를 열 때 내려
 * 준다 - 브라우저에만 두면 다른 PC 에서 기억이 없다(2026-08-13 지적).
 *
 * <p>여기는 그 기억을 화면이 쓰기 좋게 들고 있는 자리다. 서버에 담는 일은 부르는
 * 쪽(main.ts)이 한다 - 이 갈래를 화면 없이 시험할 수 있게 둔다.
 */

import type { SheetViewMemory } from './api';

/** 화면 엔진이 받는 배율 범위. 밖의 값을 담으면 다음에 열 때 아무것도 안 보인다. */
const SMALLEST_ZOOM = 0.1;
const BIGGEST_ZOOM = 4;

let openWorkbookSeq: number | null = null;
let lastSheetSeq: number | null = null;
let zooms = new Map<number, number>();

/** 서버에서 받은 기억을 들인다. 문서를 열 때 한 번 부른다. */
export function useMemory(workbookSeq: number, memory: SheetViewMemory): void {
  openWorkbookSeq = workbookSeq;
  lastSheetSeq = memory.lastSheetSeq ?? null;
  zooms = new Map(Object.entries(memory.zooms ?? {})
    .map(([sheetSeq, zoom]) => [Number(sheetSeq), zoom]));
}

/** 들고 있던 기억을 버린다. 문서를 닫거나 시험을 새로 시작할 때다. */
export function clearMemory(): void {
  openWorkbookSeq = null;
  lastSheetSeq = null;
  zooms = new Map();
}

export function lastSheetOf(workbookSeq: number): number | null {
  return openWorkbookSeq === workbookSeq ? lastSheetSeq : null;
}

export function rememberSheet(workbookSeq: number, sheetSeq: number): void {
  openWorkbookSeq = workbookSeq;
  lastSheetSeq = sheetSeq;
}

export function zoomOf(sheetSeq: number): number | null {
  return zooms.get(sheetSeq) ?? null;
}

/**
 * 배율을 기억한다.
 *
 * @returns 담을 만한 값이었는지. 아니면 서버로 보낼 것도 없다.
 */
export function rememberZoom(sheetSeq: number, zoom: number): boolean {
  if (!Number.isFinite(zoom) || zoom < SMALLEST_ZOOM || zoom > BIGGEST_ZOOM) {
    return false;
  }
  zooms.set(sheetSeq, zoom);
  return true;
}
