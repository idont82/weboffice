/**
 * 셀 안 여러 줄.
 *
 * <p>Univer 는 셀 안의 줄바꿈을 값(v)이 아니라 <b>문단 묶음(p)</b>으로 다룬다.
 * 값에 넣은 {@code \n} 은 편집기로 들어갈 때 문단이 되지 못하고, 편집을 마치면
 * 공백으로 눌린다 - 여러 줄 셀을 한 번 고쳤을 뿐인데 줄이 통째로 사라졌다
 * (2026-09-03 실측).
 *
 * <p>문서 표현은 문단을 {@code \r} 로 끊고 마지막에 {@code \n} 을 둔다.
 */

/** 문단 하나의 시작 자리. Univer 가 이것으로 문단을 나눈다. */
interface Paragraph {
  startIndex: number;
}

/** 글자 서식. 셀 서식에서 글자에 관한 것만 옮겨 온다. */
export type TextStyle = Record<string, unknown>;

/**
 * 글자에 관한 서식 이름.
 *
 * <p>배경·테두리·정렬은 셀의 것이라 문단에 넣지 않는다 - 넣으면 셀 서식과 문단
 * 서식이 같은 것을 두 번 그린다.
 */
const TEXT_KEYS = ['ff', 'fs', 'it', 'bl', 'ul', 'st', 'ol', 'cl', 'va'];

/**
 * Univer 의 기본 글꼴 크기(@univerjs/core 의 DEFAULT_STYLES).
 *
 * <p>서식이 없는 셀에도 이것을 적어 준다. 안 적으면 문단이 <b>문서</b> 기본으로
 * 그려져 한 줄 셀보다 눈에 띄게 크다(2026-09-03 지적: E·I·J·K 열만 크게 보인다).
 */
export const DEFAULT_FONT_SIZE = 11;

/** 셀 서식에서 글자 서식만 골라 낸다. */
export function textStyleOf(style: Record<string, unknown> | null | undefined): TextStyle {
  const kept: TextStyle = { fs: DEFAULT_FONT_SIZE };
  for (const key of TEXT_KEYS) {
    const value = style?.[key];
    if (value !== undefined && value !== null) {
      kept[key] = value;
    }
  }
  return kept;
}

/** 셀에 담기는 작은 문서. 우리가 쓰는 것만 적는다. */
export interface CellDoc {
  body: {
    dataStream: string;
    paragraphs: Paragraph[];
    textRuns: { st: number; ed: number; ts: TextStyle }[];
  };
  documentStyle: { textStyle: TextStyle };
}

/** 읽을 때는 남이 만든 문서도 온다. 본문이 없을 수 있다. */
export interface AnyDoc {
  body?: { dataStream?: string };
}

/** 셀 안에서 줄을 끊는 표시. */
const PARAGRAPH = '\r';

/** 여러 줄 값인가. 한 줄이면 값(v)으로 두는 편이 가볍다. */
export function hasLines(text: string): boolean {
  return text.includes('\n') || text.includes('\r');
}

/**
 * 여러 줄 글을 셀 문서로 만든다.
 *
 * <p>{@code paragraphs} 를 함께 적는다. 없으면 Univer 가 한 문단으로 읽어 줄이
 * 도로 붙는다.
 */
export function linesToDoc(text: string, style?: Record<string, unknown> | null): CellDoc {
  const lines = text.split(/\r\n|\r|\n/);
  const textStyle = textStyleOf(style);

  const paragraphs: Paragraph[] = [];
  let at = 0;
  for (const line of lines) {
    at += line.length;
    paragraphs.push({ startIndex: at });
    at += 1;
  }

  // 글자 서식을 문단에도 적는다. 안 적으면 셀 서식이 아니라 문서 기본으로 그려져
  // 여러 줄 셀만 글꼴이 달라 보인다.
  const dataStream = `${lines.join(PARAGRAPH)}${PARAGRAPH}\n`;
  return {
    body: {
      dataStream,
      paragraphs,
      textRuns: [{ st: 0, ed: dataStream.length, ts: textStyle }],
    },
    documentStyle: { textStyle },
  };
}

/**
 * 셀 문서에서 글을 뽑는다.
 *
 * <p>문단 표시는 줄바꿈으로 되돌리고, 문서 끝 표시는 버린다.
 */
export function textOfDoc(doc: AnyDoc): string {
  const stream = doc.body?.dataStream;
  if (!stream) {
    return '';
  }
  return stream
    .replace(/\r\n$/, '')
    .replace(/\r$/, '')
    .split(PARAGRAPH)
    .join('\n');
}
