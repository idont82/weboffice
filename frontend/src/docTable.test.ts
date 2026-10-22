import { describe, expect, it } from 'vitest';

import { fitFloating, toBodyWithTables } from './docTable';

/** Univer 가 표를 그릴 때 쓰는 제어문자. 실제 문서를 뜯어 확인한 것이다. */
const TABLE_START = String.fromCharCode(0x1a);
const ROW_START = String.fromCharCode(0x1b);
const CELL_START = String.fromCharCode(0x1c);
const CELL_END = String.fromCharCode(0x1d);
const ROW_END = String.fromCharCode(0x0e);
const TABLE_END = String.fromCharCode(0x0f);
const CR = String.fromCharCode(0x0d);
const LF = String.fromCharCode(0x0a);

describe('toBodyWithTables', () => {
  it('표 없는 문서는 문단만 만든다', () => {
    const body = toBodyWithTables([{ paragraphNo: 0, text: '글', style: null, runs: null }], []);

    expect(body.dataStream).toBe('글\r\n');
    expect(body.tables).toEqual([]);
  });

  it('표 한 칸을 제어문자로 감싼다', () => {
    // 셀은 시작·글·문단끝·구역끝·셀끝 차례다. 하나라도 빠지면 Univer 가 표를
    // 그리지 못한다(2026-08-12 실측: 우리가 만든 표를 뜯어 확인했다).
    const body = toBodyWithTables([], [{ afterParagraphNo: -1, cells: [['가']] }]);

    expect(body.dataStream).toBe(
      `${TABLE_START}${ROW_START}${CELL_START}가\r\n${CELL_END}${ROW_END}${TABLE_END}\r\n`);
  });

  it('행과 칸을 차례로 감싼다', () => {
    const body = toBodyWithTables([], [{ afterParagraphNo: -1, cells: [['가', '나'], ['다', '라']] }]);

    expect(body.dataStream).toBe(
      TABLE_START
      + `${ROW_START}${CELL_START}가\r\n${CELL_END}${CELL_START}나\r\n${CELL_END}${ROW_END}`
      + `${ROW_START}${CELL_START}다\r\n${CELL_END}${CELL_START}라\r\n${CELL_END}${ROW_END}`
      + TABLE_END + '\r\n');
  });

  it('표가 놓인 자리와 크기를 알려 준다', () => {
    const body = toBodyWithTables([], [{ afterParagraphNo: -1, cells: [['가']] }]);

    expect(body.tables).toHaveLength(1);
    expect(body.tables![0].startIndex).toBe(0);
    // 표 끝() 다음 자리까지가 표다.
    expect(body.tables![0].endIndex).toBe(body.dataStream.indexOf(TABLE_END) + 1);
    expect(Object.keys(body.tableSource!)).toHaveLength(1);
  });

  it('칸마다 문단 자리를 잡아 준다', () => {
    // 문단 자리가 없으면 칸 안에 글을 칠 수 없다.
    const body = toBodyWithTables([], [{ afterParagraphNo: -1, cells: [['가', '나']] }]);

    const marks = body.paragraphs.map((p) => p.startIndex);
    expect(marks).toContain(body.dataStream.indexOf('가') + 1);
    expect(marks).toContain(body.dataStream.indexOf('나') + 1);
  });

  it('표 얼개를 빠짐없이 채운다', () => {
    // 칸 너비(tableColumns)나 표 폭(size)이 없으면 Univer 가 문서를 통째로 못
    // 그린다 - 화면이 하얗게 비고 "reading 'slice'" 만 남았다(2026-08-12 실측).
    const body = toBodyWithTables([], [{ afterParagraphNo: -1, cells: [['가', '나']] }]);
    const table = Object.values(body.tableSource!)[0] as Record<string, unknown>;

    expect(Object.keys(table)).toEqual(expect.arrayContaining([
      'tableRows', 'tableColumns', 'tableId', 'align', 'indent', 'textWrap',
      'position', 'dist', 'cellMargin', 'size',
    ]));
    expect(table.tableColumns as unknown[]).toHaveLength(2);
  });

  it('가운데 표는 가운데에 앉는다', () => {
    // 표지의 주황 막대가 폭 2인치짜리 가운데 표다. 정렬을 흘리면 왼쪽으로 쏠린다
    // (2026-08-20 지적).
    const body = toBodyWithTables([], [{ afterParagraphNo: -1, cells: [['']], align: 2 }]);
    const table = Object.values(body.tableSource!)[0] as { align: number };

    expect(table.align).toBe(1);   // Univer: 0 왼쪽 · 1 가운데 · 2 오른쪽
  });

  it('오른쪽 표와 왼쪽 표도 그 자리에 앉는다', () => {
    const right = toBodyWithTables([], [{ afterParagraphNo: -1, cells: [['']], align: 3 }]);
    const left = toBodyWithTables([], [{ afterParagraphNo: -1, cells: [['']] }]);

    expect((Object.values(right.tableSource!)[0] as { align: number }).align).toBe(2);
    expect((Object.values(left.tableSource!)[0] as { align: number }).align).toBe(0);
  });

  it('못박은 줄 높이를 그대로 쓴다', () => {
    // 4px 막대가 글 한 줄 높이로 부풀면 원본과 딴판이다.
    const body = toBodyWithTables([], [{
      afterParagraphNo: -1, cells: [[''], ['']], rowHeights: [4, null],
    }]);
    const table = Object.values(body.tableSource!)[0] as {
      tableRows: { trHeight: { val: { v: number }; hRule: number } }[];
    };

    expect(table.tableRows[0].trHeight).toEqual({ val: { v: 4 }, hRule: 2 });
    expect(table.tableRows[1].trHeight.hRule).toBe(0);
  });

  it('칸 여백은 못박은 줄에서 사라진다', () => {
    // 위아래 여백 5px 이 남으면 4px 로 못박은 줄이 그만큼 두꺼워진다.
    const body = toBodyWithTables([], [{
      afterParagraphNo: -1, cells: [['']], rowHeights: [4],
    }]);
    const table = Object.values(body.tableSource!)[0] as {
      tableRows: { tableCells: { margin: { top: { v: number } } }[] }[];
    };

    expect(table.tableRows[0].tableCells[0].margin.top.v).toBe(0);
  });

  it('칸 너비는 본문 폭을 고르게 나눈다', () => {
    const body = toBodyWithTables([], [{ afterParagraphNo: -1, cells: [['가', '나', '다']] }]);
    const table = Object.values(body.tableSource!)[0] as {
      tableColumns: { size: { width: { v: number } } }[];
      size: { width: { v: number } };
    };

    const total = table.tableColumns.reduce((sum, c) => sum + c.size.width.v, 0);
    expect(total).toBeCloseTo(table.size.width.v, 0);
  });

  it('행마다 칸 수가 다르면 빈 칸으로 채운다', () => {
    // 워드는 칸을 합쳐(gridSpan) 한 행을 짧게 만든다. 우리는 합치기를 모르니 칸
    // 수가 어긋나고, 그러면 Univer 가 표에서 레이아웃을 멈춰 뒤쪽 글이 통째로
    // 안 보인다(2026-08-12 실측: 8쪽 문서가 첫 표까지만 그려졌다).
    const body = toBodyWithTables([], [{
      afterParagraphNo: -1,
      cells: [['제목', '값'], ['가', '나', '다', '라']],
    }]);
    const table = Object.values(body.tableSource!)[0] as {
      tableRows: { tableCells: unknown[] }[];
      tableColumns: unknown[];
    };

    expect(table.tableColumns).toHaveLength(4);
    expect(table.tableRows[0].tableCells).toHaveLength(4);
    expect(table.tableRows[1].tableCells).toHaveLength(4);
    // 빈 칸도 글자 자리(문단)를 가진다.
    expect(body.paragraphs.length).toBeGreaterThanOrEqual(8);
  });

  it('표가 있어도 글자 서식을 살린다', () => {
    // 표가 있으면 서식을 통째로 버리고 있었다. 열기만 해도 굵게·크기·색이
    // 사라졌다(2026-08-12 실측: 워드 문서 114개 문단의 서식이 저장 때 날아갔다).
    const body = toBodyWithTables(
      [{ paragraphNo: 0, text: '앞말', style: null, runs: '[{"st":0,"ed":2,"ts":{"bl":1}}]' },
        { paragraphNo: 1, text: '뒷말', style: null, runs: '[{"st":0,"ed":2,"ts":{"fs":20}}]' }],
      [{ afterParagraphNo: 0, cells: [['칸']] }]);

    expect(body.textRuns).toHaveLength(2);
    expect(body.textRuns[0]).toMatchObject({ st: 0, ed: 2, ts: { bl: 1 } });
    // 표를 지나 뒤로 밀린 자리에 걸려야 한다.
    const at = body.dataStream.indexOf('뒷말');
    expect(body.textRuns[1]).toMatchObject({ st: at, ed: at + 2, ts: { fs: 20 } });
  });

  it('표가 있어도 문단 서식을 살린다', () => {
    const body = toBodyWithTables(
      [{ paragraphNo: 0, text: '가운데', style: '{"paragraphStyle":{"horizontalAlign":2}}', runs: null }],
      [{ afterParagraphNo: 0, cells: [['칸']] }]);

    expect(body.paragraphs[0]).toMatchObject({
      paragraphStyle: { horizontalAlign: 2 },
    });
  });

  it('합친 칸을 합친 채로 세운다', () => {
    // 합치기를 잃으면 표 머리 제목이 좁은 칸에 눌려 세로로 늘어진다
    // (2026-08-12 지적: 목차 표 머리 "Table of Contents").
    const body = toBodyWithTables([], [{
      afterParagraphNo: -1,
      cells: [['머리', ''], ['왼쪽', '오른쪽']],
      columnSpans: [[2, 0], [1, 1]],
      rowSpans: [[1, 1], [1, 1]],
    }]);
    const table = Object.values(body.tableSource!)[0] as {
      tableRows: { tableCells: { columnSpan?: number; rowSpan?: number }[] }[];
    };

    expect(table.tableRows[0].tableCells[0].columnSpan).toBe(2);
    // 가려진 칸은 0 이다. 자리는 지키되 그려지지 않는다.
    expect(table.tableRows[0].tableCells[1].columnSpan).toBe(0);
    expect(table.tableRows[1].tableCells[0].columnSpan).toBeUndefined();
  });

  it('세로로 합친 칸도 그대로 세운다', () => {
    const body = toBodyWithTables([], [{
      afterParagraphNo: -1,
      cells: [['위아래', '오른쪽'], ['', '아래']],
      columnSpans: [[1, 1], [0, 1]],
      rowSpans: [[2, 1], [0, 1]],
    }]);
    const table = Object.values(body.tableSource!)[0] as {
      tableRows: { tableCells: { rowSpan?: number; columnSpan?: number }[] }[];
    };

    expect(table.tableRows[0].tableCells[0].rowSpan).toBe(2);
    expect(table.tableRows[1].tableCells[0].rowSpan).toBe(0);
  });

  it('테두리 없는 표에는 선을 긋지 않는다', () => {
    // 줄 세우기 용도로 테두리를 지운 표가 흔하다. 격자로 그리면 원본과 딴판이다.
    const body = toBodyWithTables([], [{
      afterParagraphNo: -1,
      cells: [['가', '나']],
      hasBorders: false,
    }]);
    const table = Object.values(body.tableSource!)[0] as {
      tableRows: { tableCells: { borderTop?: { border: { s: number } } }[] }[];
    };

    expect(table.tableRows[0].tableCells[0].borderTop).toEqual({ border: { s: 0 } });
  });

  it('선이 있는 표에는 테두리를 적지 않는다', () => {
    // Univer 는 아무 말 없으면 선을 긋는다. 그 기본을 그대로 쓴다.
    const body = toBodyWithTables([], [{ afterParagraphNo: -1, cells: [['가']] }]);
    const table = Object.values(body.tableSource!)[0] as {
      tableRows: { tableCells: { borderTop?: unknown }[] }[];
    };

    expect(table.tableRows[0].tableCells[0].borderTop).toBeUndefined();
  });

  it('그림에 제 문단을 준다', () => {
    // 문단 안 마지막 글자로 넣으면 화면이 그리지 못했다(2026-08-13 실측).
    const body = toBodyWithTables(
      [{ paragraphNo: 0, text: '앞말', style: null, runs: null }],
      [{ afterParagraphNo: 0, cells: [['칸']] }],
      [{ afterParagraphNo: 0, url: '/api/images/7', width: 10, height: 10 }],
      'wo-doc-1');

    const anchor = body.dataStream.indexOf(String.fromCharCode(0x08));
    expect(anchor).toBeGreaterThan(body.dataStream.indexOf('앞말'));
    // 그림 자리 다음에는 문단 끝이 온다.
    expect(body.dataStream[anchor + 1]).toBe('\r');
    expect(body.paragraphs.some((one) => one.startIndex === anchor + 1)).toBe(true);
  });

  it('같은 자리의 그림을 표보다 먼저 놓는다', () => {
    // 원본은 로고가 표 위에 있다. 표를 먼저 적으면 로고가 표 아래로 내려간다
    // (2026-08-19 지적: ERP Security Requirements 첫 쪽).
    const body = toBodyWithTables(
      [{ paragraphNo: 0, text: '앞말', style: null, runs: null }],
      [{ afterParagraphNo: 0, cells: [['칸']] }],
      [{ afterParagraphNo: 0, url: '/api/images/7', width: 10, height: 10 }],
      'wo-doc-1');

    const anchor = body.dataStream.indexOf(String.fromCharCode(0x08));
    const table = body.dataStream.indexOf(String.fromCharCode(0x1a));
    expect(anchor).toBeLessThan(table);
  });

  it('칸 글자 서식을 살린다', () => {
    // 표 머리가 굵은 원본이 흔하다. 서식이 빠지면 머리와 본문이 같아 보인다.
    const body = toBodyWithTables([], [{
      afterParagraphNo: -1,
      cells: [['머리', '보통']],
      cellRuns: [['[{"st":0,"ed":2,"ts":{"bl":1}}]', null]],
    }]);

    const at = body.dataStream.indexOf('머리');
    expect(body.textRuns).toHaveLength(1);
    expect(body.textRuns[0]).toMatchObject({ st: at, ed: at + 2, ts: { bl: 1 } });
  });

  it('칸 바탕색을 그대로 쓴다', () => {
    // 머리 줄이 회색인 표가 흔하다. 바탕색이 빠지면 어느 줄이 머리인지 안 보인다.
    const body = toBodyWithTables([], [{
      afterParagraphNo: -1,
      cells: [['머리', '보통']],
      cellShades: [['#d9d9d9', null]],
    }]);
    const table = Object.values(body.tableSource!)[0] as {
      tableRows: { tableCells: { backgroundColor?: { rgb: string } }[] }[];
    };

    expect(table.tableRows[0].tableCells[0].backgroundColor).toEqual({ rgb: '#d9d9d9' });
    expect(table.tableRows[0].tableCells[1].backgroundColor).toBeUndefined();
  });

  it('그림에 제 문단을 준다', () => {
    // 문단 없이 자리만 넣으면 화면이 백지가 된다(2026-08-13 실측). 맞춤은 안
    // 건드린다 - 원본은 그림 문단에 맞춤을 안 적는다(2026-08-26 지적).
    const body = toBodyWithTables(
      [{ paragraphNo: 0, text: '앞글', style: null, runs: null }],
      [],
      [{ afterParagraphNo: 0, url: '/i.png', width: 100, height: 50 }],
      'wo-doc-1',
    );

    const anchor = body.dataStream.indexOf(String.fromCharCode(0x08));
    const own = body.paragraphs.find((one) => one.startIndex === anchor + 1) as {
      paragraphStyle?: { horizontalAlign?: number };
    };
    expect(own).toBeDefined();
    expect(own?.paragraphStyle?.horizontalAlign).toBeUndefined();
  });

  it('넓은 표를 본문 폭 안으로 줄인다', () => {
    // 표가 본문 폭과 똑같아지면 Univer 가 문서를 아예 못 그린다(2026-08-13 실측:
    // 720px 본문에 720px 표를 넣으니 화면이 백지가 됐다). 조금 좁게 잡는다.
    const body = toBodyWithTables([], [{
      afterParagraphNo: -1,
      cells: [['가', '나', '다']],
      columnWidths: [400, 400, 400],
    }], [], '', 600);
    const table = Object.values(body.tableSource!)[0] as {
      tableColumns: { size: { width: { v: number } } }[];
      size: { width: { v: number } };
    };

    const drawn = table.tableColumns.reduce((sum, c) => sum + c.size.width.v, 0);
    expect(drawn).toBeLessThan(600);
    expect(drawn).toBeGreaterThan(590);
  });

  it('본문에 들어가는 표는 원본 너비 그대로 둔다', () => {
    // 원본이 일부러 좁게 만든 표를 본문 폭까지 늘리면 원본과 딴판이 된다.
    const body = toBodyWithTables([], [{
      afterParagraphNo: -1,
      cells: [['가', '나']],
      columnWidths: [100, 100],
    }], [], '', 600);
    const table = Object.values(body.tableSource!)[0] as {
      tableColumns: { size: { width: { v: number } } }[];
    };

    expect(table.tableColumns.map((c) => c.size.width.v)).toEqual([100, 100]);
  });

  it('원본 칸 너비를 그대로 쓴다', () => {
    // 너비를 잃으면 모든 칸이 똑같이 나뉜다. 첫 칸이 좁고 둘째 칸이 넓은 원본과
    // 딴판이다(2026-08-12 지적).
    const body = toBodyWithTables([], [{
      afterParagraphNo: -1,
      cells: [['좁게', '넓게']],
      columnWidths: [100, 300],
    }]);
    const table = Object.values(body.tableSource!)[0] as {
      tableColumns: { size: { width: { v: number } } }[];
      size: { width: { v: number } };
    };

    const widths = table.tableColumns.map((c) => c.size.width.v);
    expect(widths).toEqual([100, 300]);
  });

  it('너비가 본문보다 넓으면 본문 폭에 맞춰 줄인다', () => {
    // 원본이 우리보다 여백이 좁으면 표가 종이 밖으로 나간다.
    const body = toBodyWithTables([], [{
      afterParagraphNo: -1,
      cells: [['가', '나']],
      columnWidths: [900, 300],
    }]);
    const table = Object.values(body.tableSource!)[0] as {
      tableColumns: { size: { width: { v: number } } }[];
      size: { width: { v: number } };
    };

    expect(table.size.width.v).toBeCloseTo(604, 0);
    expect(table.tableColumns[0].size.width.v)
      .toBeCloseTo(table.tableColumns[1].size.width.v * 3, 0);
  });

  it('칸 수보다 너비가 모자라면 고르게 나눈다', () => {
    // 칸을 합친(gridSpan) 표는 너비 수와 칸 수가 어긋난다. 반만 맞는 너비로
    // 그리면 표가 더 어긋나 보인다.
    const body = toBodyWithTables([], [{
      afterParagraphNo: -1,
      cells: [['가', '나', '다']],
      columnWidths: [100, 300],
    }]);
    const table = Object.values(body.tableSource!)[0] as {
      tableColumns: { size: { width: { v: number } } }[];
    };

    expect(new Set(table.tableColumns.map((c) => c.size.width.v)).size).toBe(1);
  });

  it('문단 사이에 표를 끼운다', () => {
    // 표 뒤에는 언제나 문단 자리가 온다. 없으면 Univer 가 표에서 레이아웃을 멈춰
    // 뒤쪽 글이 통째로 안 보인다(2026-08-12 실측: 8쪽 문서가 첫 표까지만 그려졌다).
    const body = toBodyWithTables(
      [{ paragraphNo: 0, text: '위', style: null, runs: null },
        { paragraphNo: 1, text: '아래', style: null, runs: null }],
      [{ afterParagraphNo: 0, cells: [['칸']] }]);

    expect(body.dataStream).toBe(
      '위' + CR + TABLE_START + ROW_START + CELL_START + '칸' + CR + LF + CELL_END
      + ROW_END + TABLE_END + CR + '아래' + CR + LF);
  });
});

