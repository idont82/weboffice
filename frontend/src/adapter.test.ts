import { describe, expect, it } from 'vitest';
import { fromUniverCellData, toUniverCellData, type CellDto } from './adapter';

const cells: CellDto[] = [
  { rowNo: 0, columnNo: 0, valueType: 'STRING', textValue: '품목명' },
  { rowNo: 0, columnNo: 1, valueType: 'NUMBER', numberValue: '1234.56',
    styleValue: '{"n":{"pattern":"#,##0.00"}}' },
  { rowNo: 0, columnNo: 4, valueType: 'NUMBER', formulaValue: 'B1*2' },
  { rowNo: 1, columnNo: 0, valueType: 'BOOLEAN', booleanYn: 'Y' },
];

describe('toUniverCellData', () => {
  it('행·열 인덱스를 중첩 객체 키로 만든다', () => {
    const data = toUniverCellData(cells);

    expect(Object.keys(data)).toEqual(['0', '1']);
    expect(Object.keys(data[0])).toEqual(['0', '1', '4']);
  });

  it('문자열 셀을 값 타입 1로 매핑한다', () => {
    expect(toUniverCellData(cells)[0][0]).toEqual({ v: '품목명', t: 1 });
  });

  it('숫자 셀은 숫자 값과 서식을 함께 넣는다', () => {
    expect(toUniverCellData(cells)[0][1]).toEqual({
      v: 1234.56,
      t: 2,
      s: { n: { pattern: '#,##0.00' } },
    });
  });

  it('수식 셀은 f 필드로 넣는다', () => {
    expect(toUniverCellData(cells)[0][4]).toEqual({ f: '=B1*2' });
  });

  it('불린 셀은 값 타입 3으로 매핑한다', () => {
    expect(toUniverCellData(cells)[1][0]).toEqual({ v: 1, t: 3 });
  });

  it('빈 셀은 키를 만들지 않는다', () => {
    const data = toUniverCellData([]);
    expect(data).toEqual({});
  });
});

describe('수식 셀 캐시 결과값', () => {
  // 브라우저 실측: Univer 스냅샷의 수식 셀은 {"f":"=A1+A2","v":30,"t":2} 형태로
  // 계산 결과를 함께 들고 있다. 이 값을 서버로 넘기지 않으면 POI 가 수식 셀의
  // 캐시값을 기본값 0 으로 써서, 내려받은 xlsx 를 엑셀이 재계산 없이 열면 0 이 보인다.
  it('Univer 수식 셀의 계산 결과를 numberValue 로 넘긴다', () => {
    const restored = fromUniverCellData({ 2: { 0: { f: '=A1+A2', v: 30, t: 2 } } }, '시트1', []);

    expect(restored.cells[0]).toMatchObject({
      valueType: 'NUMBER', formulaValue: 'A1+A2', numberValue: 30,
    });
  });

  it('문자열을 반환하는 수식은 STRING 으로 넘긴다', () => {
    const restored = fromUniverCellData({ 0: { 0: { f: '=A1&"개"', v: '3개', t: 1 } } }, '시트1', []);

    expect(restored.cells[0]).toMatchObject({
      valueType: 'STRING', formulaValue: 'A1&"개"', textValue: '3개',
    });
  });

  it('계산 전이라 결과값이 없는 수식 셀은 값 없이 넘긴다', () => {
    const restored = fromUniverCellData({ 0: { 0: { f: '=A1+A2' } } }, '시트1', []);

    expect(restored.cells[0]).toMatchObject({ formulaValue: 'A1+A2' });
    expect(restored.cells[0].numberValue).toBeUndefined();
    expect(restored.cells[0].textValue).toBeUndefined();
  });

  it('서버가 준 수식 캐시값을 Univer 셀에도 실어 보낸다', () => {
    const data = toUniverCellData([
      { rowNo: 0, columnNo: 0, valueType: 'NUMBER', formulaValue: 'B1*2', numberValue: 42 },
    ]);

    expect(data[0][0]).toEqual({ f: '=B1*2', v: 42, t: 2 });
  });
});

