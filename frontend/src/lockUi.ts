/** 셀 좌표를 사람이 읽는 A1 표기로 바꾼다. 자리 표시(presence)와 상태 문구가 쓴다. */

export function toA1(rowNo: number, columnNo: number): string {
  let column = '';
  let remaining = columnNo;
  do {
    column = String.fromCharCode(65 + (remaining % 26)) + column;
    remaining = Math.floor(remaining / 26) - 1;
  } while (remaining >= 0);
  return `${column}${rowNo + 1}`;
}
