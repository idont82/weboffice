/**
 * 쪽 나눔 바로 앞의 빈 줄을 눌러 둔다.
 *
 * <p>워드 문서는 쪽 나눔 앞에 빈 문단을 남겨 둔다. 워드에서 그 줄은 쪽 끝에 놓여
 * 눈에 띄지 않는다. 우리 화면은 줄 높이가 워드와 딱 같지 않아 그 빈 줄이 다음
 * 쪽으로 밀리고, 글자 하나 없는 쪽이 생긴다(2026-08-25 실측: 원본 24쪽이 25쪽).
 *
 * <p>줄을 지우지는 않는다. 지우면 저장할 때 원본이 바뀐다. 높이만 1px 로 눌러
 * 앞 쪽에 남긴다 - 내보낼 때는 문단에 함께 담긴 줄 간격 배수를 쓰므로 원본 높이가
 * 그대로 살아난다.
 */

import type { DocParagraph } from './docBody';

/**
 * 눌러 둔 줄의 높이(px).
 *
 * <p>5 이하로 적으면 Univer 가 "배수" 로 읽어 글자 상자 높이로 되돌린다 - 눌러도
 * 한 줄이 그대로 보였다(2026-08-25 지적). 6 은 눈에 띄지 않으면서 그 경계를
 * 넘는 값이다.
 */
const SQUEEZED = 6;

/** 눌러 둔 줄의 글자 크기(pt). 글자 상자가 줄 높이를 밀어 올리지 못하게 한다. */
const SQUEEZED_FONT = 1;

/** 이 문단이 새 쪽을 여는가. */
function opensPage(row: DocParagraph | undefined): boolean {
  if (row?.style == null) {
    return false;
  }
  try {
    return (JSON.parse(row.style) as Record<string, unknown>).pageBreakBefore === 1;
  } catch (cannotRead) {
    return false;
  }
}

/**
 * @param keep 눌러선 안 되는 문단 번호(표·그림이 걸린 자리).
 */
export function shrinkBeforePageBreak(rows: DocParagraph[],
  keep: ReadonlySet<number> = new Set()): DocParagraph[] {
  return rows.map((row, at) => {
    const empty = (row.text ?? '').trim() === '';
    if (!empty || keep.has(row.paragraphNo) || !opensPageAfter(rows, at)) {
      return row;
    }
    const style = row.style == null
      ? {} as Record<string, unknown>
      : JSON.parse(row.style) as Record<string, unknown>;
    const paragraph = (style.paragraphStyle ?? {}) as Record<string, unknown>;
    paragraph.lineSpacing = SQUEEZED;
    paragraph.spacingRule = 2;
    paragraph.spaceAbove = { v: 0 };
    paragraph.spaceBelow = { v: 0 };
    style.paragraphStyle = paragraph;
    // 글자 상자도 함께 줄인다. 크기를 그대로 두면 그 상자가 줄 높이를 밀어 올린다.
    return { ...row, style: JSON.stringify(style), runs: squeezedRuns(row) };
  });
}

/** 이 문단 뒤로 빈 줄만 이어지다가 새 쪽이 열리는가. */
function opensPageAfter(rows: DocParagraph[], at: number): boolean {
  for (let next = at + 1; next < rows.length; next += 1) {
    if (opensPage(rows[next])) {
      return true;
    }
    if ((rows[next].text ?? '').trim() !== '') {
      return false;
    }
  }
  return false;
}

/** 눌러 둔 줄의 글자 서식. 크기만 1pt 로 줄인다. */
function squeezedRuns(row: DocParagraph): string {
  const text = row.text ?? '';
  return JSON.stringify([{ st: 0, ed: Math.max(1, text.length),
    ts: { fs: SQUEEZED_FONT } }]);
}
