/**
 * Univer 글 본문 ↔ 문단 줄.
 *
 * <p>Univer 는 본문을 한 덩어리로 들고 있다 - 글자는 {@code dataStream} 하나에
 * 이어 붙고, 문단과 서식은 그 안의 자리(오프셋)로 가리킨다. 우리 표는 문단 한 줄에
 * 한 행이라 그 사이를 옮겨야 한다.
 *
 * <p>문단으로 자르는 이유: 통째로 담으면 둘이 동시에 고칠 때 나중 저장이 앞선 것을
 * 덮는다. 시트에서 셀 단위로 한 것과 같은 얼개다 - 잠금·변경이력·실시간이 여기에
 * 붙는다.
 *
 * <p>{@code dataStream} 의 규칙: {@code \r} 이 문단 끝, {@code \n} 이 구역 끝이다.
 * 둘 다 글자가 아니므로 문단 글자에 넣지 않는다.
 */

import { withDefaultFont, withUsableFont } from './docFont';
import { insertFields, type FieldMark, type FieldRange } from './docField';

/**
 * 표가 남기는 제어문자. Univer 가 칸 경계를 이 글자들로 표시한다.
 *
 * <p>글자로 담으면 다시 열 때 문단이 어그러지고 docx 로 내보낸 파일에도 쓰레기가
 * 섞인다(2026-08-11 실측: 표 하나에 문단 21개가 제어문자로 채워졌다). 표의 얼개는
 * 문서 스냅샷이 따로 담는다.
 */
const CONTROL = /[\u0000-\u0008\u000b-\u001f]/;

/** 그림이 앉는 자리. 화면이 그림 하나마다 이 글자를 하나 놓는다. */
const DRAWING_ANCHOR = String.fromCharCode(0x08);

/**
 * 표 안이라는 표시. 칸 끝·행 끝·표 시작이 섞인 문단은 표의 것이다.
 *
 * <p>표 글까지 문단으로 저장하면 문단 수가 부풀고(2026-08-12 실측: 161 -> 203)
 * 내려받은 docx 에 표 글이 두 번 나온다. 표는 문서 한 벌(스냅샷)이 따로 담는다.
 */
const IN_TABLE = /[\u001a\u001b\u001c\u001d\u000e]/;

/** 표 끝. 이 뒤에 사람이 쓴 글이 붙으면 그것은 표가 아니라 문단이다. */
const TABLE_END = '\u000f';

/** 문단 한 줄. 서버 표와 같은 모양이다. */
export interface DocParagraph {
  paragraphNo: number;
  text: string;
  /** 문단 서식(제목·정렬·글머리 기호). 없으면 null. */
  style: string | null;
  /** 글자 서식. 문단 안 자리로 적는다. 없으면 null. */
  runs: string | null;
}

interface TextRun {
  st: number;
  ed: number;
  [key: string]: unknown;
}

interface ParagraphMark {
  startIndex: number;
  [key: string]: unknown;
}

export interface DocBody {
  dataStream: string;
  paragraphs: ParagraphMark[];
  textRuns: TextRun[];
  /** 구역 끝(
) 자리. 없으면 Univer 가 문단을 새로 넣을 자리를 못 찾는다. */
  sectionBreaks?: { startIndex: number; sectionType?: number }[];
  /** 표가 놓인 자리. 없으면 표를 넣어도 화면에 그려지지 않는다. */
  tables?: unknown[];
}

/** 쪽 나눔 글자. 문단 글이 이 글자로 끝나면 Univer 가 다음 쪽을 연다. */
export const PAGE_BREAK = String.fromCharCode(12);

/** 이 문단이 새 쪽에서 시작하는가. 서버가 문단 서식에 적어 보낸다. */
export function opensNewPage(row: DocParagraph | undefined): boolean {
  if (!row?.style) {
    return false;
  }
  try {
    return (JSON.parse(row.style) as Record<string, unknown>).pageBreakBefore === 1;
  } catch {
    return false;
  }
}

/**
 * 문단 서식에서 우리끼리 쓰는 값을 뗀다.
 *
 * <p>{@code pageBreakBefore} · {@code tabRight} · {@code tabLeader} 는 서버와
 * 우리끼리 쓰는 값이다. Univer 문단 서식에는 없다. 쪽은 앞 문단 끝에
 * 쪽 나눔 글자를 붙여 넘긴다 - Univer 문단 서식에는 그런 값이 없다. 그대로 실어
 * 보내면 편집 화면이 저장할 때 모르는 값을 서버로 되돌려 보낸다.
 */
export function withoutPageBreak(style: Record<string, unknown>): object {
  const { pageBreakBefore: _break, tabRight: _stop, tabLeader: _leader, ...rest } = style;
  return rest;
}

/**
 * 화면이 그림 때문에 만들어 준 문단인가.
 *
 * <p>그런 문단은 가운데 맞춤 하나만 갖는다. 원본에서 온 문단은 간격·줄 높이 같은
 * 제 서식을 갖는다.
 */
