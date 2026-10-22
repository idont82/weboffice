/**
 * 보기 전용 화면이 받는 스냅샷 다루기. 순수 함수만 둔다.
 *
 * <p>서버는 스냅샷 몸통에 변환 결과 JSON <b>그것만</b> 싣는다. 무슨 문서인지·누가
 * 보는지는 응답 머리에 실린다 - 몸통을 감싸면 시트·글·발표 자료 세 갈래 모두
 * 감싸는 층이 하나 더 생기고, 그 층은 화면 말고 아무도 쓰지 않는다.
 *
 * <p>화면을 세우는 코드({@link ./viewerRender})는 Univer 를 띄워야 해서 시험하기
 * 어렵다. 값을 옮기는 부분만 여기로 갈라 둔다.
 */

import type { CellDto } from './adapter';
import type { DocImage } from './docTable';
import { ANCHOR_BOTH, SHEET_DRAWING_RESOURCE } from './drawingSync';
import type { SlideDeckView } from './slideView';

/** 화면이 다루는 문서 종류. 서버 {@code DocKind.docType()} 과 같은 값이다. */
export type ViewerDocType = 'SHEET' | 'DOC' | 'SLIDE';

/** 응답 머리에서 읽은 것. 몸통(변환 결과 JSON)과 짝을 이룬다. */
export interface ViewerMeta {
  docType: ViewerDocType;
  fileName: string;
  encrypted: boolean;
  /** 다른 시스템이 알려 준 열람자. 안 알려 줬으면 null 이다. */
  viewerName: string | null;
}

export const DOC_TYPE_HEADER = 'X-Wo-Doc-Type';
export const ENCRYPTED_HEADER = 'X-Wo-Encrypted';
export const FILE_NAME_HEADER = 'X-Wo-File-Name';
export const VIEWER_NAME_HEADER = 'X-Wo-Viewer-Name';

/**
 * URL 로 싸여 온 이름을 푼다.
 *
 * <p>HTTP 머리는 한글을 실을 자리가 아니다(RFC 는 US-ASCII 만 본다). 서버가
 * UTF-8 URL 로 싸 보내고 여기서 푼다.
 *
 * <p>못 풀면 날것으로 둔다. decodeURIComponent 는 반쪽짜리 % 하나에도 던지는데,
 * 이름 하나 때문에 문서를 통째로 못 여는 것은 값이 맞지 않는다.
 */
function unwrap(value: string): string {
  try {
    return decodeURIComponent(value);
  } catch {
    return value;
  }
}

export function snapshotMeta(headers: Headers): ViewerMeta {
  const viewerName = headers.get(VIEWER_NAME_HEADER);
  return {
    docType: (headers.get(DOC_TYPE_HEADER) ?? 'SHEET') as ViewerDocType,
    fileName: unwrap(headers.get(FILE_NAME_HEADER) ?? ''),
    // 잠긴 문서에만 워터마크를 얹는다. 값이 없으면 안 잠긴 것으로 본다.
    encrypted: headers.get(ENCRYPTED_HEADER) === 'Y',
    viewerName: viewerName == null || viewerName === '' ? null : unwrap(viewerName),
  };
}

// ---------------------------------------------------------------- 그림

/**
 * base64 앞머리 → 그림 형식.
 *
 * <p>서버는 형식을 따로 알려 주지 않는다({@code byte[]} 하나뿐이다). 앞 몇 바이트가
 * 곧 형식이라 그것을 base64 로 옮긴 글자로 가린다 - png 는 {@code iVBORw0K},
 * jpeg 는 {@code /9j/}, gif 는 {@code R0lG} 로 시작한다.
 */
const SIGNATURES: [string, string][] = [
  ['iVBORw0K', 'image/png'],
  ['/9j/', 'image/jpeg'],
  ['R0lG', 'image/gif'],
  ['Qk', 'image/bmp'],
  ['UklG', 'image/webp'],
  ['PHN2Zw', 'image/svg+xml'],
];

export function imageDataUrl(base64: string): string {
  // 모르는 형식은 png 로 본다. 브라우저는 어차피 알맹이를 보고 그린다 - 형식을
  // 틀리게 적어도 대개 그려지고, 못 그려도 그림 하나만 빈다.
  const type = SIGNATURES.find(([head]) => base64.startsWith(head))?.[1] ?? 'image/png';
  return `data:${type};base64,${base64}`;
}

/** 서버 {@code DocContent.DocPicture}. {@code bytes} 는 JSON 에 base64 로 실린다. */
export interface DocPictureDto {
  afterParagraphNo: number;
  bytes: string;
  width: number;
  height: number;
  /** 글 흐름 밖에 떠 있는 그림인지. 표지 배경이 그렇다. */
  floating?: boolean;
  behind?: boolean;
  left?: number;
  top?: number;
  fromH?: number;
  fromV?: number;
}

