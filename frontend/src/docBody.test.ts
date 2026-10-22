import { describe, expect, it } from 'vitest';

import { toParagraphs, toBody, type DocParagraph } from './docBody';

describe('toParagraphs', () => {
  it('한 문단을 자른다', () => {
    // dataStream 끝의 \r 은 문단 끝, \n 은 구역 끝이다. 둘 다 글자가 아니다.
    const rows = toParagraphs({
      dataStream: '안녕\r\n',
      paragraphs: [{ startIndex: 2 }],
      textRuns: [],
    });

    expect(rows).toEqual([{ paragraphNo: 0, text: '안녕', style: null, runs: null }]);
  });

  it('여러 문단을 차례대로 자른다', () => {
    const rows = toParagraphs({
      dataStream: '첫째\r둘째\r\n',
      paragraphs: [{ startIndex: 2 }, { startIndex: 5 }],
      textRuns: [],
    });

    expect(rows.map((row) => row.text)).toEqual(['첫째', '둘째']);
    expect(rows.map((row) => row.paragraphNo)).toEqual([0, 1]);
  });

  it('문단 서식을 함께 담는다', () => {
    const rows = toParagraphs({
      dataStream: '제목\r\n',
      paragraphs: [{ startIndex: 2, paragraphStyle: { headingId: 'h1' } }],
      textRuns: [],
    });

    expect(JSON.parse(rows[0].style!)).toEqual({ paragraphStyle: { headingId: 'h1' } });
  });

  it('글자 서식을 문단 안 자리로 바꿔 담는다', () => {
    // 서식은 문서 전체 오프셋으로 온다. 문단마다 따로 담으려면 그 문단 시작을
    // 0 으로 다시 잡아야 한다 - 아니면 둘째 문단부터 엉뚱한 자리가 굵어진다.
    const rows = toParagraphs({
      dataStream: '첫째\r둘째\r\n',
      paragraphs: [{ startIndex: 2 }, { startIndex: 5 }],
      textRuns: [{ st: 3, ed: 5, ts: { bl: 1 } }],
    });

    expect(rows[0].runs).toBeNull();
    expect(JSON.parse(rows[1].runs!)).toEqual([{ st: 0, ed: 2, ts: { bl: 1 } }]);
  });

  it('문단 경계를 넘는 서식은 잘라 나눈다', () => {
    const rows = toParagraphs({
      dataStream: '첫째\r둘째\r\n',
      paragraphs: [{ startIndex: 2 }, { startIndex: 5 }],
      textRuns: [{ st: 0, ed: 5, ts: { bl: 1 } }],
    });

    expect(JSON.parse(rows[0].runs!)).toEqual([{ st: 0, ed: 2, ts: { bl: 1 } }]);
    expect(JSON.parse(rows[1].runs!)).toEqual([{ st: 0, ed: 2, ts: { bl: 1 } }]);
  });

  it('표 안 글은 문단으로 담지 않는다', () => {
    // 표는 문서 한 벌(스냅샷)이 따로 담는다. 칸 글까지 문단으로 저장하면 문단
    // 수가 부풀고(2026-08-12 실측: 161 -> 203) 내려받은 docx 에 표 글이 두 번
    // 나온다.
    const TABLE_START = String.fromCharCode(0x1a);
    const ROW_START = String.fromCharCode(0x1b);
    const CELL_START = String.fromCharCode(0x1c);
    const CELL_END = String.fromCharCode(0x1d);
    const ROW_END = String.fromCharCode(0x0e);
    const TABLE_END = String.fromCharCode(0x0f);
    const stream = '위\r' + TABLE_START + ROW_START + CELL_START + '칸'
      + '\r\n' + CELL_END + ROW_END + TABLE_END + '\r아래\r\n';

    const rows = toParagraphs({
      dataStream: stream,
      paragraphs: [
        { startIndex: 1 },
        { startIndex: stream.indexOf('칸') + 1 },
        { startIndex: stream.indexOf(TABLE_END) + 1 },
        { startIndex: stream.indexOf('아래') + 2 },
      ],
      textRuns: [],
    });

    expect(rows.map((row) => row.text)).toEqual(['위', '아래']);
    expect(rows.map((row) => row.paragraphNo)).toEqual([0, 1]);
  });

  it('빈 문서는 빈 문단 하나다', () => {
    const rows = toParagraphs({ dataStream: '\r\n', paragraphs: [{ startIndex: 0 }], textRuns: [] });

    expect(rows).toEqual([{ paragraphNo: 0, text: '', style: null, runs: null }]);
  });
});