describe('표 이름표', () => {
  it('앞가지를 붙여 이름이 겹치지 않게 한다', () => {
    // 머릿글 표와 본문 표가 같은 이름을 쓰면 한 쪽이 다른 쪽을 덮어 머릿글이
    // 안 그려진다(2026-08-13 실측).
    const body = toBodyWithTables([], [{ afterParagraphNo: -1, cells: [['가']] }],
      [], 'wo-doc-1', 600, 'header-');

    expect(Object.keys(body.tableSource!)).toEqual(['header-wo-table-1']);
  });
});

describe('쪽 나눔', () => {
  it('표가 있는 문서에서도 앞 문단이 쪽 나눔 글자로 끝난다', () => {
    const body = toBodyWithTables([
      { paragraphNo: 0, text: '표지', style: null, runs: null },
      { paragraphNo: 1, text: '목차', style: '{"pageBreakBefore":1}', runs: null },
    ], [{ afterParagraphNo: 1, cells: [['가']] }]);

    expect(body.dataStream).toContain(`표지${String.fromCharCode(12)}${String.fromCharCode(13)}목차`);
    expect(JSON.stringify(body.paragraphs)).not.toContain('pageBreakBefore');
  });
});

describe('표 칸 줄 높이', () => {
  it('칸 글도 글꼴 행간으로 잰 높이를 쓴다', () => {
    const body = toBodyWithTables([], [{
      afterParagraphNo: -1,
      cells: [['머리']],
      cellRuns: [[JSON.stringify([{ st: 0, ed: 2, ts: { fs: 9 } }])]],
    }], [], 'unit', 576, '', 'Century Gothic', () => 1.2);

    // 표 안 문단 하나. 9pt = 12px, 12 * 1.2 = 14.4px
    const styles = body.paragraphs
      .map((one) => one.paragraphStyle as Record<string, number> | undefined)
      .filter((one) => one?.lineSpacing != null);
    expect(styles[0]?.lineSpacing).toBeCloseTo(14.4, 1);
    expect(styles[0]?.spacingRule).toBe(2);
  });
});

