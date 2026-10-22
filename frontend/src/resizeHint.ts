/**
 * 머리글 경계를 끌 때 보여 줄 크기.
 *
 * <p>화면에서 몇 픽셀이 되는지 끄는 동안 알 수 없었다. 놓아 보고 아니면 다시
 * 끄는 식이라 엑셀에서 옮겨 온 표의 폭을 맞추기 어려웠다(2026-08-07 지적).
 *
 * <p>셈은 Univer 가 놓는 순간 쓰는 것과 같아야 한다. 다르면 눈금과 결과가 어긋나
 * 오히려 없느니만 못하다.
 */

/** Univer 손잡이 두께(4)의 절반. 새 경계는 포인터가 아니라 여기다. */
const THUMB_HALF = 2;

/** Univer 가 허용하는 가장 작은 크기. */
const MIN_SIZE = 2;

export type SizeAxis = 'ROW' | 'COLUMN';

/**
 * 끌고 있는 동안의 크기.
 *
 * @param startSize 끌기 전 크기(시트 픽셀)
 * @param screenDelta 포인터가 화면에서 움직인 거리
 * @param scale 확대 배율. 200% 면 화면 2픽셀이 시트 1픽셀이다.
 */
export function draggedSize(startSize: number, screenDelta: number, scale: number): number {
  // 손잡이 몫을 여기서 빼지 않는다. Univer 가 뺀 만큼은 명령 쪽에서 되돌리므로
  // (correctedDelta) 눈금도 끈 만큼 그대로 보여야 한다 - 빼면 눈금이 실제보다
  // 2px 작게 보인다(2026-08-18 지적).
  const size = startSize + screenDelta / scale;
  return Math.max(MIN_SIZE, Math.round(size));
}

/** 눈금에 적을 글. 행은 높이, 열은 너비다. */
export function hintText(axis: SizeAxis, size: number): string {
  return `${axis === 'ROW' ? '높이' : '너비'} ${size}px`;
}

/**
 * Univer 가 보낸 크기 변화량을 바로잡는다.
 *
 * <p>Univer 는 손잡이 두께의 절반을 이동 거리에서 뺀 값을 크기 변화로 쓴다
 * (sheets-ui 0.25.1: {@code moveChangeX = moveOffsetX - startOffsetX - 손잡이/2}).
 * 그래서 30px 을 끌면 28px 만 바뀌고, 한 픽셀도 안 움직여도 -2px 이 나간다 -
 * 경계를 몇 번 누르기만 해도 줄이 2px 로 주저앉는다(2026-08-18 지적).
 *
 * <p>뺀 만큼 되돌려 더한다. 안 움직였으면 0 이 되어 바뀔 것이 없다.
 *
 * @param delta Univer 가 보낸 값(시트 픽셀)
 * @param scale 확대 배율. 손잡이 몫도 배율만큼 작아진다.
 */
export function correctedDelta(delta: number, scale: number): number {
  return delta + THUMB_HALF / Math.max(scale, 0.01);
}
