/**
 * 쪽 번호 필드(워드의 PAGE·NUMPAGES).
 *
 * <p>워드는 쪽마다 값이 달라지는 자리를 "필드" 로 적는다. 서버는 그 자리와
 * 종류를 문단의 run 목록에 {@code {st, ed, fld}} 로 실어 보낸다. 글자는 워드가
 * 계산해 둔 값("2")이라 그대로 두면 모든 쪽에 같은 숫자가 찍힌다.
 *
 * <p>화면(Univer)은 글 속에 울타리 글자 둘을 두고 그 구간을 custom range 로
 * 가린 다음, 울타리 바로 뒤 글자 자리에 그 쪽의 번호를 다시 찍는다. 그래서 글에
 * 울타리를 끼우고 뒤따르는 자리를 모두 그만큼 민다.
 */

/** 구간 시작·끝 울타리. Univer 가 정한 글자다. */
export const FIELD_START = '\u001F';
export const FIELD_END = '\u001E';

/** Univer 의 custom range 갈래. 1 이 필드다. */
const FIELD_RANGE = 1;

/** 서버가 보낸 필드 자리. */
export interface FieldMark {
  st: number;
  ed: number;
  fld: string;
}

/** 글자 서식 자리. 내용은 들여다보지 않고 자리만 민다. */
export interface RunMark {
  st: number;
  ed: number;
  [key: string]: unknown;
}

export interface FieldRange {
  startIndex: number;
  endIndex: number;
  rangeId: string;
  rangeType: number;
  properties: { instruction: string; fieldType: string };
}

export interface WithFields {
  text: string;
  runs: RunMark[];
  ranges: FieldRange[];
}

export function insertFields(text: string, fields: FieldMark[], idPrefix: string,
  runs: RunMark[] = []): WithFields {
  const wanted = fields
    .filter((one) => one.ed > one.st && one.st >= 0 && one.ed <= text.length)
    .sort((a, b) => a.st - b.st);
  if (wanted.length === 0) {
    return { text, runs, ranges: [] };
  }

  let out = '';
  let read = 0;
  const ranges: FieldRange[] = [];
  for (const [no, field] of wanted.entries()) {
    out += text.slice(read, field.st);
    const startIndex = out.length;
    out += FIELD_START + text.slice(field.st, field.ed) + FIELD_END;
    ranges.push({
      startIndex,
      endIndex: out.length - 1,
      rangeId: `${idPrefix}-field-${no}`,
      rangeType: FIELD_RANGE,
      // 워드의 지시문을 그대로 둔다 - 다시 내보낼 때 이것이 참이다.
      properties: { instruction: field.fld, fieldType: field.fld },
    });
    read = field.ed;
  }
  out += text.slice(read);

  return { text: out, runs: runs.map((one) => shifted(one, wanted)), ranges };
}

/** 울타리가 끼어든 만큼 자리를 민다. */
function shifted(run: RunMark, fields: FieldMark[]): RunMark {
  return { ...run, st: run.st + before(run.st, fields), ed: run.ed + before(run.ed, fields) };
}

/** 그 자리 앞에 끼어든 울타리 수. 시작에 하나, 끝에 하나다. */
function before(at: number, fields: FieldMark[]): number {
  let count = 0;
  for (const field of fields) {
    if (at > field.st) {
      count += 1;
    }
    if (at >= field.ed) {
      count += 1;
    }
  }
  return count;
}