function madeForDrawing(style: Record<string, unknown>): boolean {
  const keys = Object.keys(style);
  if (keys.length === 0) {
    return true;
  }
  if (keys.length > 1 || keys[0] !== 'paragraphStyle') {
    return false;
  }
  const paragraph = style.paragraphStyle as Record<string, unknown> | undefined;
  const own = Object.keys(paragraph ?? {});
  return own.length === 0 || (own.length === 1 && own[0] === 'horizontalAlign');
}

/** 본문을 문단 줄로 자른다. */
export function toParagraphs(body: DocBody): DocParagraph[] {
  const runs = body.textRuns ?? [];
  const rows: DocParagraph[] = [];

  let from = 0;
  let afterPageBreak = false;
  // 표 안에 든 문단은 몇 겹이든 세어 둔다. 칸 안 줄 나눔으로 생긴 문단은 제어문자를
  // 하나도 안 갖고 있어 글자만 보면 본문 문단과 구별할 수 없다(2026-08-25 실측:
  // 표 밖 문단 42개가 62개로 늘고 내려받은 문서에 표 글이 두 번 나왔다).
  let depth = 0;
  body.paragraphs.forEach((mark) => {
    const to = mark.startIndex;
    const { startIndex: _drop, ...rest } = mark;

    // 제어문자를 뺀 글자와, 뺀 만큼 당겨진 자리를 함께 만든다. 자리를 안 당기면
    // 서식이 엉뚱한 곳에 걸린다.
    const raw = body.dataStream.slice(from, to);
    let text = '';
    const moved: number[] = [];
    for (let at = 0; at < raw.length; at += 1) {
      moved.push(text.length);
      if (!CONTROL.test(raw[at])) {
        text += raw[at];
      }
    }
    moved.push(text.length);
    const shift = (offset: number) => moved[Math.max(0, Math.min(offset - from, raw.length))];

    // 이 문단에 걸치는 서식만 모아 문단 시작을 0 으로 다시 잡는다. 그러지 않으면
    // 둘째 문단부터 엉뚱한 자리가 굵어진다.
    const mine = runs
      .filter((run) => run.st < to && run.ed > from)
      .map((run) => ({
        ...run,
        st: shift(Math.max(run.st, from)),
        ed: shift(Math.min(run.ed, to)),
      }))
      .filter((run) => run.ed > run.st);

    // 표 안 문단은 문단이 아니다. 표 끝만 든 빈 자리도 마찬가지다 - 표 뒤에
    // 사람이 쓴 글은 글자가 남으므로 살아남는다.
    const opened = depth;
    depth += (raw.match(//g) ?? []).length;
    depth = Math.max(0, depth - (raw.match(//g) ?? []).length);
    const belongsToTable = IN_TABLE.test(raw)
      || (raw.includes(TABLE_END) && text.length === 0)
      // 표를 열어 둔 채로 만난 문단은 칸 안 줄이다.
      || (opened > 0 && !raw.includes(TABLE_END));
    // 그림에는 화면이 제 문단을 준다(문단 없이 자리만 넣으면 백지가 된다). 그것을
    // 저장하면 문단이 그림 수만큼 늘어 쪽 수가 어긋난다(2026-08-24 지적). 그림
    // 자리만 든 문단은 저장하지 않는다 - 그림은 따로 담아 두고 화면이 다시 끼운다.
    // 다만 제 서식을 가진 문단은 지킨다. 표지는 글이 도형 안에 있어 문단 글자가
    // 비는데, 그 문단을 버리면 표지 쪽이 사라지고 목차가 첫 쪽으로 올라와 배경
    // 그림에 덮인다(2026-08-25 지적).
    const holdsOnlyDrawing = raw.includes(DRAWING_ANCHOR) && text.length === 0
      && madeForDrawing(rest);
    // 쪽 나눔만 든 문단은 화면이 끼운 것이다. 저장하면 빈 줄이 하나씩 는다 -
    // 표시는 다음 문단이 가져간다(2026-08-25).
    const holdsOnlyBreak = raw.includes(PAGE_BREAK) && text.length === 0;
    if (!belongsToTable && !holdsOnlyDrawing && !holdsOnlyBreak) {
      // 앞 문단이 쪽 나눔 글자로 끝났으면 이 문단이 새 쪽을 연다. 그 표시를
      // 되돌리지 않으면 가져온 문서를 한 번 저장하는 것만으로 원본의 쪽 나눔이
      // 사라진다.
      const style: Record<string, unknown> = { ...rest };
      // 앞 문단이 쪽 나눔으로 끝났거나, 이 문단이 쪽 나눔으로 시작하면 새 쪽이다.
      if (afterPageBreak || raw.startsWith(PAGE_BREAK)) {
        style.pageBreakBefore = 1;
      }
      rows.push({
        paragraphNo: rows.length,
        text,
        style: Object.keys(style).length > 0 ? JSON.stringify(style) : null,
        runs: mine.length > 0 ? JSON.stringify(mine) : null,
      });
    }
    afterPageBreak = raw.endsWith(PAGE_BREAK);
    // 문단 끝의 \r 을 건너뛴다.
    from = to + 1;
  });

  return rows;
}

/** 문단 줄을 본문으로 되붙인다. */

/**
 * 서식이 없는 문단이 쓰는 기본값.
 *
 * <p>화면 기본은 줄 높이를 글자 상자(11pt 에서 21px)로 잡아, 엔터를 치면 줄이
 * 뚝 떨어져 보인다. 구글 문서는 1.15줄이다(2026-08-19 지적). 규칙 2는
 * "정확히 이 높이" 이고, 그 뜻대로 그리도록 빌드 때 고쳐 두었다(exactLine).
 */
const DEFAULT_PARAGRAPH = {
  paragraphStyle: {
    lineSpacing: 1.15 * 11 * (96 / 72),
    spacingRule: 2,
    spaceBelow: { v: 0 },
  },
};

export function toBody(rows: DocParagraph[], defaultFont: string | null = null): DocBody {
  // 저장된 것이 없어도 빈 문단 하나는 있어야 한다. 빈 본문을 주면 Univer 가 잠긴
  // 것으로 보고 글자를 받지 않는다.
  const ordered = [...rows].sort((a, b) => a.paragraphNo - b.paragraphNo);
  const source = ordered.length > 0
    ? ordered
    : [{ paragraphNo: 0, text: '', style: null, runs: null }];

  let dataStream = '';
  const paragraphs: ParagraphMark[] = [];
  const textRuns: TextRun[] = [];
  const customRanges: FieldRange[] = [];

  for (const [at, row] of source.entries()) {
    const style = row.style ? JSON.parse(row.style) as Record<string, unknown> : null;
    // 새 쪽을 여는 문단은 글 앞에 쪽 나눔 글자를 둔다. 앞 문단 끝에 붙이면 그
    // 문단의 끝 표시가 새 쪽 첫 줄로 남아 빈 줄이 보인다(2026-08-25 지적).
    // Univer 가 문단 앞 쪽 나눔도 읽도록 빌드 때 기운다(emptyPageBreak.ts).
    if (at > 0 && opensNewPage(row)) {
      dataStream += PAGE_BREAK;
    }
    const from = dataStream.length;
    // 쪽 번호 필드는 울타리를 끼워 그 구간을 알린다 - 까닭은 docField 에 적었다.
    const parsed = (row.runs ? JSON.parse(row.runs) as Array<Record<string, unknown>> : []);
    const filled = insertFields(row.text,
      parsed.filter((one) => typeof one.fld === 'string') as unknown as FieldMark[],
      `p${row.paragraphNo}`,
      parsed.filter((one) => typeof one.fld !== 'string') as unknown as TextRun[]);
    dataStream += filled.text;
    for (const range of filled.ranges) {
      customRanges.push({ ...range,
        startIndex: range.startIndex + from, endIndex: range.endIndex + from });
    }

    for (const run of filled.runs as TextRun[]) {
      // 없는 글꼴은 폭이 비슷한 글꼴로 바꾼다. 안 바꾸면 크기가 같아도 글자가
      // 커 보인다(2026-08-13 지적: 본문 글자 크기가 원본과 다르다).
      // 글꼴을 안 적은 조각은 문서 기본 글꼴로 그린다. 모르면 Univer 기본으로
      // 그려 줄이 접히는 자리가 달라진다(2026-08-25 실측).
      textRuns.push(withUsableFont(withDefaultFont(
        { ...run, st: run.st + from, ed: run.ed + from }, defaultFont)));
    }

    paragraphs.push({
      startIndex: dataStream.length,
      // pageBreakBefore 는 우리끼리 쓰는 표시다. Univer 는 모르는 값이라 그대로
      // 넘기면 저장할 때 서버로 되돌아간다.
      ...(style ? withoutPageBreak(style) : DEFAULT_PARAGRAPH),
    });
    dataStream += '\r';

  }

  // 마지막 문단 끝의 \r 뒤에 구역 끝 \n 이 온다. 그 자리를 따로 알려야 한다 -
  // 빼면 엔터를 쳐도 "Cannot insert into missing item" 만 나고 줄이 안 바뀐다
  // (2026-08-11 콘솔 실측).
  return {
    dataStream: `${dataStream}\n`,
    paragraphs,
    textRuns,
    sectionBreaks: [{ startIndex: dataStream.length }],
    ...(customRanges.length > 0 ? { customRanges } : {}),
    // 표가 놓일 자리. 비워 두더라도 배열은 있어야 한다 - 없으면 표를 넣어도
    // 정의(tableSource)만 남고 어디에 놓였는지가 사라져 화면에 안 그려진다
    // (2026-08-11 실측).
    tables: [],
  };
}