describe('못박은 줄 높이', () => {
  it('칸 글줄도 그 높이를 넘지 않는다', () => {
    // 4px 짜리 색 막대가 칸 안 빈 줄(16px) 때문에 부풀었다(2026-08-25 지적).
    const body = toBodyWithTables(
      [{ paragraphNo: 0, text: '앞', style: null, runs: null }],
      [{ afterParagraphNo: 0, cells: [['']], rowHeights: [4], hasBorders: false }],
      [], 'wo-doc-1', 600);

    const table = Object.values(body.tableSource ?? {})[0] as {
      tableRows: { trHeight: { val: { v: number }; hRule: number } }[];
    };
    expect(table.tableRows[0].trHeight).toEqual({ val: { v: 4 }, hRule: 2 });

    const cellLine = (body.paragraphs ?? []).map((one) => one.paragraphStyle)
      .filter((style) => style != null)
      .find((style) => (style as { lineSpacing?: number }).lineSpacing != null) as
      { lineSpacing: number };
    // 화면(Univer 0.25.1)은 5 이하를 "배수" 로 읽는다. 6 이 그 경계를 넘는
    // 가장 낮은 높이다.
    expect(cellLine.lineSpacing).toBeLessThanOrEqual(6);
    // 글자 상자가 줄을 밀어 올리지 못하게 빈 칸 글자도 1pt 로 줄인다.
    const small = (body.textRuns ?? []).some((run) =>
      (run as { ts?: { fs?: number } }).ts?.fs === 1);
    expect(small).toBe(true);
  });
});

