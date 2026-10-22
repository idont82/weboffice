import { describe, expect, it } from 'vitest';

import { edgeBody, edgeLines } from './docEdge';

/** 문단 마감과 그림 자리 글자. 소스에 그대로 적으면 파일이 깨진다. */
const MARK = String.fromCharCode(13);
const ANCHOR = String.fromCharCode(8);
const BREAK = String.fromCharCode(10);

describe('edgeLines', () => {
  it('표 한 줄을 글 한 줄로 편다', () => {
    // 머릿글 표는 화면이 그리지 못한다. 글줄로 펴서 적는다.
    const lines = edgeLines({
      paragraphs: [],
      tables: [{ afterParagraphNo: -1, cells: [['Title: ', 'ERP', '', '']] }],
    });

    expect(lines).toHaveLength(1);
    expect(lines[0].text).toBe('Title:  ERP');
  });

  it('칸 글자 서식을 옮긴 자리로 함께 옮긴다', () => {
    // 원본 머릿글은 9pt 다. 서식을 잃으면 문서 기본 크기로 커진다(2026-08-13 지적).
    const lines = edgeLines({
      paragraphs: [],
      tables: [{
        afterParagraphNo: -1,
        cells: [['Title: ', 'ERP']],
        cellRuns: [[
          '[{"st":0,"ed":7,"ts":{"bl":1,"fs":9}}]',
          '[{"st":0,"ed":3,"ts":{"fs":9}}]',
        ]],
      }],
    });

    const runs = JSON.parse(lines[0].runs ?? '[]') as
      { st: number; ed: number; ts: { fs: number; bl?: number } }[];
    expect(lines[0].text).toBe('Title:  ERP');
    expect(runs).toHaveLength(2);
    expect(runs[0]).toEqual({ st: 0, ed: 7, ts: { bl: 1, fs: 9 } });
    // 둘째 칸은 앞 칸(7자)과 사이 한 칸만큼 밀린다.
    expect(runs[1]).toEqual({ st: 8, ed: 11, ts: { fs: 9 } });
  });

  it('빈 줄은 버린다', () => {
    const lines = edgeLines({
      paragraphs: [],
      tables: [{ afterParagraphNo: -1, cells: [['', ''], ['글', '']] }],
    });

    expect(lines.map((one) => one.text)).toEqual(['글']);
  });
});

describe('edgeBody', () => {
  it('머릿글 그림을 얼개에 넣는다', () => {
    // 머릿글 로고가 화면에 안 그려졌다(2026-08-25 지적: 편집 모드).
    const body = edgeBody({
      paragraphs: [{ paragraphNo: 0, text: '', style: null, runs: null }],
      tables: [{ afterParagraphNo: -1, cells: [['SecurityHub Guide']] }],
      pictures: [],
    }, 'wo-doc-1-header', 600, null, [
      { afterParagraphNo: -1, url: 'data:image/png;base64,AA', width: 105.6, height: 30 },
    ]);

    expect(Object.keys(body.drawings ?? {})).toHaveLength(1);
    expect(body.drawingsOrder).toHaveLength(1);
    expect(body.dataStream).toContain(ANCHOR);
    // 그림이 제 문단을 차지하면 머릿글이 한 줄 높아져 본문이 밀린다
    // (2026-08-25 실측: 24쪽이 26쪽). 표에서 편 글줄과 같은 줄에 둔다.
    expect(body.dataStream).toBe(MARK + ANCHOR + 'SecurityHub Guide' + MARK + BREAK);
  });

  it('그림이 없으면 얼개도 비어 있다', () => {
    const body = edgeBody({
      paragraphs: [{ paragraphNo: 0, text: '머릿글', style: null, runs: null }],
      tables: [],
      pictures: [],
    }, 'wo-doc-1-header', 600, null, []);

    expect(Object.keys(body.drawings ?? {})).toHaveLength(0);
    expect(body.dataStream).toContain('머릿글');
  });
});

describe('머릿글 자리 맞춤', () => {
  it('칸 맞춤이 오른쪽이면 편 줄도 오른쪽이다', () => {
    // 원본 머릿글은 로고가 왼쪽, 글이 오른쪽 칸이다(2026-08-25 지적).
    const lines = edgeLines({
      paragraphs: [],
      tables: [{
        afterParagraphNo: -1,
        cells: [['', 'SecurityHub Application Guide']],
        cellAligns: [[1, 3]],
      }],
    });

    expect(lines).toHaveLength(1);
    expect(JSON.parse(lines[0].style ?? '{}').paragraphStyle.horizontalAlign).toBe(3);
  });

  it('쪽마다 되풀이되는 그림으로 싣는다', () => {
    // 머릿글은 쪽마다 다시 그린다. 이 표시가 없으면 화면이 첫 쪽에만 그린다
    // (Univer 0.25.1 실측: 2쪽에만 로고가 보였다).
    const body = edgeBody({
      paragraphs: [{ paragraphNo: 0, text: '', style: null, runs: null }],
      tables: [{ afterParagraphNo: -1, cells: [['머릿글']] }],
      pictures: [],
    }, 'wo-doc-1-header', 600, null, [
      { afterParagraphNo: -1, url: 'data:image/png;base64,AA', width: 105.6, height: 30 },
    ]);

    const drawing = Object.values(body.drawings ?? {})[0] as { isMultiTransform?: number };
    expect(drawing.isMultiTransform).toBe(1);
  });

  it('로고는 글 흐름 밖 왼쪽에 띄운다', () => {
    // 원본은 로고가 왼쪽 칸, 글이 오른쪽 칸이다. 글줄에 끼우면 로고가 글을 따라
    // 오른쪽으로 끌려간다(2026-08-25 지적).
    const body = edgeBody({
      paragraphs: [{ paragraphNo: 0, text: '', style: null, runs: null }],
      tables: [{ afterParagraphNo: -1, cells: [['머릿글']] }],
      pictures: [],
    }, 'wo-doc-1-header', 600, null, [
      { afterParagraphNo: -1, url: 'data:image/png;base64,AA', width: 105.6, height: 30 },
    ]);

    const drawing = Object.values(body.drawings ?? {})[0] as {
      layoutType?: number;
      docTransform?: { positionH?: { posOffset?: number }; positionV?: { posOffset?: number } };
    };
    expect(drawing.layoutType).toBe(1);
    expect(drawing.docTransform?.positionH?.posOffset).toBe(0);
  });
});
