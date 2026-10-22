/**
 * CellDto <-> Univer 셀 데이터 변환. 순수 함수만 둔다.
 *
 * 필드명은 서버 record(weboffice.excel.CellDto)와 1:1로 맞춘다.
 * 서버가 BigDecimal 을 JSON 문자열로 보낼 수 있으므로 numberValue 는 string | number 를 모두 받는다.
 */

import { type AnyDoc, hasLines, linesToDoc, textOfDoc } from './cellLines';

export type ValueType = 'STRING' | 'NUMBER' | 'DATETIME' | 'BOOLEAN' | 'ERROR';

export interface CellDto {
  rowNo: number;
  columnNo: number;
  valueType: ValueType;
  textValue?: string | null;
  numberValue?: string | number | null;
  datetimeValue?: string | null;
  booleanYn?: string | null;
  formulaValue?: string | null;
  /**
   * 서식 JSON 전체. 화면(Univer)의 서식 객체를 문자열로 담은 것이다.
   *
   * 숫자 서식만 담던 때에는 테두리·정렬·글꼴이 저장 뒤에 사라졌다(2026-08-06 지적).
   * 서버는 이 문자열을 해석하지 않고 왕복만 시킨다.
   */
  styleValue?: string | null;
}

export interface MergeRangeDto {
  fromRowNo: number;
  fromColumnNo: number;
  toRowNo: number;
  toColumnNo: number;
}

export interface SheetDto {
  sheetName: string;
  cells: CellDto[];
  mergeRanges: MergeRangeDto[];
}

/** Univer CellValueType: 1=문자, 2=숫자, 3=불린, 4=강제문자 */
const UNIVER_TYPE = { STRING: 1, NUMBER: 2, BOOLEAN: 3 } as const;


/**
 * 엑셀 클립보드가 쓰는 색 이름을 캔버스가 아는 값으로 바꾼다.
 *
 * 엑셀은 테두리 색을 {@code windowtext} 로 내보낸다. CSS 시스템 색 키워드라
 * 브라우저는 알지만 캔버스 strokeStyle 로는 무효라 선이 아예 안 그어진다 -
 * 붙여넣으면 값·배경은 오는데 테두리만 사라지는 이유다(2026-08-06 실측).
 *
 * 서식 전체를 훑어 색으로 쓰이는 자리만 바꾼다. 구조를 모르는 채 다니므로
 * 필드 이름이 아니라 값의 생김새로 판단한다.
 */
const SYSTEM_COLORS: Record<string, string> = {
  windowtext: '#000000',
  window: '#ffffff',
  buttonface: '#f0f0f0',
  highlight: '#0078d7',
  highlighttext: '#ffffff',
  captiontext: '#000000',
  infotext: '#000000',
  menutext: '#000000',
};

export function normalizeColors(value: unknown): unknown {
  if (typeof value === 'string') {
    return SYSTEM_COLORS[value.toLowerCase()] ?? value;
  }
  if (Array.isArray(value)) {
    return value.map(normalizeColors);
  }
  if (value !== null && typeof value === 'object') {
    const out: Record<string, unknown> = {};
    for (const [key, inner] of Object.entries(value as Record<string, unknown>)) {
      out[key] = normalizeColors(inner);
    }
    return out;
  }
  return value;
}

export interface UniverCell {
  v?: string | number;
  t?: number;
  f?: string;
  /**
   * 서식. 객체이거나 문자열 id 다.
   *
   * Univer 는 같은 서식을 여러 셀이 공유하도록 워크북 서식표에 넣고 셀에는 id 만
   * 남길 수 있다(타입이 IStyleData | string). id 를 그대로 저장하면 다시 열 때
   * 아무 서식도 안 붙는다 - 그 id 는 그때 그 워크북 안에서만 뜻이 있다.
   * 저장 전에 푸는 것은 main.ts 의 withResolvedStyle 이 한다.
   *
   * 내용은 해석하지 않는다. 테두리·정렬·색·글꼴이 전부 여기 들어오고, 우리는
   * 문자열로 만들어 왕복만 시킨다.
   */
  s?: Record<string, unknown> | string | null;
  /**
   * 셀 안 여러 줄. Univer 는 줄바꿈을 값(v)이 아니라 문단 묶음으로 다룬다.
   *
   * <p>값에 넣은 개행은 편집기에서 문단이 되지 못하고, 편집을 마치면 공백으로
   * 눌린다 - 여러 줄 셀을 한 번 고쳤을 뿐인데 줄이 사라졌다(2026-09-03 실측).
   */
  p?: AnyDoc | null;
}

