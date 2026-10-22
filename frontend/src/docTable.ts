/**
 * 문단과 표를 Univer 본문으로 짓는다.
 *
 * <p>워드·한글에서 가져온 표를 표 모양 그대로 세우는 자리다. 전에는 칸 글자를
 * 문단으로 펼쳐 세로로 길게 늘어놓았다 - 원본과 너무 달라 보였다(2026-08-12 지적).
 *
 * <p>표는 제어문자로 감싼다. 우리가 만든 표를 뜯어 확인한 규칙이다:
 * 표시작 · 행시작 · 칸시작 · 글 · 문단끝 · 구역끝 · 칸끝 · 행끝 · 표끝.
 * 하나라도 빠지면 Univer 가 표를 그리지 못한다.
 */

import { withDefaultFont, withUsableFont } from './docFont';
import { fontLineRatio } from './docLineHeight';
import { insertFields, type FieldMark, type FieldRange, type RunMark } from './docField';
import { opensNewPage, toBody, withoutPageBreak, PAGE_BREAK, type DocBody, type DocParagraph }
  from './docBody';

const TABLE_START = String.fromCharCode(0x1a);
const ROW_START = String.fromCharCode(0x1b);
const CELL_START = String.fromCharCode(0x1c);
const CELL_END = String.fromCharCode(0x1d);
const ROW_END = String.fromCharCode(0x0e);
const TABLE_END = String.fromCharCode(0x0f);

/** 화면은 96dpi, 글자 크기는 72dpi 다. */
const PX_PER_PT = 96 / 72;

/** 칸 글자 크기를 안 적었을 때. 워드 파일 규칙의 기본(10pt)이다. */
const CELL_FONT_SIZE = 10;

/** 그림이 글 흐름에서 차지하는 자리. Univer 가 이 글자 하나로 그림을 건다. */
const DRAWING_ANCHOR = String.fromCharCode(0x08);

/** 가져온 표 하나. 어느 문단 뒤에 놓이는지와 칸 글자·칸 너비를 담는다. */
export interface DocTable {
  /** 이 문단 번호 뒤에 놓인다. -1 이면 문서 맨 앞이다. */
  afterParagraphNo: number;
  cells: string[][];
  /** 원본 칸 너비(px). 없거나 칸 수와 어긋나면 고르게 나눈다. */
  columnWidths?: number[];
  /** 칸 바탕색(#rrggbb). 색이 없는 칸은 null 이다. */
  cellShades?: (string | null)[][];
  /** 가로로 차지하는 칸 수. 합치기에 가려진 칸은 0 이다. */
  columnSpans?: number[][];
  /** 세로로 차지하는 줄 수. 합치기에 가려진 칸은 0 이다. */
  rowSpans?: number[][];
  /** 선이 그어진 표인지. 적지 않으면 그어진 것으로 본다(Univer 기본). */
  hasBorders?: boolean;
  /** 칸 글자 서식. 칸 글자 안 자리로 적는다. 서식이 없는 칸은 null 이다. */
  cellRuns?: (string | null)[][];
  /** 표가 앉는 자리. 1 왼쪽 · 2 가운데 · 3 오른쪽. 적지 않으면 왼쪽이다. */
  align?: number;
  /** 못박은 줄 높이(px). 안 적힌 줄은 null 이고 글에 맞춰 늘어난다. */
  rowHeights?: (number | null)[];
  /** 칸 안쪽 여백(px). 없으면 화면 기본을 쓴다. */
  cellMargin?: { left: number; right: number; top: number; bottom: number };
  /**
   * 줄 높이를 못박은 것인지. 적지 않으면 못박은 것으로 본다.
   *
   * <p>한글 문서 높이는 "이만큼 찼다" 는 뜻이라 못박으면 글이 잘린다(2026-08-26).
   */
  exactRowHeights?: boolean;
  /** 칸 글의 가로 맞춤. 1 왼쪽 · 2 가운데 · 3 오른쪽. 안 적혔으면 왼쪽이다. */
  cellAligns?: number[][];
  /** 칸 안 문단 사이 간격(px). 안 적혔으면 0 이다. */
  cellSpaces?: number[][];
  /**
   * 그은 변만 담은 JSON. 없으면 표 전체가 격자이거나 선이 없다.
   *
   * <p>네 변 가운데 아래만 긋는 제목 표가 흔하다(2026-08-26 지적).
   */
  borders?: string;
  /**
   * 이 표가 새 쪽을 여는지.
   *
   * <p>표지 다음 목차가 표로 시작하는 문서가 있다. 흘리면 목차 표만 표지 쪽에
   * 남는다(2026-08-25 지적).
   */
  opensNewPage?: boolean;
}

