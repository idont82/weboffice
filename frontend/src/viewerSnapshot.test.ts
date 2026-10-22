import { describe, expect, it } from 'vitest';

import {
  imageDataUrl, isEditCommand, snapshotMeta, toDeckView, toDocImages, toSheetDrawingResource,
  toSheetGrid,
} from './viewerSnapshot';

/** 시험에서 쓰는 머리 묶음. 서버가 붙이는 것과 같은 이름이다. */
function headers(pairs: Record<string, string>): Headers {
  return new Headers(pairs);
}

describe('snapshotMeta', () => {
  it('머리 넷을 읽는다', () => {
    const meta = snapshotMeta(headers({
      'X-Wo-Doc-Type': 'SHEET',
      'X-Wo-Encrypted': 'Y',
      'X-Wo-File-Name': '%EB%B3%B4%EA%B3%A0%EC%84%9C.xlsx',
      'X-Wo-Viewer-Name': '%ED%99%8D%EA%B8%B8%EB%8F%99%20%EC%B1%85%EC%9E%84',
    }));

    expect(meta.docType).toBe('SHEET');
    expect(meta.encrypted).toBe(true);
    // 머리에 한글을 그대로 넣으면 깨진다. 서버가 URL 로 싸 보내고 여기서 푼다.
    expect(meta.fileName).toBe('보고서.xlsx');
    expect(meta.viewerName).toBe('홍길동 책임');
  });

  it('열람자 이름이 없으면 null 이다', () => {
    const meta = snapshotMeta(headers({
      'X-Wo-Doc-Type': 'DOC',
      'X-Wo-Encrypted': 'N',
      'X-Wo-File-Name': 'a.docx',
    }));

    expect(meta.encrypted).toBe(false);
    expect(meta.viewerName).toBeNull();
  });

  it('망가진 이름 때문에 문서를 못 열지는 않는다', () => {
    // decodeURIComponent 는 반쪽짜리 % 하나에도 던진다. 이름 하나 때문에
    // 문서를 통째로 못 여는 것보다 이름을 날것으로 보여 주는 편이 낫다.
    const meta = snapshotMeta(headers({
      'X-Wo-Doc-Type': 'SLIDE',
      'X-Wo-Encrypted': 'N',
      'X-Wo-File-Name': '100%.pptx',
    }));

    expect(meta.fileName).toBe('100%.pptx');
  });
});

describe('imageDataUrl', () => {
  it('생김새로 형식을 가린다', () => {
    // 서버는 그림 종류를 알려 주지 않는다. 앞 몇 글자가 곧 형식이다.
    expect(imageDataUrl('iVBORw0KGgo=')).toMatch(/^data:image\/png;base64,/);
    expect(imageDataUrl('/9j/4AAQSkZJRg==')).toMatch(/^data:image\/jpeg;base64,/);
    expect(imageDataUrl('R0lGODlhAQ==')).toMatch(/^data:image\/gif;base64,/);
  });

  it('모르는 형식은 png 로 본다', () => {
    expect(imageDataUrl('AAAA')).toBe('data:image/png;base64,AAAA');
  });
});

describe('toDocImages', () => {
  it('바이트를 주소로 바꾼다', () => {
    // 그림을 따로 받아 오는 길이 없다. base64 로 실려 온 것을 그대로 건다.
    const images = toDocImages([
      { afterParagraphNo: 3, bytes: 'iVBORw0KGgo=', width: 100, height: 50 },
    ]);

    expect(images).toHaveLength(1);
    expect(images[0].afterParagraphNo).toBe(3);
    expect(images[0].url).toBe('data:image/png;base64,iVBORw0KGgo=');
    expect(images[0].width).toBe(100);
  });

  it('없으면 빈 목록이다', () => {
    expect(toDocImages(null)).toEqual([]);
  });
});

describe('toDeckView', () => {
  it('슬라이드 그림도 주소로 바꾼다', () => {
    const view = toDeckView({
      width: 960,
      height: 540,
      slides: [{
        slideNo: 0,
        texts: [{ text: '가', left: 1, top: 2, width: 3, height: 4, fontSize: 18, bold: true }],
        tables: [],
        pictures: [{ bytes: '/9j/4AAQ', left: 5, top: 6, width: 7, height: 8 }],
        notes: null,
      }],
    });

    expect(view.width).toBe(960);
    expect(view.slides[0].pictures[0].url).toBe('data:image/jpeg;base64,/9j/4AAQ');
    expect(view.slides[0].texts[0].text).toBe('가');
  });

  it('빈 칸이 있어도 견딘다', () => {
    // 서버 record 는 빈 목록을 주지만 null 이 섞여 와도 화면이 죽지 않아야 한다.
    const view = toDeckView({ width: 0, height: 0, slides: null });

    expect(view.slides).toEqual([]);
    // 폭이 0 이면 배율을 셈할 때 0 으로 나눈다. 기본 크기로 물러선다.
    expect(view.width).toBeGreaterThan(0);
  });
});

