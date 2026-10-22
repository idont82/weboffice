import { describe, expect, test } from 'vitest';

import { neighborSheet } from './shortcuts';

const sheets = [
  { sheetSeq: 10, sheetName: '1월' },
  { sheetSeq: 11, sheetName: '2월' },
  { sheetSeq: 12, sheetName: '3월' },
];

describe('neighborSheet', () => {
  test('다음 시트로 간다', () => {
    expect(neighborSheet(sheets, 11, 1)?.sheetName).toBe('3월');
  });

  test('이전 시트로 간다', () => {
    expect(neighborSheet(sheets, 11, -1)?.sheetName).toBe('1월');
  });

  test('끝에서는 감기지 않는다', () => {
    // 엑셀도 여기서 멈춘다. 감기면 마지막 시트에서 한 번 더 눌렀을 때 첫 시트로
    // 튀어 어디로 갔는지 모른다.
    expect(neighborSheet(sheets, 12, 1)).toBeNull();
    expect(neighborSheet(sheets, 10, -1)).toBeNull();
  });

  test('모르는 시트면 아무 데도 안 간다', () => {
    expect(neighborSheet(sheets, 999, 1)).toBeNull();
  });

  test('시트가 하나뿐이면 갈 곳이 없다', () => {
    expect(neighborSheet([sheets[0]], 10, 1)).toBeNull();
  });
});