describe('toBody', () => {
  it('되붙이면 원래대로다', () => {
    const body = {
      dataStream: '첫째\r둘째\r\n',
      paragraphs: [{ startIndex: 2 }, { startIndex: 5 }],
      textRuns: [{ st: 3, ed: 5, ts: { bl: 1 } }],
    };

    // 서식이 없던 문단에는 기본 줄 간격이 붙는다. 나머지는 그대로다.
    const back = toBody(toParagraphs(body));

    expect(back.dataStream).toBe(body.dataStream);
    expect(back.textRuns).toEqual(body.textRuns);
    expect(back.paragraphs.map((one) => one.startIndex)).toEqual([2, 5]);
  });

  it('문단 서식도 되살린다', () => {
    const rows: DocParagraph[] = [
      { paragraphNo: 0, text: '제목', style: '{"paragraphStyle":{"headingId":"h1"}}', runs: null },
    ];

    expect(toBody(rows)).toEqual({
      dataStream: '제목\r\n',
      paragraphs: [{ startIndex: 2, paragraphStyle: { headingId: 'h1' } }],
      textRuns: [],
      sectionBreaks: [{ startIndex: 3 }],
      tables: [],
    });
  });

  it('구역 끝 표시를 함께 준다', () => {
    // 이것이 없으면 Univer 가 문단을 새로 넣을 자리를 못 찾는다 - 엔터를 쳐도
    // "Cannot insert into missing item" 만 나고 줄이 안 바뀐다(2026-08-11 실측).
    const body = toBody([{ paragraphNo: 0, text: '한 줄', style: null, runs: null }]);

    expect(body.sectionBreaks).toEqual([{ startIndex: 4 }]);
    expect(body.dataStream[4]).toBe('\n');
  });

  it('표를 넣을 자리를 함께 준다', () => {
    // 이 배열이 없으면 표를 넣어도 body 에 자리가 안 잡혀 화면에 그려지지 않는다.
    // 표 정의(tableSource)만 남고 어디에 놓인 표인지는 사라졌다(2026-08-11 실측).
    expect(toBody([]).tables).toEqual([]);
  });

  it('저장된 것이 없으면 빈 문단 하나를 준다', () => {
    // 빈 본문을 주면 Univer 가 잠긴 것으로 본다. 글자를 넣을 자리가 있어야 한다.
    const body = toBody([]);

    expect(body.dataStream).toBe('\r\n');
    expect(body.paragraphs).toHaveLength(1);
    expect(body.paragraphs[0].startIndex).toBe(0);
  });
});

describe('기본 줄 간격', () => {
  it('서식이 없는 문단에 줄 간격을 넣는다', () => {
    // 화면 기본은 글자 상자 높이(11pt 에서 21px)라 엔터를 치면 줄이 뚝 떨어져
    // 보인다. 구글은 1.15줄(약 17px)이다(2026-08-19 지적).
    const body = toBody([{ paragraphNo: 0, text: '가', style: null, runs: null }]);
    const first = body.paragraphs[0] as { paragraphStyle?: { lineSpacing?: number } };

    expect(first.paragraphStyle?.lineSpacing).toBeCloseTo(16.9, 1);
  });

  it('서식이 있는 문단은 그대로 둔다', () => {
    const style = JSON.stringify({ paragraphStyle: { lineSpacing: 30, spacingRule: 2 } });
    const body = toBody([{ paragraphNo: 0, text: '가', style, runs: null }]);
    const first = body.paragraphs[0] as { paragraphStyle?: { lineSpacing?: number } };

    expect(first.paragraphStyle?.lineSpacing).toBe(30);
  });
});

