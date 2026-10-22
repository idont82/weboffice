/**
 * 줄 간격.
 *
 * <p>워드처럼 배수로 준다. Univer 문단 서식의 규칙 0(AUTO)이 배수이고, 2(EXACT)는
 * 적힌 길이로 못박는다 - 못박으면 글자를 키워도 줄이 안 늘어나 글이 겹친다.
 */

/** 줄 간격 규칙: 0 배수(AUTO) · 1 최소(AT_LEAST) · 2 정확히(EXACT). */
const MULTIPLE = 0;

/** 고를 수 있는 줄 간격. 워드가 주는 것과 같다. */
export const LINE_SPACINGS = [
  { label: '1.0', value: 1 },
  { label: '1.15', value: 1.15 },
  { label: '1.5', value: 1.5 },
  { label: '2.0', value: 2 },
  { label: '2.5', value: 2.5 },
];

/** 문단 하나. 화면(Univer) 본문에서 오는 모양이다. */
export interface SpacedParagraph {
  startIndex: number;
  paragraphStyle?: { lineSpacing?: number; spacingRule?: number } | null;
}

/**
 * 커서가 든 문단의 줄 간격(배수). 배수가 아니면 null 이다.
 *
 * <p>가져온 워드 문서는 줄 높이를 길이로 못박아 온다(규칙 2). 그런 문단은 배수
 * 목록에서 고를 수 없으므로 아무것도 짚지 않는다.
 */
export function lineSpacingOf(paragraphs: SpacedParagraph[],
  caretAt: number | null | undefined): number | null {
  if (caretAt == null || paragraphs.length === 0) {
    return null;
  }
  const paragraph = paragraphs.find((one) => one.startIndex >= caretAt);
  if (paragraph == null) {
    return null;
  }
  const style = paragraph.paragraphStyle;
  if (style?.lineSpacing == null) {
    return 1;
  }
  return style.spacingRule === MULTIPLE ? style.lineSpacing : null;
}

/** pt 를 px 로. 화면은 96dpi 로 그린다. */
const PX_PER_PT = 96 / 72;

/**
 * 사람에게 보여 줄 줄 간격(배수). 알 수 없으면 null 이다.
 *
 * <p>가져온 워드 문서와 우리 기본 문서는 줄 높이를 길이로 못박아 온다(규칙 2).
 * 그대로 두면 "지금 몇 줄" 을 영영 못 보여 주므로 글자 크기로 나눠 배수로
 * 환산한다.
 */
export function shownLineSpacing(paragraphs: SpacedParagraph[],
  caretAt: number | null | undefined, fontPt: number): number | null {
  const multiple = lineSpacingOf(paragraphs, caretAt);
  if (multiple != null) {
    return multiple;
  }
  if (caretAt == null) {
    return null;
  }
  const paragraph = paragraphs.find((one) => one.startIndex >= caretAt);
  const length = paragraph?.paragraphStyle?.lineSpacing;
  if (length == null || fontPt <= 0) {
    return null;
  }
  return Math.round(length / (fontPt * PX_PER_PT) * 100) / 100;
}

/** Univer 문단 설정 명령에 넘길 값. */
export function lineSpacingSetting(spacing: number):
{ paragraph: { lineSpacing: number; spacingRule: number } } {
  return { paragraph: { lineSpacing: spacing, spacingRule: MULTIPLE } };
}