describe('칸 안 줄 나눔', () => {
  it('칸 글의 줄 나눔마다 문단을 만든다', () => {
    // 박스 안 문단을 한 줄로 이으면 표가 원본보다 낮아져 뒷글이 앞 쪽으로
    // 올라온다(2026-08-25 지적: 표가 다음 쪽으로 넘어간다).
    const body = toBodyWithTables(
      [{ paragraphNo: 0, text: '앞', style: null, runs: null }],
      [{ afterParagraphNo: 0, cells: [[`첫 줄${String.fromCharCode(10)}둘째 줄`]] }],
      [], 'wo-doc-1', 600);

    expect(body.dataStream).toContain('첫 줄');
    expect(body.dataStream).toContain('둘째 줄');
    // 줄 나눔 글자는 문단 마감으로 바뀐다 - 그대로 두면 화면이 네모로 그린다.
    expect(body.dataStream).not.toContain(`첫 줄${String.fromCharCode(10)}둘째`);
    const between = body.dataStream.indexOf('첫 줄') + 3;
    expect(body.dataStream.slice(between, between + 1)).toBe(String.fromCharCode(13));
  });
});

describe('칸 안 문단 간격', () => {
  it('마지막 줄만 빼고 간격을 준다', () => {
    // 간격을 흘리면 박스가 원본보다 낮아져 뒷글이 앞 쪽으로 올라온다.
    const body = toBodyWithTables(
      [{ paragraphNo: 0, text: '앞', style: null, runs: null }],
      [{ afterParagraphNo: 0, cells: [[`첫 줄${String.fromCharCode(10)}둘째 줄`]],
        cellSpaces: [[4]] }],
      [], 'wo-doc-1', 600);

    const spaced = (body.paragraphs ?? [])
      .map((one) => (one.paragraphStyle ?? {}) as { spaceBelow?: { v: number } })
      .filter((style) => style.spaceBelow?.v === 4);
    expect(spaced).toHaveLength(1);
  });
});

