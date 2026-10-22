import { describe, expect, it } from 'vitest';

import type { DrawingDto } from './api';
import {
  ANCHOR_BOTH,
  SHEET_DRAWING_RESOURCE,
  diffDrawings,
  toDrawingDto,
  toDrawingResource,
  toUniverDrawing,
  type UniverDrawing,
} from './drawingSync';

function dto(drawingId: string, fromRowNo = 0): DrawingDto {
  return {
    drawingId,
    imageSeq: 1,
    fromRowNo,
    fromRowOffset: 0,
    fromColumnNo: 0,
    fromColumnOffset: 0,
    toRowNo: fromRowNo + 3,
    toRowOffset: 0,
    toColumnNo: 3,
    toColumnOffset: 0,
    anchorType: ANCHOR_BOTH,
    zOrder: 0,
    drawingExtra: null,
  };
}

function univerDrawing(overrides: Partial<UniverDrawing> = {}): UniverDrawing {
  return {
    unitId: 'wb-1',
    subUnitId: 'sheet-01',
    drawingId: 'dw-1',
    drawingType: 0,
    imageSourceType: 'URL',
    source: '/api/images/9',
    sheetTransform: {
      from: { row: 1, rowOffset: 5, column: 2, columnOffset: 10 },
      to: { row: 6, rowOffset: 15, column: 4, columnOffset: 20 },
    },
    anchorType: ANCHOR_BOTH,
    ...overrides,
  };
}

describe('diffDrawings', () => {
  it('새로 생긴 것만 보낸다', () => {
    const result = diffDrawings([dto('a')], [dto('a'), dto('b')]);

    // 안 바뀐 a 를 다시 보내면 이력이 오염되고 남의 화면이 헛되이 깜빡인다.
    expect(result.upserted.map((d) => d.drawingId)).toEqual(['b']);
    expect(result.deletedIds).toEqual([]);
  });

  it('사라진 것을 삭제로 낸다', () => {
    const result = diffDrawings([dto('a'), dto('b')], [dto('a')]);

    expect(result.upserted).toEqual([]);
    expect(result.deletedIds).toEqual(['b']);
  });

  it('값이 바뀐 것만 보낸다', () => {
    const result = diffDrawings([dto('a', 0)], [dto('a', 5)]);

    expect(result.upserted.map((d) => d.drawingId)).toEqual(['a']);
  });

  it('아무것도 안 바뀌면 빈 델타다', () => {
    // 이 단정이 깨지면 되보내기 고리가 생긴다.
    const result = diffDrawings([dto('a')], [dto('a')]);

    expect(result.upserted).toEqual([]);
    expect(result.deletedIds).toEqual([]);
  });
});

describe('toDrawingDto', () => {
  it('앵커를 픽셀 그대로 옮긴다', () => {
    const result = toDrawingDto(univerDrawing(), 2)!;

    expect(result.fromRowNo).toBe(1);
    expect(result.fromRowOffset).toBe(5);
    expect(result.fromColumnNo).toBe(2);
    expect(result.fromColumnOffset).toBe(10);
    expect(result.toRowNo).toBe(6);
    expect(result.toColumnOffset).toBe(20);
    expect(result.zOrder).toBe(2);
    expect(result.imageSeq).toBe(9);
  });

  it('소수 오프셋은 반올림한다', () => {
    // 서버 컬럼이 int 다. 소수를 그대로 보내면 저장 시 잘리는데,
    // 잘린 값이 다시 내려오면 매번 델타로 잡혀 되보내기가 끝나지 않는다.
    const result = toDrawingDto(univerDrawing({
      sheetTransform: {
        from: { row: 0, rowOffset: 5.4, column: 0, columnOffset: 10.6 },
        to: { row: 3, rowOffset: 0, column: 3, columnOffset: 0 },
      },
    }), 0)!;

    expect(result.fromRowOffset).toBe(5);
    expect(result.fromColumnOffset).toBe(11);
  });

  it('우리 이미지가 아니면 저장하지 않는다', () => {
    // base64 나 외부 URL 은 IMAGE_SEQ 가 없어 저장할 수 없다.
    expect(toDrawingDto(univerDrawing({ source: 'data:image/png;base64,AAA' }), 0)).toBeNull();
    expect(toDrawingDto(univerDrawing({ source: 'https://example.com/a.png' }), 0)).toBeNull();
  });

  it('매핑하지 않은 필드를 추가 속성에 모은다', () => {
    const result = toDrawingDto(univerDrawing({
      srcRect: { top: 10 },
      transform: { angle: 30 },
    } as Partial<UniverDrawing>), 0)!;

    const extra = JSON.parse(result.drawingExtra!);
    expect(extra.srcRect).toEqual({ top: 10 });
    expect(extra.transform).toEqual({ angle: 30 });
    // 컬럼으로 뺀 값은 들어가면 안 된다. 두 군데 있으면 어긋난다.
    expect(extra.sheetTransform).toBeUndefined();
    expect(extra.source).toBeUndefined();
    expect(extra.drawingId).toBeUndefined();
  });

  it('drawingType 도 추가 속성으로 왕복한다', () => {
    // 컬럼으로 빼지 않았다. 버리고 늘 0(이미지)으로 되돌리면 언젠가 도형이
    // 조용히 이미지로 바뀐다.
    const extra = JSON.parse(toDrawingDto(univerDrawing(), 0)!.drawingExtra!);

    expect(extra.drawingType).toBe(0);
    expect(Object.keys(extra)).toEqual(['drawingType']);
  });

  it('추가 속성이 정말 없으면 null 이다', () => {
    const bare = { ...univerDrawing() } as Partial<UniverDrawing>;
    delete bare.drawingType;

    expect(toDrawingDto(bare as UniverDrawing, 0)!.drawingExtra).toBeNull();
  });
});