describe('쪽 나눔', () => {
  const CR = String.fromCharCode(13);
  const LF = String.fromCharCode(10);
  const FF = String.fromCharCode(12);

  it('쪽 나눔은 새 쪽 문단 앞에 붙는다', () => {
    // 앞 문단에 붙이면 그 문단의 끝 표시가 새 쪽 첫 줄로 남아 빈 줄이 보인다
    // (2026-08-25 지적). Univer 가 문단 앞 쪽 나눔도 읽도록 빌드 때 기운다.
    const body = toBody([
      { paragraphNo: 0, text: '표지', style: null, runs: null },
      { paragraphNo: 1, text: '목차', style: '{"pageBreakBefore":1}', runs: null },
    ]);

    expect(body.dataStream).toBe(`표지${CR}${FF}목차${CR}${LF}`);
  });

  it('첫 문단은 쪽을 넘기지 않는다', () => {
    // 앞에 아무것도 없는데 넘기면 빈 쪽이 하나 생긴다.
    const body = toBody([
      { paragraphNo: 0, text: '첫 줄', style: '{"pageBreakBefore":1}', runs: null },
    ]);

    expect(body.dataStream).not.toContain(FF);
  });

  it('쪽 나눔 표시는 문단 서식에 남기지 않는다', () => {
    // Univer 문단 서식에 없는 값이다. 그대로 실으면 편집 화면이 저장할 때 서버로
    // 되돌려 보낸다.
    const body = toBody([
      { paragraphNo: 0, text: '가', style: null, runs: null },
      { paragraphNo: 1, text: '나', style: '{"pageBreakBefore":1,"horizontalAlign":2}',
        runs: null },
    ]);

    expect(JSON.stringify(body.paragraphs)).not.toContain('pageBreakBefore');
    expect(JSON.stringify(body.paragraphs[1])).toContain('horizontalAlign');
  });
});

describe('쪽 나눔 왕복', () => {
  const CR = String.fromCharCode(13);
  const LF = String.fromCharCode(10);
  const FF = String.fromCharCode(12);

  it('쪽 나눔 글자를 다음 문단의 표시로 되돌린다', () => {
    // 가져온 문서를 편집기에서 열고 저장하면 이 길을 탄다. 표시를 못 되돌리면
    // 한 번 저장하는 것만으로 원본의 쪽 나눔이 사라진다.
    const rows = toParagraphs({
      dataStream: `표지${FF}${CR}목차${CR}${LF}`,
      paragraphs: [{ startIndex: 3 }, { startIndex: 7 }],
      textRuns: [],
    });

    expect(rows.map((row) => row.text)).toEqual(['표지', '목차']);
    expect(rows[0].style).toBeNull();
    expect(JSON.parse(rows[1].style!)).toEqual({ pageBreakBefore: 1 });
  });

  it('본디 서식이 있으면 표시만 얹는다', () => {
    const rows = toParagraphs({
      dataStream: `가${FF}${CR}나${CR}${LF}`,
      paragraphs: [{ startIndex: 2 }, { startIndex: 5, horizontalAlign: 2 }],
      textRuns: [],
    });

    expect(JSON.parse(rows[1].style!)).toEqual({ horizontalAlign: 2, pageBreakBefore: 1 });
  });

  it('쪽 나눔이 없으면 표시도 없다', () => {
    const rows = toParagraphs({
      dataStream: `가${CR}나${CR}${LF}`,
      paragraphs: [{ startIndex: 1 }, { startIndex: 3 }],
      textRuns: [],
    });

    expect(rows.every((row) => row.style == null)).toBe(true);
  });
});

describe('그림만 든 문단', () => {
  const RETURN = String.fromCharCode(13);
  const DRAWING = String.fromCharCode(8);

  it('저장할 때 뺀다', () => {
    // 화면은 그림에 제 문단을 준다(문단 없이 자리만 넣으면 백지가 된다). 그것을
    // 그대로 저장하면 문단이 그림 수만큼 늘어 쪽 수가 어긋난다(2026-08-24 지적).
    const rows = toParagraphs({
      dataStream: `앞글${RETURN}${DRAWING}${RETURN}뒷글${RETURN}`,
      paragraphs: [
        { startIndex: 2 },
        { startIndex: 4, paragraphStyle: { horizontalAlign: 2 } },
        { startIndex: 7 },
      ],
      textRuns: [],
    });

    expect(rows.map((row) => row.text)).toEqual(['앞글', '뒷글']);
  });

  it('그림과 글이 같이 있으면 남긴다', () => {
    const rows = toParagraphs({
      dataStream: `${DRAWING}그림 설명${RETURN}`,
      paragraphs: [{ startIndex: 6 }],
      textRuns: [],
    });

    expect(rows.map((row) => row.text)).toEqual(['그림 설명']);
  });
});