export type UniverCellMatrix = Record<number, Record<number, UniverCell>>;

export function toUniverCellData(cells: CellDto[]): UniverCellMatrix {
  const matrix: UniverCellMatrix = {};

  for (const cell of cells) {
    matrix[cell.rowNo] ??= {};
    matrix[cell.rowNo][cell.columnNo] = toUniverCell(cell);
  }

  return matrix;
}

function toUniverCell(cell: CellDto): UniverCell {
  const result: UniverCell = {};

  // 수식 셀도 캐시 결과값을 함께 실어 준다. 없으면 Univer 가 재계산을 마칠 때까지
  // 빈 칸으로 보이고, Univer 가 지원하지 않는 함수는 원본 결과조차 남지 않는다.
  if (cell.formulaValue) {
    result.f = `=${cell.formulaValue}`;
    const cached = cachedValueOf(cell);
    if (cached !== undefined) {
      result.v = cached.v;
      result.t = cached.t;
    }
    return result;
  }

  switch (cell.valueType) {
    case 'NUMBER':
      result.v = Number(cell.numberValue ?? 0);
      result.t = UNIVER_TYPE.NUMBER;
      break;
    case 'BOOLEAN':
      result.v = cell.booleanYn === 'Y' ? 1 : 0;
      result.t = UNIVER_TYPE.BOOLEAN;
      break;
    case 'DATETIME':
      // P0 범위: 날짜는 표시 문자열로 넘긴다. 엑셀 시리얼 값 변환은 P2.
      result.v = cell.datetimeValue ?? '';
      result.t = UNIVER_TYPE.STRING;
      break;
    default: {
      const text = cell.textValue ?? '';
      result.v = text;
      result.t = UNIVER_TYPE.STRING;
      // 여러 줄은 문단으로도 넣는다. 값만 넣으면 고치고 나올 때 줄이 사라진다.
      // 셀 서식을 함께 실어 준다 - 안 실으면 문단이 문서 기본 글꼴로 그려져
      // 여러 줄 셀만 크게 보인다(2026-09-03 지적).
      if (hasLines(text)) {
        result.p = linesToDoc(text, parsedStyle(cell.styleValue ?? '') as
          Record<string, unknown> | null);
      }
      break;
    }
  }

  if (cell.styleValue) {
    const style = parsedStyle(cell.styleValue);
    if (style != null) {
      result.s = style;
    }
  }

  return result;
}

/**
 * 이미 푼 서식. 같은 문자열은 같은 객체를 함께 쓴다.
 *
 * <p>시트 한 장에 셀이 2만 개인데 서식은 백여 가지다. 셀마다 JSON 을 풀고 색을
 * 고치느라 문서 여는 시간의 4분의 1을 여기서 썼다(2026-08-20 실측: 583ms).
 *
 * <p>객체를 함께 쓰는 것이 안전한 까닭: Univer 는 서식을 고칠 때 이 객체를 고치지
 * 않고 새 서식을 만들어 제 사전에 넣는다.
 */
const styleCache = new Map<string, UniverCell['s']>();

/** 사전이 무한정 자라지 않게 한다. 문서를 여러 개 열어도 여기서 멈춘다. */
const STYLE_CACHE_MOST = 5000;

function parsedStyle(styleValue: string): UniverCell['s'] | null {
  const seen = styleCache.get(styleValue);
  if (seen !== undefined) {
    return seen;
  }
  let style: UniverCell['s'] | null = null;
  try {
    // 읽을 때도 색을 정규화한다. 정규화 이전에 저장된 문서가 이미 있고,
    // 그것들은 테두리 색이 windowtext 인 채로 들어 있다.
    style = normalizeColors(JSON.parse(styleValue)) as UniverCell['s'];
  } catch {
    // 사람이 손으로 고친 값이 들어올 수 있다. 서식 하나 때문에 문서가 안 열리는
    // 것보다 서식을 잃는 편이 낫다.
    return null;
  }
  if (styleCache.size >= STYLE_CACHE_MOST) {
    styleCache.clear();
  }
  styleCache.set(styleValue, style);
  return style;
}