/**
 * 가져온 그림 하나. 알맹이는 그림 저장소에 있고 여기에는 자리와 크기만 있다.
 *
 * <p>아직 그리지는 못한다. 본문에 그림 자리()를 넣으면 Univer 가 문서를 통째로
 * 못 그렸다 - 글자 하나 없이 화면이 하얗게 떴다(2026-08-12 실측, 세 번 되풀이).
 * 그림 하나 때문에 문서를 통째로 잃을 수는 없어 자리만 들고 있는다.
 */
export interface DocImage {
  /** 이 문단 번호 뒤에 놓인다. -1 이면 문서 맨 앞이다. */
  afterParagraphNo: number;
  url: string;
  width: number;
  height: number;
  /**
   * 제 문단을 갖지 않고 그 문단 글 앞에 끼는 그림.
   *
   * <p>머릿글 로고가 그렇다. 제 문단을 주면 머릿글이 한 줄 높아져 본문이 밀린다
   * (2026-08-25 실측: 24쪽이 26쪽).
   */
  inline?: boolean;
  /**
   * 쪽마다 다시 그리는 그림.
   *
   * <p>머릿글 로고가 그렇다. 이 표시가 없으면 화면이 첫 쪽에만 그린다
   * (Univer 0.25.1 실측: 2쪽에만 로고가 보였다).
   */
  everyPage?: boolean;
  /**
   * 글 흐름 밖 왼쪽에 띄우는 그림.
   *
   * <p>머릿글 로고가 그렇다. 글줄에 끼우면 오른쪽 맞춤인 글을 따라 로고까지
   * 오른쪽으로 끌려간다(2026-08-25 지적).
   */
  floatLeft?: boolean;
  /**
   * 글 흐름 밖에 떠 있는 그림.
   *
   * <p>표지 배경이 그렇다. 흐름에 넣으면 첫 쪽을 통째로 차지한다(2026-08-25 지적).
   */
  floating?: boolean;
  /** 글 뒤에 깔리는지. */
  behind?: boolean;
  /** 떠 있는 그림의 자리(px). */
  left?: number;
  top?: number;
  /** 자리를 어디서부터 재는지. 가로 0 쪽 · 1 단 · 2 글자 · 3 여백. */
  fromH?: number;
  /** 세로 0 쪽 · 1 문단 · 2 줄 · 3 여백. */
  fromV?: number;
}

interface TableMark {
  startIndex: number;
  endIndex: number;
  tableId: string;
}

export interface DocBodyWithTables extends Omit<DocBody, 'tables'> {
  tables?: TableMark[];
  tableSource?: Record<string, unknown>;
  /** 그림이 글 흐름에서 차지하는 자리. */
  customBlocks?: { startIndex: number; blockId: string; blockType: number }[];
  drawings?: Record<string, unknown>;
  drawingsOrder?: string[];
}

/**
 * 칸 여백·줄 높이·표 폭. Univer 가 새 표에 쓰는 값을 그대로 뜬 것이다.
 *
 * <p>하나라도 빠지면 Univer 가 문서를 통째로 못 그린다 - 화면이 하얗게 비고
 * "reading 'slice'" 만 남았다(2026-08-12 실측).
 */
/** 표는 본문보다 이만큼 좁아야 한다. 딱 맞으면 화면이 백지가 된다. */
const TABLE_SLACK = 2;

const CELL_MARGIN = { start: { v: 10 }, end: { v: 10 }, top: { v: 5 }, bottom: { v: 5 } };

/** 선을 긋지 않는 칸. 굵기 0 이 "선 없음" 이다. */
const NO_BORDER = {
  borderTop: { border: { s: 0 } },
  borderBottom: { border: { s: 0 } },
  borderLeft: { border: { s: 0 } },
  borderRight: { border: { s: 0 } },
};