describe('표 뒤 쪽 나눔', () => {
  it('쪽 나눔을 표 뒤에 둔다', () => {
    // 표 앞 문단 끝에 두면 표까지 다음 쪽으로 밀리고, 저장하면 표 안에 든
    // 쪽 나눔이 통째로 사라진다(2026-08-25 실측: 쪽 나눔 6개가 1개로).
    const body = toBodyWithTables(
      [{ paragraphNo: 0, text: '앞', style: null, runs: null },
        { paragraphNo: 1, text: '새 쪽',
          style: JSON.stringify({ pageBreakBefore: 1 }), runs: null }],
      [{ afterParagraphNo: 0, cells: [['칸']] }],
      [], 'wo-doc-1', 600);

    const breakAt = body.dataStream.indexOf(String.fromCharCode(12));
    const tableEnd = body.dataStream.indexOf(String.fromCharCode(15));
    expect(breakAt).toBeGreaterThan(tableEnd);
  });
});

describe('떠 있는 그림', () => {
  it('글 흐름 밖 자리에 그대로 띄운다', () => {
    // 표지 배경 그림을 흐름에 넣으면 첫 쪽을 통째로 차지한다(2026-08-25 지적).
    const body = toBodyWithTables(
      [{ paragraphNo: 0, text: '표지', style: null, runs: null }],
      [],
      [{ afterParagraphNo: -1, url: 'data:image/png;base64,AA', width: 820, height: 1140,
        inline: true, floating: true, behind: true, left: -22, top: -132,
        fromH: 0, fromV: 1 }],
      'wo-doc-1', 600);

    const drawing = Object.values(body.drawings ?? {})[0] as {
      layoutType?: number; behindDoc?: number;
      docTransform?: {
        positionH?: { posOffset?: number; relativeFrom?: number };
        positionV?: { posOffset?: number; relativeFrom?: number };
      };
    };
    expect(drawing.layoutType).toBe(1);
    expect(drawing.behindDoc).toBe(1);
    expect(drawing.docTransform?.positionH?.posOffset).toBe(-22);
    expect(drawing.docTransform?.positionV?.posOffset).toBe(-132);
    // 워드는 세로를 문단 위에서 잰다. 쪽 위에서 재면 그림이 종이 밖으로 나간다
    // (2026-08-25 실측: 표지 배경이 화면에서 사라졌다).
    expect(drawing.docTransform?.positionV?.relativeFrom).toBe(1);
    expect(drawing.docTransform?.positionH?.relativeFrom).toBe(0);
  });
});