export function toDocImages(pictures: DocPictureDto[] | null | undefined): DocImage[] {
  return (pictures ?? []).map((picture) => ({
    afterParagraphNo: picture.afterParagraphNo,
    // 떠 있는 그림은 제 문단을 갖지 않는다. 흐름에 넣으면 표지 배경이 첫 쪽을
    // 통째로 차지한다(2026-08-25 지적).
    ...(picture.floating ? {
      floating: true, inline: true, behind: picture.behind === true,
      left: picture.left ?? 0, top: picture.top ?? 0,
      fromH: picture.fromH ?? 0, fromV: picture.fromV ?? 1,
    } : {}),
    url: imageDataUrl(picture.bytes),
    width: picture.width,
    height: picture.height,
  }));
}

// ---------------------------------------------------------------- 발표 자료

/** 서버 {@code SlideDeck}. 그림만 화면 모양과 다르다(주소 대신 바이트다). */
export interface SlideDeckDto {
  width: number;
  height: number;
  /** 첫 장에 매길 쪽 번호. 표지를 0쪽으로 두는 자료가 있다. */
  firstSlideNo?: number;
  slides: {
    slideNo: number;
    texts: { text: string; left: number; top: number; width: number; height: number;
      fontSize: number; bold: boolean; color?: string | null;
      lines?: { text: string; fontSize: number; bold: boolean;
        color?: string | null; font?: string | null; bullet?: string | null;
        indent?: number; hanging?: number; align?: string | null }[];
      shrinkToFit?: boolean; font?: string | null;
      anchor?: string | null; rotation?: number; align?: string | null;
      padding?: { left: number; top: number; right: number; bottom: number }; }[] | null;
    shapes?: { left: number; top: number; width: number; height: number;
      fill: string | null; line: string | null; lineWidth: number; shape: string;
      rotation: number; gradient?: { color: string; at: number }[];
      gradientAngle?: number; image?: string | null; corner?: number;
      outline?: { x: number; y: number }[]; }[] | null;
    background?: string | null;
    backgroundImage?: string | null;
    tables: { cells: { text: string; fill?: string | null; color?: string | null;
        align?: string | null; fontSize?: number; bold?: boolean;
        font?: string | null }[][];
      left: number; top: number; width: number; height: number; }[] | null;
    pictures: { bytes: string; left: number; top: number; width: number;
      height: number; }[] | null;
    notes: string | null;
  }[] | null;
}

/** 슬라이드 크기를 못 읽었을 때. 4:3 도 16:9 도 아닌 것보다 파워포인트 기본이 낫다. */
const DEFAULT_SLIDE = { width: 960, height: 540 };

export function toDeckView(deck: SlideDeckDto): SlideDeckView {
  return {
    // 폭이 0 이면 화면에 맞추는 배율을 셈할 때 0 으로 나눈다.
    width: deck.width > 0 ? deck.width : DEFAULT_SLIDE.width,
    height: deck.height > 0 ? deck.height : DEFAULT_SLIDE.height,
    firstSlideNo: deck.firstSlideNo ?? 1,
    slides: (deck.slides ?? []).map((slide) => ({
      slideNo: slide.slideNo,
      texts: slide.texts ?? [],
      tables: slide.tables ?? [],
      pictures: (slide.pictures ?? []).map((picture) => ({
        url: imageDataUrl(picture.bytes),
        left: picture.left,
        top: picture.top,
        width: picture.width,
        height: picture.height,
      })),
      notes: slide.notes,
      // 색을 칠한 도형과 바탕. 이것을 빼면 흰 종이에 검은 글씨만 남는다.
      // 도형에 깔아 둔 그림은 알맹이로 온다. 화면이 읽을 주소로 바꾼다.
      shapes: (slide.shapes ?? []).map((shape) => ({
        ...shape,
        image: shape.image ? imageDataUrl(shape.image) : null,
      })),
      background: slide.background ?? null,
      backgroundImage: slide.backgroundImage == null || slide.backgroundImage === ''
        ? null : imageDataUrl(slide.backgroundImage),
    })),
  };
}

// ---------------------------------------------------------------- 시트

/** 편집 화면과 같은 값이다. 보기 화면만 격자가 다르면 같은 문서가 달라 보인다. */
const MIN_ROWS = 1_000;
const MIN_COLUMNS = 50;
const SPARE_ROWS = 200;
const SPARE_COLUMNS = 20;

/**
 * 시트 격자 크기.
 *
 * <p>편집 화면은 서버가 세어 둔 {@code maxRowNo} 를 쓰지만 보기 문서는 문서함에
 * 들어가지 않아 그 수가 없다. 받은 셀에서 직접 센다.
 */
