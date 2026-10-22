/**
 * 그림 동기화.
 *
 * <p>Univer 는 그림이 바뀔 때마다 알린다. 이동·크기 조절은 드래그 중에 연속으로
 * 발생하므로 그대로 보내면 초당 수십 번 저장한다. 마지막 상태와 비교해 진짜
 * 바뀐 것만, 디바운스한 뒤에 보낸다.
 *
 * <p>셀 저장이 같은 함정에 빠진 적이 있다 - 잠근 범위 전체를 매번 훑어 안 바뀐
 * 셀까지 되보내고, 그것이 브로드캐스트로 돌아와 다시 저장을 부르며 무한히 오갔다.
 *
 * <p>Univer 쪽 자료 구조는 docs/superpowers/plans/2026-08-05-univer-drawing-probe.md
 * 의 실측을 따른다.
 */
import { ImageSourceType } from '@univerjs/core';

import type { DrawingDto } from './api';
import { imageSeqOf } from './serverImageIo';

/** xlsx 의 두 셀 앵커에 해당하는 Univer 값. 숫자가 아니라 문자열이다. */
export const ANCHOR_BOTH = '1';

/** Univer 가 시트 그림을 담는 스냅샷 리소스 이름. data 는 JSON 문자열이다. */
export const SHEET_DRAWING_RESOURCE = 'SHEET_DRAWING_PLUGIN';

export interface DrawingDelta {
  upserted: DrawingDto[];
  deletedIds: string[];
}

/** Univer 의 ISheetImage 중 우리가 쓰는 부분. */
export interface UniverDrawing {
  unitId: string;
  subUnitId: string;
  drawingId: string;
  drawingType: number;
  imageSourceType: string;
  source: string;
  sheetTransform: {
    from: { row: number; rowOffset: number; column: number; columnOffset: number };
    to: { row: number; rowOffset: number; column: number; columnOffset: number };
  };
  anchorType?: string;
  [extra: string]: unknown;
}

/** 컬럼으로 뺀 값. drawingExtra 에 중복해 넣지 않는다 - 두 군데 있으면 어긋난다. */
const MAPPED_FIELDS = new Set([
  'unitId', 'subUnitId', 'drawingId', 'imageSourceType', 'source',
  'sheetTransform', 'anchorType',
]);

// ---------------------------------------------------------------- 델타

/** 비교용 지문. 필드가 늘어도 자동으로 따라가도록 통째로 직렬화한다. */
function fingerprint(drawing: DrawingDto): string {
  return JSON.stringify(drawing);
}

export function diffDrawings(before: DrawingDto[], after: DrawingDto[]): DrawingDelta {
  const beforeById = new Map(before.map((d) => [d.drawingId, fingerprint(d)]));
  const afterIds = new Set(after.map((d) => d.drawingId));

  return {
    upserted: after.filter((d) => beforeById.get(d.drawingId) !== fingerprint(d)),
    deletedIds: before.map((d) => d.drawingId).filter((id) => !afterIds.has(id)),
  };
}

// ---------------------------------------------------------------- 변환

/**
 * Univer 그림 → 서버 DTO.
 *
 * <p>우리 서버가 올린 이미지가 아니면(base64, 외부 URL) null 을 준다. 남이 만든
 * 문서를 열면 그런 그림이 들어올 수 있는데, 저장하려면 IMAGE_SEQ 가 있어야 한다.
 */
export function toDrawingDto(drawing: UniverDrawing, zOrder: number): DrawingDto | null {
  const imageSeq = imageSeqOf(drawing.source);
  if (imageSeq === null) {
    return null;
  }

  const { from, to } = drawing.sheetTransform;
  return {
    drawingId: drawing.drawingId,
    imageSeq,
    fromRowNo: from.row,
    fromRowOffset: Math.round(from.rowOffset),
    fromColumnNo: from.column,
    fromColumnOffset: Math.round(from.columnOffset),
    toRowNo: to.row,
    toRowOffset: Math.round(to.rowOffset),
    toColumnNo: to.column,
    toColumnOffset: Math.round(to.columnOffset),
    anchorType: drawing.anchorType ?? ANCHOR_BOTH,
    zOrder,
    drawingExtra: extraOf(drawing),
  };
}