describe('fromUniverCellData', () => {
  it('왕복 변환에서 셀이 보존된다', () => {
    const restored = fromUniverCellData(toUniverCellData(cells), '시트1', []);

    expect(restored.sheetName).toBe('시트1');
    expect(restored.cells).toHaveLength(4);
    expect(restored.cells).toEqual(expect.arrayContaining([
      expect.objectContaining({ rowNo: 0, columnNo: 0, valueType: 'STRING', textValue: '품목명' }),
      expect.objectContaining({ rowNo: 0, columnNo: 4, formulaValue: 'B1*2' }),
      expect.objectContaining({ rowNo: 1, columnNo: 0, valueType: 'BOOLEAN', booleanYn: 'Y' }),
    ]));
  });
});

describe('서식 왕복', () => {
  it('테두리·정렬을 잃지 않는다', () => {
    // 숫자 서식만 담던 때에는 저장하고 다시 열면 테두리가 사라졌다(2026-08-06 지적).
    const style = { bd: { b: { s: 1, cl: { rgb: '#000000' } } }, ht: 2, vt: 1 };

    const back = fromUniverCellData({ 0: { 0: { v: '값', t: 1, s: style } } }, '', []);

    expect(JSON.parse(back.cells[0].styleValue!)).toEqual(style);
  });

  it('서식이 없으면 담지 않는다', () => {
    // 빈 서식이 사전에 행을 만들면 표가 빈 값으로 찬다.
    const back = fromUniverCellData({ 0: { 0: { v: '값', t: 1 } } }, '', []);

    expect(back.cells[0].styleValue).toBeNull();
  });

  it('서식을 화면 모양 그대로 돌려준다', () => {
    const style = { ht: 3, bl: 1 };

    const shown = toUniverCellData([
      { rowNo: 0, columnNo: 0, valueType: 'STRING', textValue: '값',
        styleValue: JSON.stringify(style) },
    ]);

    expect(shown[0][0].s).toEqual(style);
  });

  it('문자열 서식 id 는 저장하지 않는다', () => {
    // Univer 는 같은 서식을 워크북 서식표에 넣고 셀에는 id 만 남길 수 있다.
    // 그 id 를 저장하면 다시 열 때 아무 서식도 안 붙는다(2026-08-06 지적).
    const back = fromUniverCellData({ 0: { 0: { v: '값', t: 1, s: 'style-7' } } }, '', []);

    expect(back.cells[0].styleValue).toBeNull();
  });

  it('엑셀이 쓰는 시스템 색 이름을 바꾼다', () => {
    // 엑셀 클립보드는 테두리 색을 windowtext 로 내보낸다. 캔버스가 못 그리는
    // 값이라 붙여넣으면 테두리만 사라졌다(2026-08-06 실측).
    const style = { bd: { l: { s: 1, cl: { rgb: 'windowtext' } } } };

    const back = fromUniverCellData({ 0: { 0: { v: '값', t: 1, s: style } } }, '', []);

    expect(JSON.parse(back.cells[0].styleValue!).bd.l.cl.rgb).toBe('#000000');
  });

  it('멀쩡한 색은 건드리지 않는다', () => {
    const style = { bg: { rgb: 'rgb(237,237,237)' }, cl: { rgb: '#c81e1e' } };

    const back = fromUniverCellData({ 0: { 0: { v: '값', t: 1, s: style } } }, '', []);

    expect(JSON.parse(back.cells[0].styleValue!)).toEqual(style);
  });
});

describe('서식 되풀이', () => {
  it('같은 서식 문자열은 한 번만 푼다', () => {
    // 시트 한 장에 셀이 2만 개인데 서식은 백여 가지다. 셀마다 JSON 을 풀면
    // 문서 여는 데만 0.6초를 여기서 썼다(2026-08-20 실측).
    const style = '{"bl":1}';
    const data = toUniverCellData([
      { rowNo: 0, columnNo: 0, valueType: 'STRING', textValue: '가', styleValue: style },
      { rowNo: 0, columnNo: 1, valueType: 'STRING', textValue: '나', styleValue: style },
    ]);

    expect(data[0][0].s).toBe(data[0][1].s);
  });

  it('다른 서식은 따로 푼다', () => {
    const data = toUniverCellData([
      { rowNo: 0, columnNo: 0, valueType: 'STRING', textValue: '가', styleValue: '{"bl":1}' },
      { rowNo: 0, columnNo: 1, valueType: 'STRING', textValue: '나', styleValue: '{"it":1}' },
    ]);

    expect(data[0][0].s).not.toBe(data[0][1].s);
    expect(data[0][1].s).toEqual({ it: 1 });
  });
});