export function toSheetGrid(cells: CellDto[] | null | undefined):
{ rowCount: number; columnCount: number } {
  let maxRow = 0;
  let maxColumn = 0;
  for (const cell of cells ?? []) {
    if (cell.rowNo > maxRow) maxRow = cell.rowNo;
    if (cell.columnNo > maxColumn) maxColumn = cell.columnNo;
  }
  return {
    rowCount: Math.max(maxRow + 1 + SPARE_ROWS, MIN_ROWS),
    columnCount: Math.max(maxColumn + 1 + SPARE_COLUMNS, MIN_COLUMNS),
  };
}

/** 서버 {@code PictureDto}. 오프셋 단위는 픽셀이다(Univer 와 같다). */
export interface SheetPictureDto {
  bytes: string;
  fromRowNo: number;
  fromRowOffset: number;
  fromColumnNo: number;
  fromColumnOffset: number;
  toRowNo: number;
  toRowOffset: number;
  toColumnNo: number;
  toColumnOffset: number;
  zOrder: number;
}

/**
 * 시트 그림을 워크북 스냅샷 리소스로 묶는다.
 *
 * <p>편집 화면은 우리 서버에 올린 그림을 주소({@code /api/images/7})로 건다.
 * 보기 문서는 저장소에 들어가지 않아 그런 주소가 없다 - 받은 base64 를 그대로
 * 실어 건다.
 *
 * <p>{@code data} 는 <b>객체가 아니라 JSON 문자열</b>이다. Univer 가 JSON.parse 를
 * 하고, 실패하면 {} 를 돌려줘 그림이 조용히 사라진다.
 */
export function toSheetDrawingResource(unitId: string,
  sheets: { subUnitId: string; pictures: SheetPictureDto[] | null }[]):
  { name: string; data: string } {
  const bySubUnit: Record<string, unknown> = {};

  for (const sheet of sheets) {
    const data: Record<string, unknown> = {};
    const order: string[] = [];
    (sheet.pictures ?? []).forEach((picture, at) => {
      const drawingId = `${sheet.subUnitId}-image-${at}`;
      data[drawingId] = {
        unitId,
        subUnitId: sheet.subUnitId,
        drawingId,
        drawingType: 0,
        imageSourceType: 'BASE64',
        source: imageDataUrl(picture.bytes),
        sheetTransform: {
          from: {
            row: picture.fromRowNo,
            rowOffset: picture.fromRowOffset,
            column: picture.fromColumnNo,
            columnOffset: picture.fromColumnOffset,
          },
          to: {
            row: picture.toRowNo,
            rowOffset: picture.toRowOffset,
            column: picture.toColumnNo,
            columnOffset: picture.toColumnOffset,
          },
        },
        // 두 셀 앵커. 엑셀에서 가져온 그림은 셀과 함께 움직인다.
        anchorType: ANCHOR_BOTH,
      };
      order.push(drawingId);
    });
    bySubUnit[sheet.subUnitId] = { unitId, subUnitId: sheet.subUnitId, data, order };
  }

  return { name: SHEET_DRAWING_RESOURCE, data: JSON.stringify(bySubUnit) };
}

// ---------------------------------------------------------------- 편집 막기

/**
 * 글을 고치는 명령인가.
 *
 * <p>시트는 권한 하나를 내리면 편집이 막히지만(WorkbookEditablePermission) 글
 * 문서에는 그런 자리가 OSS 에 없다. 명령 이름으로 가린다.
 *
 * <p>고르는 기준은 "글자가 바뀌는가" 다. 자리 옮기기(operation)·스크롤까지 막으면
 * 보기 화면이 못 쓰게 된다.
 */
/**
 * 보기 화면에서도 통과시킬 시트 움직임.
 *
 * <p>자리를 고르고 굴려 보고 배율을 바꾸는 것까지 막으면 문서를 볼 수가 없다.
 * 글자를 넣는 길(칸 편집 열기)은 여기 없다 - 그것은 고치는 명령이다.
 */
const LOOKING = new Set([
  'sheet.operation.set-selections',
  'sheet.operation.set-scroll',
  'sheet.operation.scroll-to-range',
  'sheet.operation.set-zoom-ratio',
  'sheet.operation.set-worksheet-active',
]);

export function isEditCommand(id: string): boolean {
  if (id.startsWith('doc.command.') || id.startsWith('doc.mutation.')) {
    return true;
  }
  // 시트는 권한을 내려도 칸에 글자가 들어갔다(2026-08-27 지적). 명령으로도 막는다.
  if (id.startsWith('sheet.command.') || id.startsWith('sheet.mutation.')) {
    return true;
  }
  return id.startsWith('sheet.operation.') && !LOOKING.has(id);
}