/**
 * 그은 변만 긋는 칸 서식.
 *
 * <p>네 변 가운데 아래만 긋는 제목 표가 흔하다. 안 그은 변은 굵기 0 으로 닫는다 -
 * 안 닫으면 화면 기본(격자)이 살아나 원본과 딴판이 된다(2026-08-26 지적).
 */
function cellBorders(drawn: string | undefined): Record<string, unknown> | null {
  if (!drawn) {
    return null;
  }
  let sides: Record<string, { size?: number; color?: string }>;
  try {
    sides = JSON.parse(drawn) as Record<string, { size?: number; color?: string }>;
  } catch {
    return null;
  }
  const named: Record<string, string> = {
    top: 'borderTop', bottom: 'borderBottom', left: 'borderLeft', right: 'borderRight',
  };
  const made: Record<string, unknown> = {};
  for (const [side, key] of Object.entries(named)) {
    const one = sides[side];
    made[key] = one == null
      // 굵기 0 이면 화면이 그 변을 안 긋는다. 안 적으면 화면 기본(격자)이 산다.
      ? { color: { rgb: 'transparent' }, width: { v: 0 } }
      : {
        color: { rgb: one.color ?? '#000000' },
        width: { v: Math.max(1, one.size ?? 1) },
      };
  }
  return made;
}
const ROW_HEIGHT = { val: { v: 30 }, hRule: 0 };

/** 칸 여백 없음. 못박은 줄에 쓴다 - 여백이 남으면 그만큼 줄이 두꺼워진다. */
const NO_MARGIN = { start: { v: 0 }, end: { v: 0 }, top: { v: 0 }, bottom: { v: 0 } };

/**
 * 워드 정렬(1 왼쪽 · 2 가운데 · 3 오른쪽)을 Univer 것으로 바꾼다.
 *
 * <p>Univer 는 0 왼쪽 · 1 가운데 · 2 오른쪽이다(TableAlignmentType).
 */
function tableAlign(align: number | undefined): number {
  return align === 2 ? 1 : align === 3 ? 2 : 0;
}

/** 본문 폭. A4 에서 좌우 여백을 뺀 값이다(ruler 의 PAGE 와 같은 셈). */
export const BODY_WIDTH = 794 - Math.round((96 / 2.54) * 2.5) * 2;

/**
 * 칸 글자에서 제어문자를 뗀다.
 *
 * <p>줄바꿈 하나가 섞이면 Univer 가 구역 끝으로 읽어 문서 얼개가 무너진다.
 * 칸 안 문단을 가르는 글자만 남기고 모두 공백으로 바꾼다 - 그것까지 지우면
 * 표가 원본보다 낮아져 뒷글이 앞 쪽으로 올라온다(2026-08-25 지적).
 */
function clean(text: string): string {
  return text.split(CELL_LINE_BREAK)
    // eslint-disable-next-line no-control-regex
    .map((line) => line.replace(/[\u0000-\u0009\u000b-\u001f]+/g, ' ').trim())
    .join(CELL_LINE_BREAK)
    .trim();
}

/**
 * 칸 너비를 정한다.
 *
 * <p>원본 너비가 칸 수와 딱 맞을 때만 쓴다. 칸을 합친(gridSpan) 표는 너비 수와 칸
 * 수가 어긋나는데, 반만 맞는 너비로 그리면 고르게 나눈 것보다 더 어긋나 보인다.
 *
 * <p>원본이 우리보다 여백이 좁으면 표가 종이 밖으로 나간다. 그럴 때는 비율을
 * 지키며 본문 폭에 맞춰 줄인다.
 */
function widthsOf(columnCount: number, given: number[] | undefined,
  bodyWidth: number): number[] {
  const usable = given != null && given.length === columnCount
    && given.every((width) => width > 0);
  if (!usable) {
    return Array.from({ length: columnCount }, () => bodyWidth / columnCount);
  }
  const total = given.reduce((sum, width) => sum + width, 0);
  // 본문에 안 들어가는 표만 줄인다. 원본이 좁게 만든 표를 늘리면 딴판이 된다.
  // 본문 폭과 똑같아져도 안 된다 - Univer 가 문서를 통째로 못 그린다(2026-08-13
  // 실측: 720px 본문에 720px 표를 넣으니 화면이 백지가 됐다).
  const most = bodyWidth - TABLE_SLACK;
  const scale = total > most ? most / total : 1;
  return given.map((width) => width * scale);
}

