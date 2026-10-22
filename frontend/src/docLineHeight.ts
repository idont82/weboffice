/**
 * 줄 높이를 원본처럼 잰다.
 *
 * <p>워드는 줄 높이를 {@code 배수 x 글자 크기 x 글꼴 행간} 으로 잡는다. 글꼴마다
 * 행간이 다르다 - Century Gothic 은 1.22, Calibri 는 1.22, 맑은 고딕은 1.4 쯤이다.
 * 우리는 그 자리에 1.1 을 못 박아 써서 문서마다 쪽이 밀렸다(2026-08-25 지적:
 * 원본 24쪽이 화면에서 25쪽).
 *
 * <p>그래서 화면이 실제로 쓸 글꼴의 행간을 재서 다시 셈한다. 서버가 담아 둔
 * 배수(lineMultiple)를 그 위에 곱한다.
 */

import type { DocParagraph } from './docBody';

/** 화면은 96dpi, 글자 크기는 72dpi 다. */
const PX_PER_PT = 96 / 72;

/** 글꼴을 못 재면 쓰는 행간. 흔한 본문 글꼴의 값이다. */
const FALLBACK_RATIO = 1.22;

/**
 * 문서 기본 글자 크기(pt).
 *
 * <p>워드 파일 규칙에서 크기를 안 적으면 10pt 다. 11 로 보면 크기를 안 적은
 * 문서가 원본보다 커진다(2026-08-26 지적).
 */
const DEFAULT_FONT_SIZE = 10;

/** 글꼴 이름 → 행간(em). 한 화면에서 여러 번 물으므로 담아 둔다. */
const measured = new Map<string, number>();

/**
 * 그 글꼴의 행간(em).
 *
 * <p>캔버스가 글꼴의 위아래 상자를 알려 준다. 못 재면 흔한 값을 쓴다.
 */
export function fontLineRatio(font: string | null): number {
  const name = font == null || font === '' ? 'sans-serif' : font;
  const kept = measured.get(name);
  if (kept != null) {
    return kept;
  }
  let ratio = FALLBACK_RATIO;
  try {
    const context = document.createElement('canvas').getContext('2d');
    if (context != null) {
      context.font = `100px "${name.replace(/"/g, '')}", sans-serif`;
      const box = context.measureText('Hg한');
      const above = box.fontBoundingBoxAscent;
      const below = box.fontBoundingBoxDescent;
      if (Number.isFinite(above) && Number.isFinite(below) && above + below > 0) {
        ratio = (above + below) / 100;
      }
    }
  } catch (cannotMeasure) {
    // 캔버스를 못 쓰는 자리(테스트 등)다. 흔한 값으로 둔다.
    ratio = FALLBACK_RATIO;
  }
  measured.set(name, ratio);
  return ratio;
}

/**
 * 문단마다 줄 높이를 다시 잰다.
 *
 * @param defaultFont 문서 기본 글꼴. 문단이 제 글꼴을 안 적었을 때 쓴다.
 * @param ratioOf 글꼴 행간을 알려 주는 자. 테스트에서 바꿔 끼운다.
 * @param defaultSize 문서 기본 글자 크기(pt).
 */
export function withLineHeights(rows: DocParagraph[], defaultFont: string | null,
  ratioOf: (font: string | null) => number = fontLineRatio,
  defaultSize = DEFAULT_FONT_SIZE): DocParagraph[] {
  return rows.map((row) => {
    if (row.style == null) {
      return row;
    }
    const style = JSON.parse(row.style) as Record<string, unknown>;
    const paragraph = style.paragraphStyle as Record<string, unknown> | undefined;
    const multiple = paragraph?.lineMultiple;
    if (paragraph == null || typeof multiple !== 'number' || multiple <= 0) {
      return row;
    }
    const first = firstRun(row.runs);
    const size = typeof first?.fs === 'number' && first.fs > 0 ? first.fs : defaultSize;
    const font = typeof first?.ff === 'string' && first.ff !== '' ? first.ff : defaultFont;
    paragraph.lineSpacing = size * PX_PER_PT * multiple * ratioOf(font);
    return { ...row, style: JSON.stringify(style) };
  });
}

/** 문단 첫 조각의 글자 서식. 없으면 없다. */
function firstRun(runs: string | null): Record<string, unknown> | null {
  if (runs == null) {
    return null;
  }
  const parsed = JSON.parse(runs) as { ts?: Record<string, unknown> }[];
  for (const run of parsed) {
    if (run.ts != null) {
      return run.ts;
    }
  }
  return null;
}
