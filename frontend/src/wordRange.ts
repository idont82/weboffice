/**
 * 커서가 놓인 낱말 범위.
 *
 * <p>고른 글자가 없을 때 색이나 굵게를 누르면 아무 일도 일어나지 않았다. 워드는
 * 그럴 때 커서가 놓인 낱말에 서식을 건다 - 같게 맞춘다.
 */

export interface WordRange {
  start: number;
  end: number;
}

/** 낱말을 가르는 글자. 공백과 흔한 구두점이다. */
const BREAK = /[\s,.!?;:()[\]{}'"·…、。]/;

/**
 * @param text 문단 글자
 * @param offset 커서 자리(문단 안)
 * @returns 낱말 범위. 커서가 빈 곳에 있으면 null.
 */
export function wordRangeAt(text: string, offset: number): WordRange | null {
  if (text.length === 0) {
    return null;
  }
  const at = Math.max(0, Math.min(offset, text.length));

  // 커서는 글자 사이에 선다. 오른쪽 글자를 먼저 보고, 없으면 왼쪽을 본다 -
  // 글을 치다 멈춘 자리(낱말 끝)가 가장 흔하다.
  let from = at;
  let to = at;
  if (at < text.length && !BREAK.test(text[at])) {
    to = at;
  } else if (at > 0 && !BREAK.test(text[at - 1])) {
    from = at - 1;
    to = at - 1;
  } else {
    return null;
  }

  while (from > 0 && !BREAK.test(text[from - 1])) {
    from -= 1;
  }
  while (to < text.length && !BREAK.test(text[to])) {
    to += 1;
  }
  return { start: from, end: to };
}