describe('toUniverDrawing', () => {
  it('DTO 를 Univer 모양으로 되돌린다', () => {
    const result = toUniverDrawing(dto('dw-9', 4), 'wb-2', 'sheet-01');

    expect(result.drawingId).toBe('dw-9');
    expect(result.source).toBe('/api/images/1');
    expect(result.imageSourceType).toBe('URL');
    expect(result.sheetTransform.from.row).toBe(4);
    expect(result.sheetTransform.to.row).toBe(7);
    expect(result.unitId).toBe('wb-2');
  });

  it('왕복해도 같은 값이 나온다', () => {
    const original = univerDrawing({ srcRect: { top: 3 } } as Partial<UniverDrawing>);

    const back = toUniverDrawing(toDrawingDto(original, 0)!, 'wb-1', 'sheet-01');

    expect(back.sheetTransform).toEqual(original.sheetTransform);
    expect(back.srcRect).toEqual({ top: 3 });
    expect(back.source).toBe(original.source);
  });

  it('추가 속성의 옛 unitId 가 이기지 못한다', () => {
    // extra 를 나중에 펼치면 그림이 엉뚱한 문서에 붙는다.
    const withStaleUnit: DrawingDto = {
      ...dto('dw-1'),
      drawingExtra: JSON.stringify({ unitId: 'wb-옛것', drawingId: 'dw-옛것' }),
    };

    const result = toUniverDrawing(withStaleUnit, 'wb-새것', 'sheet-01');

    expect(result.unitId).toBe('wb-새것');
    expect(result.drawingId).toBe('dw-1');
  });

  it('깨진 추가 속성은 버리고 그림은 살린다', () => {
    const broken: DrawingDto = { ...dto('dw-1'), drawingExtra: '{이건 JSON 이 아니다' };

    expect(() => toUniverDrawing(broken, 'wb-1', 'sheet-01')).not.toThrow();
  });
});

describe('toDrawingResource', () => {
  it('시트를 여럿 담는다', () => {
    // 문서를 열 때 시트 전부를 채워 둔다. 한 시트만 담으면 다른 탭으로 갔을 때
    // 그림이 없다(2026-08-07).
    const resource = toDrawingResource('wb-1', [
      { subUnitId: 'sheet-1', drawings: [dto('dw-1')] },
      { subUnitId: 'sheet-2', drawings: [dto('dw-2')] },
    ]);

    const parsed = JSON.parse(resource.data);
    expect(Object.keys(parsed)).toEqual(['sheet-1', 'sheet-2']);
    expect(parsed['sheet-2'].order).toEqual(['dw-2']);
    expect(parsed['sheet-2'].subUnitId).toBe('sheet-2');
  });

  it('data 가 객체가 아니라 JSON 문자열이다', () => {
    // Univer 가 등록한 parseJson 이 JSON.parse 를 한다. 객체를 넣으면 실패하고
    // catch 가 {} 를 돌려줘 그림이 조용히 사라진다.
    const resource = toDrawingResource('wb-1', [{ subUnitId: 'sheet-01', drawings: [dto('dw-1')] }]);

    expect(resource.name).toBe(SHEET_DRAWING_RESOURCE);
    expect(typeof resource.data).toBe('string');

    const parsed = JSON.parse(resource.data);
    expect(parsed['sheet-01'].order).toEqual(['dw-1']);
    expect(parsed['sheet-01'].data['dw-1'].source).toBe('/api/images/1');
    expect(parsed['sheet-01'].unitId).toBe('wb-1');
  });

  it('순서를 목록 순서대로 매긴다', () => {
    const resource = toDrawingResource('wb-1', [{ subUnitId: 'sheet-01', drawings: [dto('dw-a'), dto('dw-b'), dto('dw-c')] }]);

    expect(JSON.parse(resource.data)['sheet-01'].order).toEqual(['dw-a', 'dw-b', 'dw-c']);
  });

  it('그림이 없어도 리소스 모양은 유지한다', () => {
    const parsed = JSON.parse(toDrawingResource('wb-1', [{ subUnitId: 'sheet-01', drawings: [] }]).data);

    expect(parsed['sheet-01'].order).toEqual([]);
    expect(parsed['sheet-01'].data).toEqual({});
  });
});