/**
 * 표 칸 문단의 줄 높이.
 *
 * <p>본문과 같은 셈이다 - 글자 크기 x 글꼴 행간. 규칙은 "정확히"(2)로 둔다.
 */
function cellLineStyle(runs: string | null, defaultFont: string | null,
  ratioOf: (font: string | null) => number,
  fixed?: number | null): { lineSpacing: number; spacingRule: number } {
  const first = parseRuns(runs).find((run) => run.ts != null)?.ts as
    Record<string, unknown> | undefined;
  const size = typeof first?.fs === 'number' && first.fs > 0 ? first.fs : CELL_FONT_SIZE;
  const font = typeof first?.ff === 'string' && first.ff !== '' ? first.ff : defaultFont;
  const lines = size * PX_PER_PT * ratioOf(font);
  // 못박은 줄에서는 글줄이 그 높이를 넘지 못한다. 4px 짜리 색 막대가 칸 안
  // 빈 줄(18px) 때문에 부풀었다(2026-08-25 지적: 표지 주황 막대가 두껍다).
  // 화면(Univer 0.25.1)은 못박은 높이를 "최소" 로 읽어 칸이 낮아야만 낮아진다.
  return {
    lineSpacing: fixed != null && fixed > 0
      ? Math.max(SQUEEZED_LINE, Math.min(lines, fixed)) : lines,
    spacingRule: 2,
  };
}

/**
 * 눌러 둔 칸 줄의 높이(px).
 *
 * <p>5 이하로 적으면 화면이 "배수" 로 읽어 글자 상자 높이로 되돌린다. 6 이
 * 그 경계를 넘는 가장 낮은 값이다.
 */
const SQUEEZED_LINE = 6;

/** 눌러 둔 칸 글자 크기(pt). 글자 상자가 줄 높이를 밀어 올리지 못하게 한다. */
const SQUEEZED_CELL_FONT = 1;

/** 칸 안 문단을 가르는 글자. 서버가 이 글자로 이어 보낸다. */
const CELL_LINE_BREAK = String.fromCharCode(10);

/** 문단 마감. */
const PARAGRAPH_END = String.fromCharCode(13);

/** 못 읽는 서식은 없는 것으로 본다. 서식 하나 때문에 문서를 못 열면 안 된다. */
/** 필드 자리만 고른다. 서버가 같은 목록에 섞어 보낸다. */
function fieldsOf(runs: Array<Record<string, unknown>>): FieldMark[] {
  return runs.filter((one) => typeof one.fld === 'string') as unknown as FieldMark[];
}

/** 글자 서식 자리만 고른다. */
function stylesOf(runs: Array<Record<string, unknown>>): RunMark[] {
  return runs.filter((one) => typeof one.fld !== 'string') as unknown as RunMark[];
}

function parseRuns(runs: string | null):
{ st: number; ed: number; ts?: Record<string, unknown> }[] {
  if (!runs) {
    return [];
  }
  try {
    return JSON.parse(runs) as { st: number; ed: number }[];
  } catch {
    return [];
  }
}

/**
 * 종이보다 큰 떠 있는 그림을 종이 안에 넣는다.
 *
 * <p>워드는 종이 밖으로 나간 그림을 잘라 그리지만 화면은 그대로 그려 종이 밖까지
 * 삐져나온다(2026-08-25 지적: 첫 쪽 이미지가 너무 크다). 비율은 지킨다.
 */
export function fitFloating(images: DocImage[], pageWidth: number,
  pageHeight: number, marginLeft = 0, marginTop = 0): DocImage[] {
  return images.map((image) => {
    if (!image.floating) {
      return image;
    }
    const wide = (image.left ?? 0) < 0 || image.width > pageWidth;
    const tall = (image.top ?? 0) < 0 || image.height > pageHeight;
    if (!wide && !tall) {
      return image;
    }
    const scale = Math.min(pageWidth / image.width, pageHeight / image.height, 1);
    return {
      ...image,
      width: image.width * scale,
      height: image.height * scale,
      // 화면은 그림 자리를 본문 여백 안에서 잰다. 여백만큼 당겨야 종이 왼쪽 위다.
      left: -marginLeft,
      top: -marginTop,
    };
  });
}