/** 수식 셀이 들고 있는 캐시 결과값을 Univer 표현으로 바꾼다. 값이 없으면 undefined. */
function cachedValueOf(cell: CellDto): { v: string | number; t: number } | undefined {
  switch (cell.valueType) {
    case 'NUMBER':
      return cell.numberValue == null
        ? undefined
        : { v: Number(cell.numberValue), t: UNIVER_TYPE.NUMBER };
    case 'BOOLEAN':
      return cell.booleanYn == null
        ? undefined
        : { v: cell.booleanYn === 'Y' ? 1 : 0, t: UNIVER_TYPE.BOOLEAN };
    default:
      return cell.textValue == null
        ? undefined
        : { v: cell.textValue, t: UNIVER_TYPE.STRING };
  }
}

export function fromUniverCellData(
  matrix: UniverCellMatrix,
  sheetName: string,
  mergeRanges: MergeRangeDto[],
): SheetDto {
  const cells: CellDto[] = [];

  for (const [rowKey, row] of Object.entries(matrix)) {
    for (const [colKey, cell] of Object.entries(row)) {
      cells.push(fromUniverCell(Number(rowKey), Number(colKey), cell));
    }
  }

  return { sheetName, cells, mergeRanges };
}

function fromUniverCell(rowNo: number, columnNo: number, cell: UniverCell): CellDto {
  // 서식 객체를 통째로 문자열로 만든다. 이 문자열이 서버 서식 사전의 해시 입력이라
  // 같은 서식이면 같은 문자열이어야 한다 - JSON.stringify 는 필드 순서를 유지한다.
  //
  // 문자열 id 는 담지 않는다. 그 id 는 만든 워크북 안에서만 뜻이 있어, 저장해 두면
  // 다시 열 때 아무 서식도 안 붙는다. 푸는 것은 부르는 쪽 몫이다.
  const styleValue = cell.s && typeof cell.s === 'object'
    ? JSON.stringify(normalizeColors(cell.s))
    : null;

  // 수식 셀은 Univer 가 계산해 둔 결과값(v)을 함께 넘긴다. 넘기지 않으면 POI 가
  // 수식 셀 캐시값을 기본값 0 으로 써서, 엑셀이 재계산 없이 열 때 0 이 보인다.
  if (cell.f) {
    const base = { rowNo, columnNo, formulaValue: cell.f.replace(/^=/, ''), styleValue };

    if (cell.v === undefined || cell.v === null) {
      return { ...base, valueType: 'NUMBER' };
    }
    if (cell.t === UNIVER_TYPE.BOOLEAN) {
      return { ...base, valueType: 'BOOLEAN', booleanYn: cell.v === 1 || cell.v === 'Y' ? 'Y' : 'N' };
    }
    if (cell.t === UNIVER_TYPE.STRING || typeof cell.v === 'string') {
      return { ...base, valueType: 'STRING', textValue: String(cell.v) };
    }
    return { ...base, valueType: 'NUMBER', numberValue: Number(cell.v) };
  }

  if (cell.t === UNIVER_TYPE.NUMBER) {
    return { rowNo, columnNo, valueType: 'NUMBER', numberValue: Number(cell.v), styleValue };
  }

  if (cell.t === UNIVER_TYPE.BOOLEAN) {
    return {
      rowNo, columnNo, valueType: 'BOOLEAN',
      booleanYn: cell.v === 1 || cell.v === 'Y' ? 'Y' : 'N',
      styleValue,
    };
  }

  // 문단이 있으면 그것이 참이다. 값(v)에는 줄바꿈이 공백으로 눌려 담긴다.
  const text = cell.p ? textOfDoc(cell.p) : String(cell.v ?? '');
  return { rowNo, columnNo, valueType: 'STRING', textValue: text, styleValue };
}