describe('종이보다 큰 떠 있는 그림', () => {
  it('종이 안에 들어오게 줄인다', () => {
    // 워드는 종이 밖을 잘라 그리지만 화면은 그대로 그려 그림이 종이 밖으로
    // 튀어나온다(2026-08-25 지적: 첫 쪽 이미지가 너무 크다).
    const [fitted] = fitFloating(
      [{ afterParagraphNo: 0, url: 'x', width: 820, height: 1140,
        floating: true, left: -22, top: -132 }],
      794, 1123, 96, 87);

    expect(fitted.width).toBeLessThanOrEqual(794);
    expect(fitted.height).toBeLessThanOrEqual(1123);
    // 비율은 그대로다.
    expect(fitted.width / fitted.height).toBeCloseTo(820 / 1140, 3);
    // 화면은 그림 자리를 본문 여백 안에서 잰다. 여백만큼 당겨야 종이 왼쪽 위에
    // 붙는다(2026-08-25 실측: 여백 96px 만큼 오른쪽으로 밀렸다).
    expect(fitted.left).toBe(-96);
    expect(fitted.top).toBe(-87);
  });

  it('종이에 들어오는 그림은 그대로 둔다', () => {
    const [same] = fitFloating(
      [{ afterParagraphNo: 0, url: 'x', width: 200, height: 100,
        floating: true, left: 30, top: 40 }],
      794, 1123, 96, 87);

    expect(same.width).toBe(200);
    expect(same.left).toBe(30);
  });
});

