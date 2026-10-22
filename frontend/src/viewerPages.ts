/**
 * 보기 전용 화면의 쪽 번호.
 *
 * <p>쪽 수를 모르면 인쇄해서 올릴 문서인지 가늠할 수 없다(편집 화면에도 같은
 * 것이 있다). 쪽은 세로로 쌓이므로 화면 한가운데가 어느 쪽에 걸리는지 센다.
 */

/**
 * 지금 보고 있는 쪽 번호(1부터). 쪽이 없으면 0 이다.
 *
 * @param heights 쪽마다의 높이(문서 좌표).
 * @param gap 쪽 사이 틈(문서 좌표).
 * @param scale 확대·축소 배율.
 * @param scrollTop 내려온 거리(화면 좌표).
 * @param viewHeight 보이는 높이(화면 좌표).
 */
export function pageAt(heights: number[], gap: number, scale: number,
  scrollTop: number, viewHeight: number): number {
  if (heights.length === 0) {
    return 0;
  }
  const middle = scrollTop + viewHeight / 2;
  let top = 0;
  for (const [at, height] of heights.entries()) {
    const bottom = top + height * scale;
    if (middle < bottom || at === heights.length - 1) {
      return at + 1;
    }
    top = bottom + gap * scale;
  }
  return heights.length;
}