/**
 * 컬럼으로 빼지 않은 필드를 JSON 문자열로 모은다.
 *
 * <p>자르기(srcRect)·회전(transform.angle)·axisAlignSheetTransform 이 여기 들어간다.
 * 버리면 사용자가 자른 그림이 새로고침 때 조용히 원래대로 돌아간다.
 */
function extraOf(drawing: UniverDrawing): string | null {
  const extra: Record<string, unknown> = {};
  for (const [key, value] of Object.entries(drawing)) {
    if (!MAPPED_FIELDS.has(key) && value !== undefined) {
      extra[key] = value;
    }
  }
  return Object.keys(extra).length === 0 ? null : JSON.stringify(extra);
}

/** 서버 DTO → Univer 그림. */
export function toUniverDrawing(dto: DrawingDto, unitId: string,
                                subUnitId: string): UniverDrawing {
  const extra = dto.drawingExtra ? safeParse(dto.drawingExtra) : {};

  return {
    // 펼치기를 먼저 둔다. 뒤의 확정 값이 이겨야 한다 - extra 에 옛 unitId 가
    // 남아 있으면 그림이 엉뚱한 문서에 붙는다.
    ...extra,
    unitId,
    subUnitId,
    drawingId: dto.drawingId,
    drawingType: (extra.drawingType as number) ?? 0,
    imageSourceType: ImageSourceType.URL,
    source: `/api/images/${dto.imageSeq}`,
    sheetTransform: {
      from: {
        row: dto.fromRowNo,
        rowOffset: dto.fromRowOffset,
        column: dto.fromColumnNo,
        columnOffset: dto.fromColumnOffset,
      },
      to: {
        row: dto.toRowNo,
        rowOffset: dto.toRowOffset,
        column: dto.toColumnNo,
        columnOffset: dto.toColumnOffset,
      },
    },
    anchorType: dto.anchorType,
  };
}

function safeParse(json: string): Record<string, unknown> {
  try {
    return JSON.parse(json) as Record<string, unknown>;
  } catch {
    // 사람이 손으로 고친 값이 들어올 수 있다. 그림 하나 때문에 문서가
    // 안 열리는 것보다 추가 속성을 잃는 편이 낫다.
    return {};
  }
}

/**
 * 워크북 스냅샷에 넣을 리소스 항목을 만든다.
 *
 * <p>data 는 <b>객체가 아니라 JSON 문자열</b>이다. Univer 가 등록한 parseJson 이
 * JSON.parse 를 하고, 실패하면 catch 가 {} 를 돌려줘 그림이 조용히 사라진다.
 */
export interface SheetDrawings {
  subUnitId: string;
  drawings: DrawingDto[];
}

/**
 * 시트 여럿의 그림을 한 리소스로 묶는다.
 *
 * <p>문서를 열 때 시트 전부를 채워 둔다. 한 시트만 담으면 다른 탭으로 갔을 때 그림이
 * 없다 - 예전에는 탭을 누를 때마다 워크북을 다시 만들어 그 시트 것을 채웠는데,
 * 그것이 느리고 터졌다(2026-08-07).
 */
export function toDrawingResource(unitId: string, sheets: SheetDrawings[]) {
  const bySubUnit: Record<string, unknown> = {};

  for (const sheet of sheets) {
    const data: Record<string, UniverDrawing> = {};
    const order: string[] = [];
    for (const dto of sheet.drawings) {
      data[dto.drawingId] = toUniverDrawing(dto, unitId, sheet.subUnitId);
      order.push(dto.drawingId);
    }
    bySubUnit[sheet.subUnitId] = { unitId, subUnitId: sheet.subUnitId, data, order };
  }

  return {
    name: SHEET_DRAWING_RESOURCE,
    data: JSON.stringify(bySubUnit),
  };
}