describe('새 쪽을 여는 표', () => {
  it('표 앞에 쪽 나눔을 둔다', () => {
    // 표지 다음 목차가 표로 시작한다. 표시를 흘리면 목차 표가 표지 쪽에 남는다
    // (2026-08-25 지적).
    const body = toBodyWithTables(
      [{ paragraphNo: 0, text: '표지', style: null, runs: null },
        { paragraphNo: 1, text: '머리말', style: null, runs: null }],
      [{ afterParagraphNo: 0, cells: [['목차']], opensNewPage: true }],
      [], 'wo-doc-1', 600);

    const breakAt = body.dataStream.indexOf(String.fromCharCode(12));
    const tableAt = body.dataStream.indexOf(String.fromCharCode(26));
    expect(breakAt).toBeGreaterThan(-1);
    expect(breakAt).toBeLessThan(tableAt);
  });
});

describe('그림 문단 맞춤', () => {
  it('그림은 왼쪽에 세운다', () => {
    // 원본은 그림 문단에 맞춤을 안 적는다(= 왼쪽). 가운데로 세우면 원본과
    // 다른 자리에 앉는다(2026-08-26 지적: 이미지가 좌측 정렬돼야 한다).
    const body = toBodyWithTables(
      [{ paragraphNo: 0, text: '앞', style: null, runs: null }],
      [],
      [{ afterParagraphNo: 0, url: 'data:image/png;base64,AA', width: 100, height: 50 }],
      'wo-doc-1', 600);

    const middles = (body.paragraphs ?? [])
      .map((one) => (one.paragraphStyle ?? {}) as { horizontalAlign?: number })
      .filter((style) => style.horizontalAlign === 2);
    expect(middles).toHaveLength(0);
  });
});