describe('toSheetGrid', () => {
  it('값이 있는 데까지 격자를 넓힌다', () => {
    const grid = toSheetGrid([
      { rowNo: 0, columnNo: 0, valueType: 'STRING', textValue: '가' },
      { rowNo: 120, columnNo: 40, valueType: 'STRING', textValue: '나' },
    ]);

    expect(grid.rowCount).toBeGreaterThan(120);
    expect(grid.columnCount).toBeGreaterThan(40);
  });

  it('빈 시트에도 최소 격자를 준다', () => {
    // 격자가 없으면 빈 문서가 한 칸짜리로 뜬다.
    const grid = toSheetGrid([]);

    expect(grid.rowCount).toBeGreaterThanOrEqual(100);
    expect(grid.columnCount).toBeGreaterThanOrEqual(20);
  });
});

describe('toSheetDrawingResource', () => {
  it('시트 그림을 리소스 한 덩어리로 묶는다', () => {
    const resource = toSheetDrawingResource('wo-view', [{
      subUnitId: 'wo-view-sheet-0',
      pictures: [{
        bytes: 'iVBORw0KGgo=',
        fromRowNo: 1, fromRowOffset: 2, fromColumnNo: 3, fromColumnOffset: 4,
        toRowNo: 5, toRowOffset: 6, toColumnNo: 7, toColumnOffset: 8,
        zOrder: 0,
      }],
    }]);

    // data 는 객체가 아니라 JSON 문자열이어야 한다. 객체를 주면 Univer 가
    // JSON.parse 에 실패해 그림이 조용히 사라진다.
    expect(typeof resource.data).toBe('string');
    const parsed = JSON.parse(resource.data) as Record<string, {
      order: string[];
      data: Record<string, { source: string; imageSourceType: string;
        sheetTransform: { from: { row: number }; to: { row: number } }; }>;
    }>;
    const sheet = parsed['wo-view-sheet-0'];
    expect(sheet.order).toHaveLength(1);
    const drawing = sheet.data[sheet.order[0]];
    expect(drawing.imageSourceType).toBe('BASE64');
    expect(drawing.source).toBe('data:image/png;base64,iVBORw0KGgo=');
    expect(drawing.sheetTransform.from.row).toBe(1);
    expect(drawing.sheetTransform.to.row).toBe(5);
  });

  it('그림이 없는 시트도 자리는 만든다', () => {
    const resource = toSheetDrawingResource('wo-view', [
      { subUnitId: 'wo-view-sheet-0', pictures: [] },
    ]);

    const parsed = JSON.parse(resource.data) as Record<string, { order: string[] }>;
    expect(parsed['wo-view-sheet-0'].order).toEqual([]);
  });
});

describe('isEditCommand', () => {
  it('글을 고치는 명령을 가린다', () => {
    // 보기 화면에는 저장이 없다. 고칠 수 있게 두면 고친 것이 조용히 사라진다.
    expect(isEditCommand('doc.command.insert-text')).toBe(true);
    expect(isEditCommand('doc.mutation.rich-text-editing')).toBe(true);
  });

  it('시트 고치는 명령도 막는다', () => {
    // 보기 화면인데 칸에 글자가 들어갔다(2026-08-27 지적). 권한만으로는 안 막힌다.
    expect(isEditCommand('sheet.command.set-range-values')).toBe(true);
    expect(isEditCommand('sheet.mutation.set-range-values')).toBe(true);
    expect(isEditCommand('sheet.command.clear-selection-content')).toBe(true);
    expect(isEditCommand('sheet.operation.set-cell-edit-visible')).toBe(true);
    expect(isEditCommand('sheet.operation.set-activate-cell-edit')).toBe(true);
  });

  it('보기만 하는 움직임은 막지 않는다', () => {
    // 자리를 고르고 굴려 보는 것까지 막으면 문서를 볼 수가 없다.
    expect(isEditCommand('sheet.operation.set-selections')).toBe(false);
    expect(isEditCommand('sheet.operation.set-scroll')).toBe(false);
    expect(isEditCommand('sheet.operation.scroll-to-range')).toBe(false);
    expect(isEditCommand('sheet.operation.set-zoom-ratio')).toBe(false);
    expect(isEditCommand('sheet.operation.set-worksheet-active')).toBe(false);
  });

  it('읽는 명령은 막지 않는다', () => {
    // 스크롤·선택까지 막으면 보기 화면이 못 쓰게 된다.
    expect(isEditCommand('doc.operation.set-selection')).toBe(false);
    expect(isEditCommand('doc.operation.scroll')).toBe(false);
  });
});