describe('문서 기본 글꼴', () => {
  it('글꼴을 안 적은 조각에 기본 글꼴을 준다', () => {
    const body = toBody([
      { paragraphNo: 0, text: '본문', style: null,
        runs: JSON.stringify([{ st: 0, ed: 2, ts: { fs: 11 } }]) },
    ], 'Century Gothic');

    expect((body.textRuns[0].ts as Record<string, unknown>)?.ff).toBe('Century Gothic');
  });
});

describe('쪽 나눔 줄', () => {
  const BREAK = JSON.stringify({ pageBreakBefore: 1 });


  it('되읽으면 쪽 나눔 문단은 사라지고 표시만 남는다', () => {
    const body = toBody([
      { paragraphNo: 0, text: '앞 글', style: null, runs: null },
      { paragraphNo: 1, text: '새 쪽', style: BREAK, runs: null },
    ]);

    const rows = toParagraphs(body);

    expect(rows.map((row) => row.text)).toEqual(['앞 글', '새 쪽']);
    expect(rows[1].style).toContain('pageBreakBefore');
  });
});

describe('표 안 여러 문단', () => {
  it('칸 안 두 번째 문단도 본문으로 새지 않는다', () => {
    // 칸 안 줄 나눔마다 문단을 만들면서 그 문단이 본문 문단으로 저장됐다
    // (2026-08-25 실측: 표 밖 문단 42개가 62개로 늘고 내려받은 문서에 표 글이
    // 두 번 나왔다).
    const T = (code: number) => String.fromCharCode(code);
    const body = {
      dataStream: `앞${T(13)}${T(26)}${T(27)}${T(28)}첫 줄${T(13)}둘째 줄${T(13)}`
        + `${T(10)}${T(29)}${T(14)}${T(15)}뒤${T(13)}${T(10)}`,
      paragraphs: [
        { startIndex: 1 },
        { startIndex: 8 },
        { startIndex: 13 },
        { startIndex: 19 },
      ],
      textRuns: [],
    };

    const rows = toParagraphs(body as never);

    // 칸 안 두 줄은 둘 다 빠지고 본문 문단만 남는다.
    expect(rows.map((one) => one.text)).not.toContain('둘째 줄');
    expect(rows.map((one) => one.text)).toContain('앞');
  });
});

describe('떠 있는 그림이 걸린 문단', () => {
  it('제 서식을 가진 문단은 그림만 들었어도 지킨다', () => {
    // 표지는 글이 도형 안에 있어 문단 글자가 빈다. 그 문단을 버리면 표지 쪽이
    // 사라지고 목차가 첫 쪽으로 올라와 배경 그림에 덮인다(2026-08-25 지적).
    const T = (code: number) => String.fromCharCode(code);
    const body = {
      dataStream: `${T(8)}${T(13)}목차${T(13)}${T(10)}`,
      paragraphs: [
        { startIndex: 1, paragraphStyle: { spaceBelow: { v: 10 } } },
        { startIndex: 4 },
      ],
      textRuns: [],
    };

    const rows = toParagraphs(body as never);

    expect(rows.map((one) => one.text)).toEqual(['', '목차']);
  });

  it('화면이 그림에 만들어 준 문단은 버린다', () => {
    // 가운데 맞춤만 든 빈 문단은 화면이 그림 때문에 만든 것이다.
    const T = (code: number) => String.fromCharCode(code);
    const body = {
      dataStream: `${T(8)}${T(13)}목차${T(13)}${T(10)}`,
      paragraphs: [
        { startIndex: 1, paragraphStyle: { horizontalAlign: 2 } },
        { startIndex: 4 },
      ],
      textRuns: [],
    };

    const rows = toParagraphs(body as never);

    expect(rows.map((one) => one.text)).toEqual(['목차']);
  });
});

describe('쪽 번호 필드', () => {
  it('필드 구간을 custom range 로 함께 담는다', () => {
    // 머릿글·바닥글의 "Page 1 of 9" 가 쪽마다 바뀌려면 화면이 그 구간을 알아야
    // 한다 - 글자만 담으면 모든 쪽에 같은 숫자가 찍힌다(2026-10-16 실측).
    const body = toBody([{ paragraphNo: 0, text: 'Page 1 of 9', style: null,
      runs: JSON.stringify([{ st: 5, ed: 6, fld: 'PAGE' }]) }]) as
      { dataStream: string; customRanges?: Array<{ properties: { fieldType: string } }> };

    expect(body.customRanges).toHaveLength(1);
    expect(body.customRanges?.[0].properties.fieldType).toBe('PAGE');
    expect(body.dataStream).toContain('1');
  });
});