describe('그은 변만 있는 표', () => {
  it('아래만 그으면 위·좌·우는 안 긋는다', () => {
    // 제목 표는 아래 주황선 하나만 있다. 격자로 그리면 원본과 딴판이 된다
    // (2026-08-26 지적: 목차·제목 표 테두리).
    const body = toBodyWithTables(
      [{ paragraphNo: 0, text: '앞', style: null, runs: null }],
      [{ afterParagraphNo: 0, cells: [['목차']],
        borders: JSON.stringify({ bottom: { size: 3, color: '#fc5e20' } }) }],
      [], 'wo-doc-1', 600);

    const table = Object.values(body.tableSource ?? {})[0] as {
      tableRows: { tableCells: {
        borderTop?: { color: { rgb: string }; width: { v: number } };
        borderBottom?: { color: { rgb: string }; width: { v: number } };
      }[] }[];
    };
    const cell = table.tableRows[0].tableCells[0];
    // 화면은 굵기 0 이거나 색이 transparent 인 변을 안 긋는다.
    expect(cell.borderTop?.width.v).toBe(0);
    expect(cell.borderBottom?.width.v).toBeGreaterThan(0);
    expect(cell.borderBottom?.color.rgb).toBe('#fc5e20');
  });
});

describe('못박지 않은 줄 높이', () => {
  it('글이 길면 늘어나게 둔다', () => {
    // 한글 줄 높이는 "이만큼 찼다" 는 뜻이다. 못박으면 글이 잘린다(2026-08-26).
    const body = toBodyWithTables(
      [{ paragraphNo: 0, text: '앞', style: null, runs: null }],
      [{ afterParagraphNo: 0, cells: [['칸']], rowHeights: [32],
        exactRowHeights: false }],
      [], 'wo-doc-1', 600);

    const table = Object.values(body.tableSource ?? {})[0] as {
      tableRows: { trHeight: { val: { v: number }; hRule: number } }[];
    };
    // hRule 1 이 "적어도" 다.
    expect(table.tableRows[0].trHeight).toEqual({ val: { v: 32 }, hRule: 1 });
  });
});

describe('칸 안쪽 여백', () => {
  it('원본 여백을 그대로 쓴다', () => {
    // 화면 기본(좌우 10px)은 한글 문서보다 넉넉해 표가 원본보다 높아진다
    // (2026-08-26 지적).
    const body = toBodyWithTables(
      [{ paragraphNo: 0, text: '앞', style: null, runs: null }],
      [{ afterParagraphNo: 0, cells: [['칸']],
        cellMargin: { left: 2, right: 2, top: 1, bottom: 1 } }],
      [], 'wo-doc-1', 600);

    const table = Object.values(body.tableSource ?? {})[0] as {
      tableRows: { tableCells: { margin: {
        start: { v: number }; top: { v: number } } }[] }[];
    };
    const margin = table.tableRows[0].tableCells[0].margin;
    expect(margin.start.v).toBe(2);
    expect(margin.top.v).toBe(1);
  });
});