export function toBodyWithTables(rows: DocParagraph[], tables: DocTable[],
  images: DocImage[] = [], unitId = '', bodyWidth = BODY_WIDTH,
  idPrefix = '', defaultFont: string | null = null,
  ratioOf: (font: string | null) => number = fontLineRatio): DocBodyWithTables {
  if (tables.length === 0 && images.length === 0) {
    return { ...toBody(rows, defaultFont), tables: [], tableSource: {} };
  }
  const ordered = [...rows].sort((a, b) => a.paragraphNo - b.paragraphNo);
  const byPosition = new Map<number, DocTable[]>();
  for (const table of tables) {
    const list = byPosition.get(table.afterParagraphNo) ?? [];
    list.push(table);
    byPosition.set(table.afterParagraphNo, list);
  }

  const imagesAt = new Map<number, DocImage[]>();
  const inlineAt = new Map<number, DocImage[]>();
  for (const image of images) {
    if (image.inline) {
      const inlines = inlineAt.get(image.afterParagraphNo) ?? [];
      inlines.push(image);
      inlineAt.set(image.afterParagraphNo, inlines);
      continue;
    }
    const list = imagesAt.get(image.afterParagraphNo) ?? [];
    list.push(image);
    imagesAt.set(image.afterParagraphNo, list);
  }

  let stream = '';
  const paragraphs: { startIndex: number; paragraphStyle?: object }[] = [];
  const textRuns: { st: number; ed: number }[] = [];
  const marks: TableMark[] = [];
  const source: Record<string, unknown> = {};
  const customBlocks: { startIndex: number; blockId: string; blockType: number }[] = [];
  const customRanges: FieldRange[] = [];
  const drawings: Record<string, unknown> = {};
  const drawingsOrder: string[] = [];
  let tableNo = 0;
  let imageNo = 0;

  /** 그림 자리를 글 흐름에 끼운다. 얼개에 빠진 것이 있으면 문서를 통째로 못 그린다. */
  const writeImages = (afterParagraphNo: number, inline = false) => {
    for (const image of (inline ? inlineAt : imagesAt).get(afterParagraphNo) ?? []) {
      const drawingId = `${idPrefix}wo-image-${imageNo += 1}`;
      customBlocks.push({ startIndex: stream.length, blockId: drawingId, blockType: 0 });
      stream += DRAWING_ANCHOR;
      drawings[drawingId] = {
        drawingId,
        unitId,
        subUnitId: unitId,
        drawingType: 0,
        imageSourceType: 'URL',
        source: image.url,
        // 글 흐름에 끼어 흐르는 그림이다(INLINE). 흐름 밖에 띄우는 그림은
        // 글을 따라 밀리지 않고 제 자리(WRAP_NONE)에 선다.
        layoutType: image.floatLeft || image.floating ? 1 : 0,
        behindDoc: image.behind ? 1 : 0,
        wrapText: 0,
        distL: 0,
        distR: 0,
        distT: 0,
        distB: 0,
        title: '',
        description: '',
        // 쪽마다 자리를 따로 잡아야 머릿글 로고가 모든 쪽에 그려진다.
        ...(image.everyPage ? { isMultiTransform: 1 } : {}),
        // 절대 자리도 함께 준다. 빼면 문서가 통째로 안 그려진다(2026-08-13 실측).
        transform: {
          left: 0, top: 0, width: image.width, height: image.height, angle: 0,
        },
        docTransform: {
          size: { width: image.width, height: image.height },
          positionH: { relativeFrom: image.fromH ?? 0, posOffset: image.left ?? 0 },
          positionV: { relativeFrom: image.floating ? image.fromV ?? 1 : 0,
            posOffset: image.top ?? 0 },
          angle: 0,
        },
      };
      drawingsOrder.push(drawingId);
      if (inline) {
        // 글 앞에 끼는 그림은 그 문단을 함께 쓴다. 제 문단을 주면 줄이 하나 는다.
        continue;
      }
      // 그림에 제 문단을 준다. 문단 없이 자리만 넣으면 화면이 백지가 된다
      // (2026-08-13 실측). 맞춤은 건드리지 않는다 - 원본은 그림 문단에 맞춤을
      // 안 적고, 가운데로 세우면 원본과 다른 자리에 앉는다(2026-08-26 지적).
      paragraphs.push({ startIndex: stream.length });
      stream += '\r';
    }
  };

  const writeTables = (afterParagraphNo: number) => {
    for (const table of byPosition.get(afterParagraphNo) ?? []) {
      if (table.opensNewPage) {
        // 표 앞에 쪽 나눔만 든 문단을 둔다. 저장할 때 이 문단은 버려지고 표시는
        // 서버가 표에 다시 적는다.
        stream += PAGE_BREAK;
        paragraphs.push({ startIndex: stream.length });
        stream += PARAGRAPH_END;
      }
      // 행마다 칸 수를 맞춘다. 워드는 칸을 합쳐(gridSpan) 한 행을 짧게 만드는데,
      // 우리는 합치기를 모르니 그대로 두면 칸 수가 어긋난다. 그러면 Univer 가
      // 표에서 레이아웃을 멈춰 뒤쪽 글이 통째로 안 보인다(2026-08-12 실측:
      // 8쪽 문서가 첫 표까지만 그려졌다).
      const columnCount = Math.max(1, ...table.cells.map((row) => row.length));
      const grid = table.cells.map((row) => Array.from({ length: columnCount },
        (_unused, at) => clean(row[at] ?? '')));

      const startIndex = stream.length;
      stream += TABLE_START;
      for (const [rowNo, row] of grid.entries()) {
        stream += ROW_START;
        for (const [columnNo, cell] of row.entries()) {
          stream += CELL_START;
          // 칸 글자 서식도 밀린 자리로 옮긴다. 표 머리가 굵은 원본이 흔한데
          // 서식이 빠지면 머리와 본문이 같아 보인다.
          const cellFrom = stream.length;
          for (const run of parseRuns(table.cellRuns?.[rowNo]?.[columnNo] ?? null)) {
            textRuns.push(withUsableFont(withDefaultFont(
              { ...run, st: run.st + cellFrom, ed: run.ed + cellFrom }, defaultFont)));
          }
          // 못박은 줄의 빈 칸은 글자 상자도 눌러 둔다. 크기를 그대로 두면 그
          // 상자가 줄 높이를 밀어 올려 색 막대가 네 배로 두꺼워진다.
          if (cell === '' && (table.rowHeights?.[rowNo] ?? 0) > 0) {
            textRuns.push({ st: cellFrom, ed: cellFrom + 1,
              ts: { fs: SQUEEZED_CELL_FONT } } as never);
          }
          // 칸 글도 본문과 같은 셈으로 줄 높이를 잡는다. 안 그러면 표가 워드보다
          // 높아져 뒤 글이 다음 쪽으로 밀린다(2026-08-25 실측).
          const cellStyle = cellLineStyle(table.cellRuns?.[rowNo]?.[columnNo] ?? null,
            defaultFont, ratioOf, table.rowHeights?.[rowNo]);
          // 칸 안 줄 나눔마다 문단을 만든다. 한 줄로 이으면 원본보다 표가 낮아져
          // 뒷글이 앞 쪽으로 올라온다(2026-08-25 지적: 박스가 다음 쪽으로 넘어간다).
          const cellLines = cell.split(CELL_LINE_BREAK);
          // 마지막 줄에는 간격을 두지 않는다. 워드 원본이 그렇게 적는다.
          const space = table.cellSpaces?.[rowNo]?.[columnNo] ?? 0;
          const spacedStyle = space > 0
            ? { ...cellStyle, spaceBelow: { v: space } } : cellStyle;
          for (const [at, line] of cellLines.entries()) {
            stream += line;
            if (at < cellLines.length - 1) {
              paragraphs.push({ startIndex: stream.length, paragraphStyle: spacedStyle });
              stream += PARAGRAPH_END;
            }
          }
          paragraphs.push({ startIndex: stream.length, paragraphStyle: cellStyle });
          stream += '\r\n' + CELL_END;
        }
        stream += ROW_END;
      }
      stream += TABLE_END;
      const endIndex = stream.length;

      // 표 뒤에는 언제나 문단 자리가 온다. Univer 가 새로 만드는 표도 그렇다 -
      // 없으면 표에서 레이아웃이 멈춰 뒤쪽 글이 통째로 안 보인다(2026-08-12 실측:
      // 8쪽 문서가 첫 표까지만 그려졌다).
      paragraphs.push({ startIndex: stream.length });
      stream += '\r';

      // 앞가지가 붙는다. 머릿글 표와 본문 표가 같은 이름을 쓰면 한 쪽이 다른 쪽을
      // 덮어 머릿글이 안 그려진다(2026-08-13 실측).
      const tableId = `${idPrefix}wo-table-${tableNo += 1}`;
      marks.push({ startIndex, endIndex, tableId });
      const widths = widthsOf(columnCount, table.columnWidths, bodyWidth);
      source[tableId] = {
        tableRows: grid.map((row, rowNo) => ({
          // 바탕색은 있는 칸에만 적는다. 없는 칸에 흰색을 박으면 어두운 화면에서
          // 표만 하얗게 뜬다.
          tableCells: row.map((_cell, columnNo) => {
            const shade = table.cellShades?.[rowNo]?.[columnNo];
            const fixed = table.rowHeights?.[rowNo];
            const across = table.columnSpans?.[rowNo]?.[columnNo];
            const down = table.rowSpans?.[rowNo]?.[columnNo];
            return {
              // 못박은 줄에는 여백을 두지 않는다. 4px 막대가 여백 10px 을 안고
              // 부풀면 원본과 딴판이 된다(2026-08-20 지적).
              // 원본이 알려 준 여백이 먼저다. 우리 기본은 한글 문서보다 넉넉해
              // 표가 원본보다 높아진다(2026-08-26 지적).
              margin: fixed != null ? NO_MARGIN
                : table.cellMargin == null ? CELL_MARGIN : {
                  start: { v: table.cellMargin.left },
                  end: { v: table.cellMargin.right },
                  top: { v: table.cellMargin.top },
                  bottom: { v: table.cellMargin.bottom },
                },
              // 테두리를 지운 표를 격자로 그리면 원본과 딴판이다 - 줄 세우기
              // 용도로 쓴 표가 격자로 보인다. 그은 변만 적힌 표는 그 변만 긋는다.
              ...(table.hasBorders === false
                ? NO_BORDER : cellBorders(table.borders) ?? {}),
              ...(shade ? { backgroundColor: { rgb: shade } } : {}),
              // 합치기는 다른 값일 때만 적는다. 1 을 적어도 뜻은 같지만 문서가
              // 쓸데없이 커지고, 손댄 곳을 나중에 찾기 어려워진다.
              ...(across != null && across !== 1 ? { columnSpan: across } : {}),
              ...(down != null && down !== 1 ? { rowSpan: down } : {}),
            };
          }),
          // 워드가 높이를 못박은 줄은 그 높이로 그린다(hRule 2 = EXACT). 한글에서
          // 온 높이는 "적어도"(1) 다 - 못박으면 글이 잘린다.
          trHeight: table.rowHeights?.[rowNo] != null
            ? {
              val: { v: table.rowHeights[rowNo] as number },
              hRule: table.exactRowHeights === false ? 1 : 2,
            }
            : ROW_HEIGHT,
        })),
        tableColumns: widths.map((width) => ({
          size: { type: 1, width: { v: width } },
        })),
        tableId,
        align: tableAlign(table.align),
        indent: { v: 0 },
        textWrap: 0,
        position: {
          positionH: { relativeFrom: 0, posOffset: 0 },
          positionV: { relativeFrom: 0, posOffset: 0 },
        },
        dist: { distB: 0, distL: 0, distR: 0, distT: 0 },
        cellMargin: CELL_MARGIN,
        size: { type: 0, width: { v: widths.reduce((sum, width) => sum + width, 0) } },
      };
    }
  };

  if (imagesAt.has(-1)) {
    // 문서 맨 앞 그림은 담을 문단이 없다. 하나 만들어 준다.
    writeImages(-1);
    paragraphs.push({ startIndex: stream.length });
    stream += '\r';
  }
  writeTables(-1);
  for (const [at, row] of ordered.entries()) {
    const style = row.style ? JSON.parse(row.style) as Record<string, unknown> : null;
    if (at === 0) {
      // 문서 맨 앞에 걸린 떠 있는 그림은 첫 문단이 안고 간다. 제 문단을 주면
      // 표지 배경이 첫 쪽을 통째로 차지한다(2026-08-25 지적).
      writeImages(-1, true);
    }
    // 글 앞에 끼는 그림이 먼저다. 이래야 그림과 글이 한 줄에 선다.
    writeImages(row.paragraphNo, true);
    const from = stream.length;
    // 쪽 번호 필드는 울타리를 끼워 그 구간을 알린다 - 까닭은 docField 에 적었다.
    const parsed = parseRuns(row.runs);
    const filled = insertFields(row.text, fieldsOf(parsed),
      `${idPrefix}p${row.paragraphNo}`, stylesOf(parsed));
    stream += filled.text;
    for (const range of filled.ranges) {
      customRanges.push({ ...range,
        startIndex: range.startIndex + from, endIndex: range.endIndex + from });
    }

    // 글자 서식을 표 때문에 밀린 자리로 옮긴다. 이것을 빠뜨려 표가 있는 문서는
    // 굵게·크기·색이 통째로 사라졌고, 저장할 때 그 상태가 서버에 덮어써졌다
    // (2026-08-12 실측: 워드 문서 문단 114개의 서식이 날아갔다).
    for (const run of filled.runs) {
      textRuns.push(withUsableFont(withDefaultFont(
        { ...run, st: run.st + from, ed: run.ed + from } as never, defaultFont)));
    }

    // 다음 문단이 새 쪽을 열면 이 문단 글 끝에 쪽 나눔 글자를 붙인다. 글만 있는
    // 문서(docBody)와 같은 셈이다. 다만 이 문단 뒤에 표나 그림이 붙으면 그것들
    // 뒤로 미룬다 - 앞에 두면 표까지 다음 쪽으로 밀리고, 저장할 때 표 안에 든
    // 쪽 나눔이 통째로 사라진다(2026-08-25 실측: 쪽 나눔 6개가 1개로).
    const opensNext = opensNewPage(ordered[at + 1]);
    const holdsMore = byPosition.has(row.paragraphNo) || imagesAt.has(row.paragraphNo)
      || inlineAt.has(row.paragraphNo);
    if (opensNext && !holdsMore) {
      stream += PAGE_BREAK;
    }

    paragraphs.push({
      startIndex: stream.length,
      ...(style ? withoutPageBreak(style) : {}),
    });
    stream += '\r';
    // 그림이 먼저다. 원본에서 로고가 표 위에 있는데 표를 먼저 적으면 로고가 표
    // 아래로 내려갔다(2026-08-19 지적). 예전에 그림을 앞세우면 화면이 백지가
    // 됐는데, 그것은 그림에게 제 문단이 없어서였다 - 지금은 준다.
    writeImages(row.paragraphNo);
    writeTables(row.paragraphNo);
    if (opensNext && holdsMore) {
      // 표·그림 뒤에 쪽 나눔만 든 문단을 하나 둔다. 저장할 때 이 문단은 버려지고
      // 표시는 다음 문단이 가져간다(docBody).
      stream += PAGE_BREAK;
      paragraphs.push({ startIndex: stream.length });
      stream += '\r';
    }
  }

  // 표로 문서가 끝나면 그 뒤에 빈 문단을 둔다. 표 다음에 커서를 둘 자리가 없으면
  // 표 아래에 글을 이어 쓸 수 없다.
  if (stream.endsWith(TABLE_END)) {
    paragraphs.push({ startIndex: stream.length });
    stream += '\r';
  }

  return {
    dataStream: `${stream}\n`,
    paragraphs,
    textRuns,
    sectionBreaks: [{ startIndex: stream.length }],
    tables: marks,
    tableSource: source,
    ...(customRanges.length > 0 ? { customRanges } : {}),
    customBlocks,
    drawings,
    drawingsOrder,
  };
}
